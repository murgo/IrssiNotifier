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
            dbMessage.server_timestamp = int(time.time())
            dbMessage.put()
        except Exception as e:
            logging.error("Error while creating new message, exception %s", e)
            traceback.print_exception(e)
            return False
            
        c2dm = C2DM()
        c2dm.sendC2dmToUser(irssiuser, dbMessage.ToC2dmJson())
        
        return True
    def getMessages(self, timestamp, user):
        logging.debug("Getting messages after: %s" % timestamp)
        messages = Message.all()
        messages.ancestor(user)
        messages.filter("server_timestamp >", int(timestamp))
        messages.order("server_timestamp")

        #c2dm = C2DM()
        #c2dm.sendC2dmToUser(user, "read")
        
        m = messages.fetch(50)
        logging.debug("Found %s messages" % len(m))

        return m
