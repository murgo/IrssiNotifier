import logging
from datamodels import Message
import traceback
from c2dm import C2DM
import time

class MessageHandler(object):
    def handle(self, irssiuser, array):
        logging.debug("Adding new message: %s" % array)
        try:
            dbMessage = Message(parent = irssiuser.key())
            dbMessage.message = array["message"]
            dbMessage.channel = array['channel']
            dbMessage.nick = array['nick']
            dbMessage.timestamp = array['timestamp']
            dbMessage.server_timestamp = time.time()
            dbMessage.put()
        except Exception as e:
            logging.error("Error while creating new message, exception %s", e)
            traceback.print_exception(e)
            return False
            
        c2dm = C2DM()
        c2dm.sendC2dmToUser(irssiuser, dbMessage.ToJson()) #TODO don't send whole message?
        
        return True
    def getMessage(self, timestamp, user):
        logging.debug("Getting messages after: %s" % timestamp)
        messages = Message.all(parent = user.key())
        messages.filter("server_timestamp <", timestamp)
        messages.order("server_timestamp")

        c2dm = C2DM()
        c2dm.sendC2dmToUser(user, "read")

        return messages.fetch(25)
