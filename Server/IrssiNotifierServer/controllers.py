import traceback
from google.appengine.api import users

import webapp2
import logging
import licensing
import login
import dao
import gcmhelper
import jinja2
import os
import json

jinja_environment = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))
MinAndroidVersion = 8
MinScriptVersion = 2
LatestScriptVersion = 24


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
        last_notification_time = 'Upgrade to Plus to see'
        notification_count_since_licensed = 'Upgrade to Plus to see'
        license_type = 'Free'
        irssi_working = False
        license_timestamp = 0

        if user is not None:
            tokens = dao.get_gcm_tokens_for_user(user)

            for token in tokens:
                if token.registration_date is not None:
                    token.registration_date_string = token.registration_date
                else:
                    token.registration_date_string = 'Yesterday?'

            if user.license_timestamp is not None:
                license_type = 'Plus'
                license_timestamp = user.license_timestamp

                if user.last_notification_time is not None:
                    last_notification_time = user.last_notification_time
                else:
                    last_notification_time = 'Never'

                if user.notification_count_since_licensed is not None:
                    notification_count_since_licensed = user.notification_count_since_licensed
                else:
                    notification_count_since_licensed = 0

            irssi_script_version = user.irssi_script_version
            if irssi_script_version is None:
                irssi_script_version = 0

            if user.registration_date is not None:
                registration_date = user.registration_date

            if user.last_notification_time is not None:
                irssi_working = True

        template_values = {
            'user': user,
            'tokens': tokens,
            'token_count': len(tokens),
            'logged_in': user is not None,
            'login_url': users.create_login_url("#profile").replace("&", "&amp;"),
            'logout_url': users.create_logout_url("").replace("&", "&amp;"),
            'irssi_working': irssi_working,
            'irssi_latest': irssi_script_version >= LatestScriptVersion,
            'registration_date': registration_date,
            'last_notification_time': last_notification_time,
            'notification_count_since_licensed': notification_count_since_licensed,
            'license_type': license_type,
            'license_timestamp': license_timestamp
        }

        template = jinja_environment.get_template('html/index.html')
        self.response.out.write(template.render(template_values))


class SettingsController(BaseController):
    def post(self):
        success = self.initController("SettingsController.post()", ["Name", "Enabled", "RegistrationId"])
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
            dao.update_irssi_user_from_message(self.irssi_user, int(self.data['version']))
            gcmhelper.send_gcm_to_user_deferred(self.irssi_user, message.to_gcm_json())
        except:
            logging.warn("Error while creating new message, exception %s", traceback.format_exc())
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
        message_jsons = [message.to_json() for message in messages]
        response_json = json.dumps({"servermessage": serverMessage, "messages": message_jsons})

        self.response.out.write(response_json)


class CommandController(BaseController):
    def post(self):
        success = self.initController("CommandController.post()", ["command"])
        if not success:
            return self.response

        try:
            gcmhelper.send_gcm_to_user_deferred(self.irssi_user, json.dumps({"command": self.data['command']}))
        except:
            self.response.status = '400 Bad Request'
            return self.response

        self.response.out.write("")


class WipeController(BaseController):
    def post(self):
        success = self.initController("WipeController.post()", [])
        if not success:
            return self.response

        if 'RegistrationId' in self.data:
            token_key = self.data['RegistrationId']
            logging.info('Removing GCM Token: %s' % token_key)
            token = dao.get_gcm_token_for_id(self.irssi_user, token_key)
            if token is not None:
                dao.remove_gcm_token(token)
            else:
                logging.warning('GCM Token to be removed not found!')
        else:
            dao.wipe_user(self.irssi_user)

        responseJson = json.dumps({'response': 'ok'})
        self.response.headers['Content-Type'] = 'application/json'
        self.response.out.write(responseJson)


class AdminController(BaseController):
    def get(self):
        from gcm import GCM
        GCM.authkey = None
        self.redirect('https://appengine.google.com/dashboard?&app_id=s~irssinotifier')


class AnalyticsController(BaseController):
    def get(self):
        self.redirect(
            'https://www.google.com/analytics/web/?pli=1#report/visitors-overview/a29331277w55418008p56422952/')


class CronController(webapp2.RequestHandler):
    def get(self):
        logging.info("Clearing data")
        dao.clear_old_messages()


class NonceController(BaseController):
    def get(self):
        val = self.initController("NonceController.get()", [])
        if not val:
            return self.response

        nonce = dao.get_new_nonce(self.irssi_user)
        self.response.out.write(nonce.nonce)


class LicensingController(BaseController):
    def post(self):
        val = self.initController("LicensingController.post()", ['SignedData', 'Signature'])
        if not val:
            return self.response

        logging.info('Verifying license for user %s' % self.irssi_user.email)

        ok = licensing.Licensing().check_license(self.irssi_user, self.data['SignedData'], self.data['Signature'])

        if ok:
            self.response.out.write('OK')
        else:
            self.response.status = '403 Forbidden'
