import webapp2
import logging
from google.appengine.api import users

import jinja2
import os

from settingshandler import SettingsHandler
from messagehandler import MessageHandler
from login import Login
from datamodels import GcmToken
import json
from wipehandler import WipeHandler
import emaillogginghandler

jinja_environment = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))

class BaseController(webapp2.RequestHandler):
    data = {}
    def handle_exception(self, exception, debug):
        # Log the error.
        logging.exception(exception)
    
        # Set a custom message.
        self.response.write('An error occurred.')

        # If the exception is a HTTPException, use its error code.
        # Otherwise use a generic 500 error code.
        if isinstance(exception, webapp2.HTTPException):
            self.response.set_status(exception.code)
        else:
            self.response.set_status(500)

    def initController(self, name, paramRequirements):
        logging.info("Method started: %s" % name)
    
        if len(self.request.params) > 0:
            self.data = self.request.params
        else:
            try:
                self.data = self.decode_params(self.request)
            except:
                # because of weird assertion error
                self.data = {}
    
        logging.debug("Data: %s" % self.data)
        
        self.irssiUser = Login().getIrssiUser(self.data)
        if not self.irssiUser:
            self.response.status = "401 Unauthorized"
            return False
        
        if not self.validate_params(self.data, paramRequirements):
            self.response.status = "400 Bad Request"
            return False
        return True

    def decode_params(self, request):
        logging.debug("Decoding parameters: %s" % request.body)
        #TODO super ugly hack, stupid HttpPost not accepting params in android
        # Return JSON as request body? Switch to using UrlConnection?
        d = request.body
        s = d.split('&')
        data = {}
        for l in s:
            if (len(l) < 2): break
            spl = l.split('=')
            k = spl[0]
            v = spl[1]
            data[k] = v
        return data
    
    def validate_params(self, data, params):
        for i in params:
            if i not in data:
                logging.warn("data error: %s not in %s" % (i, [x for x in data]))
                return False
        return True
    


class WebController(BaseController):
    def get(self):
        logging.debug("WebController.get()")
        user = Login().getIrssiUser(self.request.params)
        
        tokens = []
        irssi_script_version = 0
        registration_date = 'Aeons ago'
        last_notification_time = 'Never'
        notification_count = 0

        if user is not None:
            tokens = GcmToken.all()
            tokens.ancestor(user.key())
            tokens = tokens.fetch(10)
            for token in tokens:
                if token.registration_date is not None:
                    token.registration_date_string = token.registration_date
                else:
                    token.registration_date_string = 'Yesterday?'

            irssi_script_version = user.irssi_script_version
            if irssi_script_version == None:
                irssi_script_version = 0
                
            if user.registration_date is not None:
                registration_date = user.registration_date

            if user.last_notification_time is not None:
                last_notification_time = user.last_notification_time

            if user.notification_count is not None:
                notification_count = user.notification_count

        template_values = {
             'user': user,
             'tokens': tokens,
             'token_count': len(tokens),
             'logged_in': user is not None,
             'login_url': users.create_login_url("#profile").replace("&", "&amp;"),
             'logout_url': users.create_logout_url("").replace("&", "&amp;"),
             'irssi_working': last_notification_time != 'Never',
             'irssi_latest': irssi_script_version >= 14,
             'registration_date': registration_date,
             'last_notification_time': last_notification_time,
             'notification_count': notification_count
        }

        template = jinja_environment.get_template('html/index.html')
        self.response.out.write(template.render(template_values))


def getAndroidServerMessage(data):
    if "version" in data:
        logging.debug("Validating version: " + data["version"])
        try:
            if int(data["version"]) < 8:
                logging.warn('Client has too old version')
                return (False, "Too old version! Get latest version of IrssiNotifier from https://irssinotifier.appspot.com")
        except ValueError:
            logging.warn('Client version is not integer')
            return (False, "Too old version! Get latest version of IrssiNotifier from https://irssinotifier.appspot.com")
    else:
        logging.warn("Unable to validate version, no version in data")
    return (True, "")


def getIrssiServerMessage(data):
    if "version" in data:
        try:
            if int(data["version"]) < 2:
                return (False, "Update your IrssiNotifier script to latest version from https://irssinotifier.appspot.com")
        except ValueError:
            logging.warn('Irssi script version is not integer')
            return (False, "Update your IrssiNotifier script to latest version from https://irssinotifier.appspot.com")
    return (True, "")


class SettingsController(BaseController):
    def post(self):
        val = self.initController("SettingsController.post()", ["Name", "Enabled", "RegistrationId", "version"])
        if not val:
            logging.debug("InitController returned false")
            return self.response

        self.response.headers['Content-Type'] = 'application/json'
        (cont, serverMessage) = getAndroidServerMessage(self.data)
        if not cont:
            self.response.out.write(json.dumps({ 'servermessage': serverMessage }))
            return self.response

        settingsHandler = SettingsHandler()
        settingsHandler.handle(self.irssiUser, self.data)
        
        responseJson = json.dumps({ 'response': 'ok' })

        self.response.out.write(responseJson)


class MessageController(BaseController):
    def post(self):
        val = self.initController("MessageController.post()", ["message", "channel", "nick", "version"])
        if not val:
            logging.debug("InitController returned false")
            return self.response

        (cont, serverMessage) = getIrssiServerMessage(self.data)
        if not cont:
            self.response.out.write(serverMessage)
            return self.response

        messageHandler = MessageHandler()
        ok = messageHandler.handle(self.irssiUser, self.data)

        if not ok:
            self.response.status = '400 Bad Request'
            return False
        self.response.out.write(serverMessage)

    def get(self):
        val = self.initController("MessageController.get()", ["version"])
        if not val:
            logging.debug("InitController returned false")
            return self.response

        self.response.headers['Content-Type'] = 'application/json'
        (cont, serverMessage) = getAndroidServerMessage(self.data)
        if not cont:
            self.response.out.write(json.dumps({ 'servermessage': serverMessage, "messages" : [] }))
            return self.response

        if "timestamp" not in self.data:
            self.data["timestamp"] = 0

        messageHandler = MessageHandler()
        messages = messageHandler.getMessages(self.data["timestamp"], self.irssiUser)
        messageJsons = []
        for message in messages:
            messageJsons.append(message.ToJson())
        responseJson = json.dumps({"servermessage": serverMessage, "messages": messageJsons})

        self.response.out.write(responseJson)


class WipeController(BaseController):
    def post(self):
        val = self.initController("WipeController.post()", [])
        if not val:
            logging.debug("InitController returned false")
            return self.response
        
        handler = WipeHandler()
        handler.handle(self.irssiUser)

        responseJson = json.dumps({'response': 'ok' })
        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class AdminController(BaseController):
    def get(self):
        self.redirect('https://appengine.google.com/dashboard?&app_id=s~irssinotifier')


class AnalyticsController(BaseController):
    def get(self):
        self.redirect('https://www.google.com/analytics/web/?pli=1#report/visitors-overview/a29331277w55418008p56422952/')


def handle_404(request, response, exception):
    logging.debug("404'd")
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication([('/', WebController), ('/API/Settings', SettingsController), ('/API/Message', MessageController), ('/API/Wipe', WipeController), ('/admin', AdminController), ('/analytics', AnalyticsController)], debug=True)
app.error_handlers[404] = handle_404

logging.debug("loaded main")
emaillogginghandler.register_logger(["irssinotifier@gmail.com"])
