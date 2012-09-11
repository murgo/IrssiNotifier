import logging
from datamodels import Message
import traceback
import time
import gcmhelper

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
        
        if irssiuser.notification_count is None:
            irssiuser.notification_count = 1
        else:
            irssiuser.notification_count += 1
        irssiuser.last_notification_time = int(time.time())
        irssiuser.irssi_script_version = int(array['version'])
        irssiuser.put()
        
        gcmhelper.sendGcmToUserDeferred(irssiuser, dbMessage.ToGcmJson())
        return True
    
    
    def getMessages(self, timestamp, user):
        logging.debug("Getting messages after: %s" % timestamp)
        messages = Message.all()
        messages.ancestor(user)
        messages.filter("server_timestamp >", int(timestamp))
        messages.order("server_timestamp")

        m = messages.fetch(50)
        logging.debug("Found %s messages" % len(m))

        return m
