from google.appengine.api import users

import webapp2
import logging
import login
import dao
import gcmhelper
import jinja2
import os
import json

jinja_environment = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))
MinAndroidVersion = 8
MinScriptVersion = 2
LatestScriptVersion = 15


def getAndroidServerMessage(data):
    if "version" in data:
        logging.debug("Validating version: " + data["version"])
        try:
            if int(data["version"]) < MinAndroidVersion:
                logging.warn('Client has too old version')
                return (
                    False,
                    "Too old version! Get latest version of IrssiNotifier from https://irssinotifier.appspot.com")
        except ValueError:
            logging.warn('Client version is not integer')
            return (
                False, "Too old version! Get latest version of IrssiNotifier from https://irssinotifier.appspot.com")
    else:
        logging.warn("Unable to validate version, no version in data")
    return True, ""


def getIrssiServerMessage(data):
    if "version" in data:
        try:
            if int(data["version"]) < MinScriptVersion:
                return (
                    False, "Update your IrssiNotifier script to latest version from https://irssinotifier.appspot.com")
        except ValueError:
            logging.warn('Irssi script version is not integer')
            return False, "Update your IrssiNotifier script to latest version from https://irssinotifier.appspot.com"
    return True, ""


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

        logging.debug("Data {0}".format(self.data))

        self.irssi_user = login.get_irssi_user(self.data)
        if not self.irssi_user:
            self.response.status = "401 Unauthorized"
            return False

        if not self.validate_params(self.data, paramRequirements):
            self.response.status = "400 Bad Request"
            return False
        return True

    def decode_params(self, request):
        logging.debug("Decoding parameters: %s" % request.body)
        d = request.body
        pairs = d.split('&')
        data = {}
        for pair in pairs:
            if len(pair) < 2:
                break
            split = pair.split('=')
            key = split[0]
            value = split[1]
            data[key] = value
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
        user = login.get_irssi_user(self.request.params)

        tokens = []
        irssi_script_version = 0
        registration_date = 'Aeons ago'
        last_notification_time = 'Never'
        notification_count = 0

        if user is not None:
            tokens = dao.get_gcm_tokens_for_user(user)

            for token in tokens:
                if token.registration_date is not None:
                    token.registration_date_string = token.registration_date
                else:
                    token.registration_date_string = 'Yesterday?'

            irssi_script_version = user.irssi_script_version
            if irssi_script_version is None:
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
            'irssi_latest': irssi_script_version >= LatestScriptVersion,
            'registration_date': registration_date,
            'last_notification_time': last_notification_time,
            'notification_count': notification_count
        }

        template = jinja_environment.get_template('html/index.html')
        self.response.out.write(template.render(template_values))


class SettingsController(BaseController):
    def post(self):
        success = self.initController("SettingsController.post()", ["Name", "Enabled", "RegistrationId", "version"])
        if not success:
            return self.response

        self.response.headers['Content-Type'] = 'application/json'
        (cont, serverMessage) = getAndroidServerMessage(self.data)
        if not cont:
            self.response.out.write(json.dumps({'servermessage': serverMessage}))
            return self.response

        dao.save_settings(self.irssi_user, self.data["RegistrationId"], bool(int(self.data["Enabled"])),
                          self.data["Name"])

        responseJson = json.dumps({'response': 'ok'})

        self.response.out.write(responseJson)


class MessageController(BaseController):
    def post(self):
        success = self.initController("MessageController.post()", ["message", "channel", "nick", "version"])
        if not success:
            return self.response

        (cont, serverMessage) = getIrssiServerMessage(self.data)
        if not cont:
            self.response.out.write(serverMessage)
            return self.response

        try:
            message = dao.add_message(self.irssi_user, self.data["message"], self.data['channel'], self.data['nick'])
            dao.update_irssi_user(self.irssi_user, int(self.data['version']))
            gcmhelper.send_gcm_to_user_deferred(self.irssi_user, message.to_gcm_json())
        except Exception as e:
            logging.warn("Error while creating new message, exception %s", e)
            self.response.status = '400 Bad Request'
            return self.response

        self.response.out.write(serverMessage)

    def get(self):
        val = self.initController("MessageController.get()", ["version"])
        if not val:
            return self.response

        self.response.headers['Content-Type'] = 'application/json'
        (cont, serverMessage) = getAndroidServerMessage(self.data)
        if not cont:
            self.response.out.write(json.dumps({'servermessage': serverMessage, "messages": []}))
            return self.response

        if "timestamp" in self.data:
            timestamp = self.data["timestamp"]
        else:
            timestamp = 0

        messages = dao.get_messages(self.irssi_user, timestamp)
        message_jsons = [message.ToJson() for message in messages]
        response_json = json.dumps({"servermessage": serverMessage, "messages": message_jsons})

        self.response.out.write(response_json)


class WipeController(BaseController):
    def post(self):
        val = self.initController("WipeController.post()", [])
        if not val:
            return self.response

        dao.wipe_user(self.irssi_user)

        responseJson = json.dumps({'response': 'ok'})
        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class AdminController(BaseController):
    def get(self):
        self.redirect('https://appengine.google.com/dashboard?&app_id=s~irssinotifier')


class AnalyticsController(BaseController):
    def get(self):
        self.redirect(
            'https://www.google.com/analytics/web/?pli=1#report/visitors-overview/a29331277w55418008p56422952/')


class CronController(webapp2.RequestHandler):
    def get(self):
        logging.info("Clearing data")
        dao.clear_old_messages()
