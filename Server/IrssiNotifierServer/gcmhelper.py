import traceback
from google.appengine.ext import deferred
from google.appengine.api.taskqueue import TransientError
from gcm import GCM
import logging
import dao
import sys

QueueName = 'gcmqueue'


def send_gcm_to_user_deferred(irssiuser, message):
    logging.info("Queuing deferred task for sending message to user %s" % irssiuser.email)
    key = irssiuser.key
    try:
        deferred.defer(_send_gcm_to_user, key, message, _queue=QueueName)
    except TransientError:
        logging.warn("Transient error: %s" % traceback.format_exc())


def _send_gcm_to_user(irssiuser_key, message):
    logging.info("Executing deferred task: _send_gcm_to_user, %s, %s" % (irssiuser_key, message))
    gcm = GCM(dao, sys.modules[__name__])
    gcm.send_gcm_to_user(irssiuser_key, message)


def send_gcm_to_token_deferred(token, message):
    logging.info("Queuing deferred task for sending message to token %s" % token.gcm_token)
    key = token.key
    try:
        deferred.defer(_send_gcm_to_token, key, message, _queue=QueueName)
    except TransientError:
        logging.warn("Transient error: %s" % traceback.format_exc())


def _send_gcm_to_token(token_key, message):
    logging.info("Executing deferred task: _send_gcm_to_token, %s, %s" % (token_key, message))
    token = dao.get_gcm_token_for_key(token_key)

    gcm = GCM(dao, sys.modules[__name__])
    gcm.send_gcm([token], message)
