import webapp2
import logging
import json
import uuid
import time
import traceback

from google.appengine.api import users
from google.appengine.ext import db
import urllib2

import jinja2
import os

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
        user = getIrssiUser(self.request.params)

        if not user:
            template = jinja_environment.get_template('login.html')
            template_values = { 'login_url': users.create_login_url(self.request.uri) }
            self.response.out.write(template.render(template_values))
            return
        
        tokens = C2dmToken.all()
        tokens.ancestor(user.key())
        tokensList = tokens.fetch(10)

        template_values = {
             'user': user,
             'tokens': tokensList,
             'logout_url': users.create_logout_url(self.request.uri),
        }
        logging.debug(template_values)
        logging.debug(tokensList)

        template = jinja_environment.get_template('index.html')
        self.response.out.write(template.render(template_values))


class C2DM(object):
    def loadAuthKey(self):
        return None
    
    def saveAuthKey(self, auth):
        pass
    
    def getAuthKey(self):
        logging.info("generating c2dm auth key")
        
        f = open("secret.txt", 'r')
        line = f.readline()
        f.close()
        
        split = line.split(' ')
        email = split[0]
        passwd = split[1]
        
        request = urllib2.Request("https://www.google.com/accounts/ClientLogin")
        request.add_data('Email=%s&Passwd=%s&service=ac2dm' % (email, passwd))
        
        # TODO: errors from the next line, possible email alarms
        response = urllib2.urlopen(request)
        lines = response.readlines()
        logging.debug("auth key response: %s" % lines)
        #sid = lines[0][4:-1]
        #lsid = lines[1][5:-1]
        auth = lines[2][5:-1]
        self.saveAuthKey(lines)
        return auth
    
    def sendC2dmToUser(self, irssiuser, message):
        tokens = C2dmToken.all()
        tokens.ancestor(irssiuser.key())
        tokensList = tokens.fetch(10)
        for token in tokensList:
            if token.enabled:
                self.sendC2dm(token.c2dm_token, message)
        
    def sendC2dm(self, token, message):
        logging.debug("Sending c2dm message")
        authkey = self.loadAuthKey()
        if authkey is None:
            authkey = self.getAuthKey()
            
        logging.debug("Auth key: %s" % authkey)

        request = urllib2.Request("https://android.apis.google.com/c2dm/send")
        request.add_header('Authorization', 'GoogleLogin auth=%s' % authkey)
        request.add_data('registration_id=%s&data.message=%s&collapse_key=%s' % (token, message, "ck"))
        
        # TODO: errors from the next line, possible email alarms
        response = urllib2.urlopen(request)
        text = response.read()
        if "id=" in text: return True
        
        # TODO "Error="
        logging.warning(text)


def generateApiToken():
    return str(uuid.uuid4())


def getIrssiUser(params):
    user = users.get_current_user()
    if not user:
        logging.debug("No Google user found")
        if 'apiToken' not in params:
            logging.debug("No token found, failing")
            return None
        token = params['apiToken']
        logging.debug("apiToken %s" % token)
        irssi_users = IrssiUser.all()
        irssi_users.filter('api_token = ', token)
        return irssi_users.get()
    
    logging.debug("Google user found")
    #logging.debug("%s %s %s %s %s" % (user.nickname(), user.user_id(), user.email(), user.federated_provider(), user.federated_identity()))
    
    feder = "%s%s" % (user.federated_provider(), user.federated_identity())
    user_id = user.user_id() if user.user_id() is not None else feder

    irssi_user = IrssiUser.get_by_key_name(user_id)
    if irssi_user is None:
        irssi_user = IrssiUser(key_name=user_id)
        irssi_user.user_id = user_id
        irssi_user.user_name = user.nickname()
        irssi_user.email = user.email()
        irssi_user.api_token = generateApiToken() 
        irssi_user.put()
    
    return irssi_user


class IrssiUser(db.Model):
    user_name = db.StringProperty()
    email = db.StringProperty()
    user_id = db.StringProperty()
    api_token = db.StringProperty()


class C2dmToken(db.Model):
    c2dm_token = db.StringProperty()
    enabled = db.BooleanProperty()
    name = db.StringProperty()
    # TODO some device info perhaps
    # TODO maybe it's better to use more generic names than c2dm, and extract c2dm sender into it's own controller
    # TODO also token types are nice, how to set default stuff to Google


