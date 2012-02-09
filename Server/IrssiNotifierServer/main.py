import webapp2
import logging
import json

from google.appengine.api import users
from google.appengine.ext import db
import urllib2

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
        user = users.get_current_user()
        logging.debug(user)

        if user:
            self.response.headers['Content-Type'] = 'text/plain'
            self.response.out.write('Hello, ' + user.nickname())
        else:
            self.redirect(users.create_login_url(self.request.uri))



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


class TestController(BaseController):
    def post(self):
        user = authenticate()
        if not user:
            self.response.out.write("lol no user")

        fail = None
        try:
            c2dm = C2DM()
            c2dm.sendC2dm(user.c2dm, self.request.params['n'])
        except Exception as e:
            fail = e

        self.response.out.write("""<html><body>
        <p>hello %s %s %s %s</p>
        <p>posting %s</p>
        <p>failure: %s</p></body></html> """ % (user.user_name, user.email, user.user_id, user.c2dm, self.request.params['n'], fail))
        
        
    def get(self):
        user = authenticate()
        if not user:
            self.response.out.write("lol no user")

        self.response.out.write("""<html><body>
        <p>hello %s %s %s %s</p>
        <form action="/Test" method="post"><input type="text" name="n" /><input type="submit" value="Submit" /></form></body></html>""" % (user.user_name, user.email, user.user_id, user.c2dm))

def authenticate():
    user = users.get_current_user()
    if not user:
        return None
    
    logging.debug("%s %s %s %s %s" % (user.nickname(), user.user_id(), user.email(), user.federated_provider(), user.federated_identity()))
    
    feder = "%s%s" % (user.federated_provider(), user.federated_identity())
    user_id = user.user_id() if user.user_id() is not None else feder

    irssi_user = IrssiUser.get_or_insert(user_id, user_name = user.nickname(), email = user.email(), user_id = user_id)
    
    return irssi_user


class IrssiUser(db.Model):
    user_name = db.StringProperty()
    email = db.StringProperty()
    user_id = db.StringProperty()
    c2dm = db.StringProperty()


class UserSettings(db.Model):
    pass


class SettingsHandler(object):
    def handle(self, user, jsonObj):
        user.c2dm = jsonObj["RegistrationId"]
        user.put()
        
        user2 = IrssiUser.get_by_key_name(user.user_id)
        
        return {"message": "%s %s %s %s" % (user2.user_name, user2.email, user2.user_id, user2.c2dm)}


class SettingsController(webapp2.RequestHandler):
    def post(self):
        logging.debug("settingscontroller start")
        
        irssiUser = authenticate()
        if not irssiUser:
            self.response.status = "401 Unauthorized"
            return

        data = self.request.body
        logging.debug("data: %s" % data)
        try:
            jsonObj = json.loads(data)
            logging.debug(jsonObj)
        except:
            logging.debug("foo")
        
        settingsHandler = SettingsHandler()
        responseMap = settingsHandler.handle(irssiUser, jsonObj)
        
        responseJson = json.dumps(responseMap)

        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


def handle_404(request, response, exception):
    logging.debug("404'd")
    logging.exception(exception)
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication([('/', Main), ('/API/Settings', SettingsController), ('/Test', TestController)], debug=True)
app.error_handlers[404] = handle_404

logging.debug("Hello reinstall: loaded main")
