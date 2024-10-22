from flask import request, jsonify, render_template, redirect, abort
import logging
import licensing
import login
import dao
import fcmhelper
import json
import traceback
import jinja2
import os

import secret_manager

jinja_environment = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))
MinAndroidVersion = 8
MinScriptVersion = 2
LatestScriptVersion = 24

LOGIN_REDIRECT_URI = os.getenv('LOGIN_REDIRECT_URI')
CLIENT_ID = secret_manager.get_secret("oauth2-client-id")

#if not LOGIN_REDIRECT_URI:
#    LOGIN_REDIRECT_URI = 'http://127.0.0.1:5000/oauth2callback';

login_url = f'https://accounts.google.com/o/oauth2/auth?client_id={CLIENT_ID}&redirect_uri={LOGIN_REDIRECT_URI}&response_type=code&scope=openid%20email'
logout_url = "/logout"

def getAndroidServerMessage(data):
    if "version" in data:
        logging.debug("Validating version: " + str(data["version"]))
        try:
            if int(data["version"]) < MinAndroidVersion:
                logging.warning('Client has too old version')
                return (
                    False,
                    "Too old version! Get latest version of IrssiNotifier from https://irssinotifier.appspot.com")
        except ValueError:
            logging.warning('Client version is not integer')
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
            logging.warning('Irssi script version is not integer')
            return False, "Update your IrssiNotifier script to latest version from https://irssinotifier.appspot.com"
    return True, ""


class BaseController:
    def __init__(self):
        self.data = {}
        self.irssi_user = None

    def init_controller(self, name, param_requirements, allowApiTokenUse):
        logging.info(f"Method started: {name}")

        if request.args:
            self.data = request.args.to_dict()
        if not self.data and request.get_json(force=True, silent=True):
            self.data = request.get_json(force=True)
        if not self.data and request.form:
            self.data = request.form.to_dict()
        logging.debug(f"Data {self.data}")

        self.irssi_user = login.get_irssi_user(self.data, allowApiTokenUse)
        if not self.irssi_user:
            abort(401)

        if not self.validate_params(self.data, param_requirements):
            abort(400)
        return True

    def validate_params(self, data, params):
        for i in params:
            if i not in data:
                logging.warning(f"data error: {i} not in {list(data.keys())}")
                return False
        return True


class WebController(BaseController):
    def get(self):
        logging.debug("WebController.get()")
        user = login.get_irssi_user(request.args, False)

        tokens = []
        irssi_script_version = 0
        registration_date = 'Aeons ago'
        last_notification_time = 'Upgrade to Plus to see'
        notification_count_since_licensed = 'Upgrade to Plus to see'
        license_type = 'Free'
        irssi_working = False
        license_timestamp = 0

        if user is not None:
            tokens = dao.get_fcm_tokens_for_user(user)

            for token in tokens:
                if token.registration_date is not None:
                    token.registration_date_string = str(token.registration_date)
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
            'login_url': login_url,
            'logout_url': logout_url,
            'irssi_working': irssi_working,
            'irssi_latest': irssi_script_version >= LatestScriptVersion,
            'registration_date': registration_date,
            'last_notification_time': last_notification_time,
            'notification_count_since_licensed': notification_count_since_licensed,
            'license_type': license_type,
            'license_timestamp': license_timestamp
        }

        return render_template('html/index.html', **template_values)


class SettingsController(BaseController):
    def post(self):
        success = self.init_controller("SettingsController.post()", ["Name", "Enabled", "RegistrationId"], True) #todo: should block api token use?
        if not success:
            return '', 400

        cont, server_message = getAndroidServerMessage(self.data)
        if not cont:
            return jsonify({'servermessage': server_message})

        dao.save_settings(self.irssi_user, self.data["RegistrationId"], bool(int(self.data["Enabled"])),
                          self.data["Name"])

        return jsonify({'response': 'ok'})


class MessageController(BaseController):
    def post(self):
        success = self.init_controller("MessageController.post()", ["message", "channel", "nick", "version"], True)
        if not success:
            return '', 400

        if request.args:
            self.data = request.args.to_dict()
        if not self.data and request.get_json(force=True, silent=True):
            self.data = request.get_json(force=True)
        if not self.data and request.form:
            self.data = request.form.to_dict()
        logging.debug(f"MessageController.Post() {self.data}")

        cont, server_message = getIrssiServerMessage(self.data)
        if not cont:
            return server_message

        try:
            fcm_message = dao.add_message(self.irssi_user, self.data["message"], self.data['channel'], self.data['nick'])
            dao.update_irssi_user_from_message(self.irssi_user, int(self.data['version']))
            self.data['fcm_message'] = fcm_message.to_fcm_json()
            fcmhelper.send_fcm_to_user_deferred(self.data)
            dao.update_irssi_user_from_message(self.irssi_user, int(self.data['version']))
        except Exception:
            logging.warning(f"Error while creating new message, exception {traceback.format_exc()}")
            return '', 400

        return server_message

    def get(self):
        success = self.init_controller("MessageController.get()", ["version"], True) # todo allow api key?
        if not success:
            return '', 400

        cont, server_message = getAndroidServerMessage(self.data)
        if not cont:
            return jsonify({'servermessage': server_message, "messages": []})

        timestamp = self.data.get("timestamp", 0)

        messages = dao.get_messages(self.irssi_user, timestamp)
        message_jsons = [message.to_json() for message in messages]
        return jsonify({"servermessage": server_message, "messages": message_jsons})


class DeferredMessageController(BaseController):
    def post(self):
        logging.debug(f'DeferredMessageController.post() {request.data}')
        fcmhelper._send_fcm_to_user(request);
        return jsonify({'response': 'ok'})


class CommandController(BaseController):
    def post(self):
        success = self.init_controller("CommandController.post()", ["command"], True)
        if not success:
            return '', 400

        try:
            self.data['fcm_message'] = json.dumps({"command": self.data['command']})
            fcmhelper.send_fcm_to_user_deferred(self.data)
        except:
            return '', 400

        return ""


class WipeController(BaseController):
    def post(self):
        success = self.init_controller("WipeController.post()", [], False)
        if not success:
            return '', 400

        if 'RegistrationId' in self.data:
            token_key = self.data['RegistrationId']
            logging.info('Removing FCM Token: %s' % token_key)
            token = dao.get_fcm_token_for_id(self.irssi_user, token_key)
            if token is not None:
                dao.remove_fcm_token(token)
            else:
                logging.warning('FCM Token to be removed not found!')
        else:
            dao.wipe_user(self.irssi_user)

        return login.logout()


#untested with new version
class CronController:
    def get(self):
        logging.info("Clearing data")
        dao.clear_old_messages()
        return '', 200


#untested with new version
class NonceController(BaseController):
    def get(self):
        success = self.init_controller("NonceController.get()", [], True) # todo allow api key?
        if not success:
            return '', 400

        nonce = dao.get_new_nonce(self.irssi_user)
        return str(nonce.nonce)


#untested with new version
class LicensingController(BaseController):
    def post(self):
        success = self.init_controller("LicensingController.post()", ['SignedData', 'Signature'], True) # todo allow api key?
        if not success:
            return '', 400

        logging.info('Verifying license for user %s' % self.irssi_user.email)

        ok = licensing.Licensing().check_license(self.irssi_user, self.data['SignedData'], self.data['Signature'])

        if ok:
            return 'OK'
        else:
            return '', 403