import socket
import traceback
import urllib2
import logging
from httplib import HTTPException
from urllib2 import HTTPError
import json

GcmUrl = "https://fcm.googleapis.com/fcm/send"


def is_set(key, arr):
    return key in arr and arr[key] is not None and arr[key] != ""


class GCM(object):
    authkey = None

    def __init__(self, dao, gcmhelper):
        self.tokens = []
        self.dao = dao
        self.gcmhelper = gcmhelper
        if GCM.authkey is None:
            GCM.authkey = self.dao.load_gcm_auth_key()
            if GCM.authkey is None:
                raise Exception("No auth key for FCM!")

    def send_gcm_to_user(self, irssiuser_key, message):
        logging.debug("Sending gcm message to user %s" % irssiuser_key)
        if GCM.authkey is None:
            logging.error("No auth key for FCM!")
            return

        tokens = self.dao.get_gcm_tokens_for_user_key(irssiuser_key)
        self.send_gcm(tokens, message)

    def send_gcm(self, tokens, message):
        self.tokens = tokens
        logging.info("Sending FCM message to %s tokens" % len(self.tokens))
        if GCM.authkey is None:
            logging.error("No auth key for FCM!")
            return

        if len(self.tokens) == 0:
            logging.info("No tokens, stop sending")
            return

        response_json = self.send_request(message, self.tokens)
        if response_json is None:
            return  # instant failure

        if response_json['failure'] == '0' and response_json['canonical_ids'] == '0':
            return  # success

        results = response_json["results"]
        index = -1
        for result in results:
            index += 1
            token = self.tokens[index]
            self.handle_gcm_result(result, token, message)

    def send_request(self, message, tokens):
        request = urllib2.Request(GcmUrl)
        request.add_header('Authorization', 'key=%s' % GCM.authkey)
        request.add_header('Content-Type', 'application/json')

        json_request = {'data': {'message': message}, 'registration_ids': [], 'priority': 'high'}
        for token in tokens:
            json_request['registration_ids'].append(token.gcm_token)

        request.add_data(json.dumps(json_request))

        response_body = ''

        try:
            response = urllib2.urlopen(request)
            response_body = response.read()
            logging.debug("FCM Message sent, response: %s" % response_body)
            return json.loads(response_body)
        except HTTPError as e:
            if 500 <= e.code < 600:
                raise Exception("NOMAIL %s, retrying whole task" % e.code)  # retry
            else:
                logging.error(
                    "Unable to send FCM message! Response code: %s, response body: %s " % (e.code, response_body))
                return None  # do not retry
        except HTTPException as e:
            logging.warn("HTTPException: Unable to send FCM message! %s" % traceback.format_exc())
            raise HTTPException("NOMAIL %s " % e)  # retry
        except socket.error as e:
            logging.warn("socket.error: Unable to send FCM message! %s" % traceback.format_exc())
            raise HTTPException("NOMAIL %s " % e)  # retry
        except:
            logging.error("Unable to send FCM message! %s" % traceback.format_exc())
            return None

    def handle_gcm_result(self, result, token, message):
        if is_set("message_id", result):
            if is_set("registration_id", result):
                new_token = result["registration_id"]
                self.replace_gcm_token_with_canonical(token, new_token)
        else:
            if is_set("error", result):
                error = result["error"]
                logging.warn("Error sending FCM message with authkey %s: %s" % (GCM.authkey, error))
                if error == "Unavailable":
                    logging.warn("Token unavailable, retrying")
                    self.gcmhelper.send_gcm_to_token_deferred(token, message)
                elif error == "NotRegistered":
                    logging.warn("Token not registered, deleting token")
                    self.dao.remove_gcm_token(token)
                elif error == "InvalidRegistration":
                    logging.error("Invalid registration, deleting token")
                    self.dao.remove_gcm_token(token)
                else:
                    if error == "InternalServerError":
                        logging.warn("InternalServerError in FCM: " + error)
                    else:
                        logging.error("Unrecoverable error in FCM: " + error)

    def replace_gcm_token_with_canonical(self, token, new_token_id):
        already_exists = new_token_id in [t.gcm_token for t in self.tokens]

        if already_exists:
            logging.info("Canonical token already exists, removing old one: %s" % (new_token_id))
            self.dao.remove_gcm_token(token)
        else:
            logging.info("Updating token with canonical token: %s -> %s" % (token.gcm_token, new_token_id))
            self.dao.update_gcm_token(token, new_token_id)
