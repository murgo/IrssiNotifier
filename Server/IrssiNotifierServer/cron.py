import webapp2
import logging
import time
from datamodels import Message
from google.appengine.ext import db

class CronController(webapp2.RequestHandler):
    def get(self):
        logging.info("Clearing data")
        messages = Message.all()
        messages.filter("server_timestamp <", int(time.time()) - 604800)

        firstCount = messages.count()
        db.delete(messages)
        lastCount = messages.count()
        
        logging.info("Deleted %s rows" % (firstCount - lastCount))

app = webapp2.WSGIApplication([('/cron/clear', CronController)], debug=True)

logging.debug("loaded cron")
