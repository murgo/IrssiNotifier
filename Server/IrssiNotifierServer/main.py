import webapp2
import logging
from google.appengine.api import users

import jinja2
import os

from settingshandler import SettingsHandler
from messagehandler import MessageHandler
from login import Login
from datamodels import C2dmToken, Message
import json
from wipehandler import WipeHandler

jinja_environment = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))

class BaseController(webapp2.RequestHandler):
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


class Main(BaseController):
    def get(self):
        logging.debug("main start")
        user = Login().getIrssiUser(self.request.params)

        if not user:
            template = jinja_environment.get_template('html/login.html')
            template_values = { 'login_url': users.create_login_url(self.request.uri) }
            self.response.out.write(template.render(template_values))
            return
        
        tokens = C2dmToken.all()
        tokens.ancestor(user.key())
        tokensList = tokens.fetch(10)

        messages = Message.all()
        messages.ancestor(user.key())
        count = messages.count(1)

        template_values = {
             'user': user,
             'tokens': tokensList,
             'logout_url': users.create_logout_url(self.request.uri),
             'working': count != 0,
        }
        logging.debug(template_values)
        logging.debug(tokensList)

        template = jinja_environment.get_template('html/index.html')
        self.response.out.write(template.render(template_values))


def decode_params(request):
    #TODO super ugly hack, stupid HttpPost not accepting params in android
    # Return JSON as request body? Switch to using UrlConnection?
    d = request.body
    s = d.split('&')
    logging.debug(s)
    data = {}
    for l in s:
        if (len(l) < 2): break
        logging.debug(l)
        spl = l.split('=')
        logging.debug(spl)
        k = spl[0]
        v = spl[1]
        data[k] = v
    return data

def validate_params(data, params):
    for i in params:
        if i not in data:
            logging.error("data error: %s not in %s" % (i, [x for x in data]))
            return False
    return True


class SettingsController(webapp2.RequestHandler):
    def post(self):
        logging.debug("settingscontroller start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            data = decode_params(self.request)
        logging.debug(data)

        irssiUser = Login().getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
        
        logging.debug(self.request.params)
        logging.debug(self.request.body)

        if not validate_params(data, ["RegistrationId", "Name", "Enabled"]):
            self.response.status = "400 Bad Request"
            return self.response
            
        settingsHandler = SettingsHandler()
        settingsHandler.handle(irssiUser, data)
        
        responseJson = json.dumps({'response': 'ok', 'message': 'lol' })

        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class MessageController(webapp2.RequestHandler):
    def post(self):
        logging.debug("messagecontroller post start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            data = decode_params(self.request)
        logging.debug(data)

        irssiUser = Login().getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
       
        if not validate_params(data, ["message", "channel", "nick", "timestamp"]):
            self.response.status = "400 Bad Request"
            return self.response
            
        messageHandler = MessageHandler()
        ok = messageHandler.handle(irssiUser, data)

        if ok:
            responseJson = json.dumps({'response': 'ok' })
        else:
            responseJson = json.dumps({'response': 'fail' })
            self.response.status = '400 Bad Request'

        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)

    def get(self):
        #untested
        logging.debug("messagecontroller get start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            data = decode_params(self.request)
        logging.debug(data)

        irssiUser = Login().getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
       
        if not validate_params(data, ["timestamp"]):
            self.response.status = "400 Bad Request"
            return self.response

        messageHandler = MessageHandler()
        messages = messageHandler.getMessages(data, irssiUser)
        messageJsons = []
        for message in messages:
            messageJsons.push(message.ToJson())
        responseJson = json.dumps(messageJsons)
        #TODO: dump custom message here

        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class WipeController(webapp2.RequestHandler):
    def post(self):
        logging.debug("wipecontroller start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            data = decode_params(self.request)
        logging.debug(data)

        irssiUser = Login().getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
       
        handler = WipeHandler()
        handler.handle(irssiUser)

        responseJson = json.dumps({'response': 'ok' })
        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


def handle_404(request, response, exception):
    logging.debug("404'd")
    logging.exception(exception)
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication([('/', Main), ('/API/Settings', SettingsController), ('/Api/Message', MessageController), ('/Api/Wipe', WipeController)], debug=True)
app.error_handlers[404] = handle_404

logging.debug("Hello reinstall: loaded main")