class Message(db.Model):
    server_timestamp = db.FloatProperty()
    timestamp = db.StringProperty()
    message = db.StringProperty()
    channel = db.StringProperty()
    nick = db.StringProperty()
    def ToJson(self):
        return json.dumps({'server_timestamp': self.server_timestamp, 'timestamp': self.timestamp, 'message': self.message, 'channel': self.channel, 'nick': self.nick})


class SettingsHandler(object):
    def handle(self, user, array):
        newToken = array["RegistrationId"]

        tokens = C2dmToken.all()
        tokens.ancestor(user.key())
        tokens.filter("c2dm_token =", newToken)
        t = tokens.get()
        
        if not t:
            logging.debug("Adding new token: " + newToken)
            tokenToAdd = C2dmToken(parent = user.key())
            tokenToAdd.c2dm_token = newToken
            tokenToAdd.enabled = bool(int(array["Enabled"]))
            tokenToAdd.name = array["Name"]
            tokenToAdd.put()
        else:
            logging.debug("Updating token: " + newToken)
            t.enabled = bool(int(array["Enabled"]))
            t.name = array["Name"]
            t.put()


class MessageHandler(object):
    def handle(self, irssiuser, array):
        try:
            logging.debug("Adding new message: %s" % array)
            dbMessage = Message(parent = irssiuser.key())
            dbMessage.message = array["message"]
            dbMessage.channel = array['channel'] #TODO
            dbMessage.nick = array['nick'] #TODO
            dbMessage.timestamp = array['timestamp'] #TODO
            dbMessage.server_timestamp = time.time()
            dbMessage.put()
        except Exception as e:
            logging.error("Error while creating new message, exception %s", e)
            traceback.print_exception(e)
            return False
            
        c2dm = C2DM()
        c2dm.sendC2dmToUser(irssiuser, dbMessage.ToJson()) #TODO don't send whole message?
        
        return True


class SettingsController(webapp2.RequestHandler):
    def post(self):
        logging.debug("settingscontroller start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            #TODO super ugly hack, stupid HttpPost not accepting params in android
            # Return JSON as request body? Switch to using UrlConnection?
            d = self.request.body
            s = d.split('&')
            logging.debug(s)
            for l in s:
                if (len(l) < 2): break
                logging.debug(l)
                spl = l.split('=')
                logging.debug(spl)
                k = spl[0]
                v = spl[1]
                data[k] = v
        logging.debug(data)


        irssiUser = getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
        
        logging.debug(self.request.params)
        logging.debug(self.request.body)
        
        for i in ["RegistrationId", 'Name', 'Enabled']:
            if i not in data:
                logging.error("data error: %s not in %s" % (i, [x for x in data]))
                self.response.status = "400 Bad Request"
                return self.response

        settingsHandler = SettingsHandler()
        settingsHandler.handle(irssiUser, data)
        
        responseJson = json.dumps({'response': 'ok', 'message': 'lol' })

        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class MessageController(webapp2.RequestHandler):
    def post(self):
        logging.debug("messagecontroller start")
        
        data = {}
        if len(self.request.params) > 0:
            data = self.request.params
        else:
            #TODO super ugly hack, stupid HttpPost not accepting params in android
            # Return JSON as request body? Switch to using UrlConnection?
            d = self.request.body
            s = d.split('&')
            logging.debug(s)
            for l in s:
                if (len(l) < 2): break
                logging.debug(l)
                spl = l.split('=')
                logging.debug(spl)
                k = spl[0]
                v = spl[1]
                data[k] = v
        logging.debug(data)

        irssiUser = getIrssiUser(data)
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return self.response
       
        for i in ["message"]:
            if i not in data or len(data[i]) == 0:
                logging.error("data error: %s not in %s" % (i, self.request.params))
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


def handle_404(request, response, exception):
    logging.debug("404'd")
    logging.exception(exception)
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication([('/', Main), ('/API/Settings', SettingsController), ('/Api/Message', MessageController)], debug=True)
app.error_handlers[404] = handle_404

logging.debug("Hello reinstall: loaded main")
