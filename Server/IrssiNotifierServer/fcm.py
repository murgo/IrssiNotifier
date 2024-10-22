import json
import socket
import traceback
import requests
import logging
import os

import dao
import firebase_login

PROJECT_ID = os.getenv('PROJECT_ID')
#PROJECT_ID = "irssinotifier-staging"
FcmUrl = f'https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send'


def is_set(key, arr):
    return key in arr and arr[key] is not None and arr[key] != ""

class FCM(object):
    authkey = None

    def __init__(self):
        self.tokens = []
        if FCM.authkey is None:
            # use firebase credentials to refresh access token
            FCM.authkey = firebase_login.getBearerToken()
            if FCM.authkey is None:
                raise Exception("No auth key for FCM!")

    def send_fcm_to_user(self, irssiuser_key, message):
        #logging.debug("Sending fcm message to user %s", irssiuser_key)
        if FCM.authkey is None:
            logging.error("No auth key for FCM!")
            return

        tokens = dao.get_fcm_tokens_for_user_key(irssiuser_key)
        self.send_fcm(tokens, message)

    def send_fcm(self, tokens, message):
        self.tokens = tokens
        logging.info("Sending FCM message to %s tokens", len(self.tokens))
        if FCM.authkey is None:
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
            self.handle_fcm_result(result, token, message)

    def send_request(self, message, tokens):
        headers = {
            'Authorization': 'Bearer %s' % FCM.authkey,
            'Content-Type': 'application/json; UTF-8',
        }

        json_request = {
            'message': {
                'token': '',
                'data': {'message': message},
                'android': {'priority': 'high'},
            }
        }

        response_body = ''

        try:
            for token in tokens:
                json_request['message']['token'] = token.gcm_token
                logging.debug(f"Posting FCM send request to {FcmUrl} , request: {json.dumps(json_request)}")
                response = requests.post(FcmUrl, headers=headers, json=json_request)
                response_body = response.text
                logging.debug("FCM Message sent, response: %s" % response_body)
                if response.status_code == 200:
                    return response.json()
                else:
                    logging.error(
                        "Unable to send FCM message! Response code: %s, response body: %s " % (response.status_code, response_body))
                    return None  # do not retry
        except requests.exceptions.RequestException as e:
            logging.warning("RequestException: Unable to send FCM message! %s" % traceback.format_exc())
            raise Exception("NOMAIL %s " % e)  # retry
        except socket.error as e:
            logging.warning("socket.error: Unable to send FCM message! %s" % traceback.format_exc())
            raise Exception("NOMAIL %s " % e)  # retry
        except:
            logging.error("Unable to send FCM message! %s" % traceback.format_exc())
            return None

    def handle_fcm_result(self, result, token, message):
        if is_set("message_id", result):
            if is_set("registration_id", result):
                new_token = result["registration_id"]
                self.replace_fcm_token_with_canonical(token, new_token)
        else:
            if is_set("error", result):
                error = result["error"]
                logging.warning("Error sending FCM message with authkey %s: %s" % (FCM.authkey, error))
                if error == "Unavailable":
                    logging.warning("Token unavailable, retrying")
                    #fcmhelper.send_fcm_to_token_deferred(token, message)
                elif error == "NotRegistered":
                    logging.warning("Token not registered, deleting token")
                    dao.remove_fcm_token(token)
                elif error == "InvalidRegistration":
                    logging.error("Invalid registration, deleting token")
                    dao.remove_fcm_token(token)
                else:
                    if error == "InternalServerError":
                        logging.warning("InternalServerError in FCM: " + error)
                    else:
                        logging.error("Unrecoverable error in FCM: " + error)

    def replace_fcm_token_with_canonical(self, token, new_token_id):
        already_exists = new_token_id in [t.gcm_token for t in self.tokens]

        if already_exists:
            logging.info("Canonical token already exists, removing old one: %s" % (new_token_id))
            dao.remove_fcm_token(token)
        else:
            logging.info("Updating token with canonical token: %s -> %s" % (token.gcm_token, new_token_id))
            dao.update_fcm_token(token, new_token_id)