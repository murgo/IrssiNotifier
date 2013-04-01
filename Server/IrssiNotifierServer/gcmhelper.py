from gcm import GCM
from google.appengine.ext import deferred
from google.appengine.api.taskqueue import TransientError
from datamodels import GcmToken
import logging

def sendGcmToUserDeferred(irssiuser, message):
    logging.info("queuing deferred task for sending message to user %s" % irssiuser.email)
    key = irssiuser.key()
    try:
        deferred.defer(_sendGcmToUser, key, message, _queue='gcmqueue')
    except TransientError as e:
        logging.warn("Transient error: %s" % e)

def _sendGcmToUser(irssiuser_key, message):
    logging.info("executing deferred task: _sendGcmToUser, %s, %s" % (irssiuser_key, message))
    gcm = GCM()
    gcm.sendGcmToUser(irssiuser_key, message)

def sendGcmToTokenDeferred(token, message):
    logging.info("queuing deferred task for sending message to token %s" % token.gcm_token)
    key = token.key()
    try:
        deferred.defer(_sendGcmToToken, key, message, _queue='gcmqueue')
    except TransientError as e:
        logging.warn("Transient error: %s" % e)

def _sendGcmToToken(token_key, message):
    logging.info("executing deferred task: _sendGcmToToken, %s, %s" % (token_key, message))
    gcm = GCM()
    
    token = GcmToken.get(token_key)
    gcm.sendGcm([token], message)
