import webapp2
import logging
from controllers import CronController

app = webapp2.WSGIApplication([('/cron/clear', CronController)], debug=True)

logging.debug("loaded cron")
