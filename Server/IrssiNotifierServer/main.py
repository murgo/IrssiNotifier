import webapp2
import logging
import emaillogginghandler
from controllers import *


def handle_404(request, response, exception):
    logging.warn("404'd")
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication(
    [('/', WebController),
     ('/API/Settings', SettingsController),
     ('/API/Message', MessageController),
     ('/API/Command', CommandController),
     ('/API/Wipe', WipeController),
     ('/API/Nonce', NonceController),
     ('/API/License', LicensingController),
     ('/admin', AdminController),
     ('/analytics', AnalyticsController)],
    debug=True)

app.error_handlers[404] = handle_404

logging.debug("loaded main")
emaillogginghandler.register_logger(["irssinotifier@gmail.com"])
