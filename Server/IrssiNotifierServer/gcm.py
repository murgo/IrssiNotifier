import urllib2
import logging
from datamodels import GcmToken, AuthKey
from urllib2 import HTTPError
import json


def isset(key, arr):
    return key in arr and arr[key] is not None and arr[key] != ""


class GCM(object):
    authkey = None

    def __init__(self):
        self.authkey = self.loadAuthKey()
        if self.authkey is None:
            logging.error("No auth key for GCM!")
            
            # hack for updating auth key
            # key = AuthKey(key_name = "GCM_AUTHKEY")
            # key.gcm_authkey = 'secrets'
            # key.put()


    def loadAuthKey(self):
        logging.debug("Loading auth key from datastore")
        key = AuthKey.get_by_key_name("GCM_AUTHKEY")
        if key is None:
            return None

        return key.gcm_authkey 

    def sendGcmToUser(self, irssiuser, message):
        logging.debug("Sending gcm message to user %s" % irssiuser.user_name)
        tokens = GcmToken.all()
        tokens.ancestor(irssiuser.key())
        tokensList = tokens.fetch(10)
        for token in tokensList:
            if token.enabled:
                self.sendGcm(token, message)

    def sendGcm(self, token, message):
        logging.debug("Sending gcm message to token %s" % token.gcm_token)

        if self.authkey is None:
            logging.error("Unable to send GCM message because auth key is not set")
            return

        request = urllib2.Request("https://android.googleapis.com/gcm/send")
        request.add_header('Authorization', 'key=%s' % self.authkey)
        request.add_header('Content-Type', 'application/json')
        
        jsonRequest = {}
        jsonRequest['registration_ids'] = [token.gcm_token]
        jsonRequest['data'] = {'message' : message}
        
        request.add_data(json.dumps(jsonRequest))

        try:
            response = urllib2.urlopen(request)
            text = response.read()
            responseJson = json.loads(text)
        except HTTPError as e:
            if (e.code == 503):
                # TODO: retry
                return
            else:
                logging.error("Unable to send GCM message! Response code: %s, response text: %s " % (e.code, text))
                return
        except:
            logging.error("Unable to send GCM message!")
            raise
        
        logging.debug("GCM Message sent, response: %s" % text)
        
        if responseJson['failure'] == '0' and responseJson['canonical_ids'] == '0':
            return #success
        
        results = responseJson["results"]
        for result in results:
            if isset("message_id", result):
                if isset("registration_id", result):
                    token.gcm_token = result["registration_id"]
                    token.put()
            else:
                if isset("error", result):
                    logging.warn("Error sending GCM message: " + result["error"])
                    if (result["error"] == "Unavailable"):
                        # TODO: retry
                        pass
                    elif (result["error"] == "NotRegistered"):
                        token.delete()
                    else:
                        logging.error("Unrecoverable error: " + result["error"])
                        pass
