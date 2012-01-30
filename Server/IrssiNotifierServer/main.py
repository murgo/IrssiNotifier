import webapp2
import logging
import json

from google.appengine.api import users

class BaseHandler(webapp2.RequestHandler):
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


class Main(BaseHandler):
    def get(self):
        logging.debug("main start")
        user = users.get_current_user()
        logging.debug(user)

        if user:
            self.response.headers['Content-Type'] = 'text/plain'
            self.response.out.write('Hello, ' + user.nickname())
        else:
            self.redirect(users.create_login_url(self.request.uri))


class Api(webapp2.RequestHandler):
    def post(self):
        logging.debug("api start")
        
        data = self.request.body
        logging.debug("data: %s" % data)
        
        try:
            obj = json.loads(data)
            logging.debug(obj)
        except:
            logging.debug("foo")

        user = users.get_current_user()
        logging.debug("user: %s" % user)
        
        nickname = "anonymous"
        if user is not None: nickname = user.nickname()
        
        response = {"message": "hello %s" % nickname}
        
        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(response)


def handle_404(request, response, exception):
    logging.debug("404'd")
    logging.exception(exception)
    response.write("lol 404'd")
    response.set_status(404)


app = webapp2.WSGIApplication([('/', Main), ('/API/', Api)], debug=True)
app.error_handlers[404] = handle_404

logging.debug("loaded main")
