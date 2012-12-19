import urllib2
import logging
from datamodels import GcmToken, AuthKey
from urllib2 import HTTPError
import json
import gcmhelper


def isset(key, arr):
    return key in arr and arr[key] is not None and arr[key] != ""


class GCM(object):
    authkey = None

    def __init__(self):
        if GCM.authkey is None:
            GCM.authkey = self.loadAuthKey()
            if GCM.authkey is None:
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


    def sendGcmToUser(self, irssiuser_key, message):
        logging.debug("Sending gcm message to user %s" % irssiuser_key)
        if GCM.authkey is None:
            logging.error("No auth key for GCM!")
            return
            
        tokens = GcmToken.all()
        tokens.ancestor(irssiuser_key)
        tokens.filter("enabled =", True)
        tokensList = tokens.fetch(10)
        self.sendGcm(tokensList, message)


    def sendGcm(self, tokens, message):
        logging.info("Sending gcm message to %s tokens" % len(tokens))
        if GCM.authkey is None:
            logging.error("No auth key for GCM!")
            return
        
        if (len(tokens) == 0):
            logging.info("No tokens, stop sending")
            return

        if GCM.authkey is None:
            logging.error("Unable to send GCM message because auth key is not set")
            return

        request = urllib2.Request("https://android.googleapis.com/gcm/send")
        request.add_header('Authorization', 'key=%s' % GCM.authkey)
        request.add_header('Content-Type', 'application/json')
        
        jsonRequest = {}
        jsonRequest['data'] = {'message' : message}
        jsonRequest['registration_ids'] = []
        for token in tokens:
            jsonRequest['registration_ids'].append(token.gcm_token)
        
        request.add_data(json.dumps(jsonRequest))

        try:
            response = urllib2.urlopen(request)
            text = response.read()
            responseJson = json.loads(text)
        except HTTPError as e:
            if (e.code == 503):
                raise Exception("503, retrying whole task") #retry
            else:
                logging.error("Unable to send GCM message! Response code: %s, response text: %s " % (e.code, text))
                return # do not retry
        except Exception as e:
            logging.warn("Unable to send GCM message! %s" % e)
            raise e #retry
        
        logging.debug("GCM Message sent, response: %s" % text)
        
        if responseJson['failure'] == '0' and responseJson['canonical_ids'] == '0':
            return #success
        
        results = responseJson["results"]
        index = -1
        for result in results:
            index += 1
            if isset("message_id", result):
                if isset("registration_id", result):
                    newid = result["registration_id"]
                    deleted = False
                    for i in xrange(len(tokens)):
                        if tokens[i].gcm_token == newid and i != index:
                            logging.info("Canonical token already exists at index %s, removing this one at index %s" % (i, index))
                            tokens[index].delete();
                            deleted = True
                            break
                        
                    if not deleted:
                        logging.info("Updating token at %s with canonical token: %s -> %s" % (index, tokens[index].gcm_token, newid))
                        token = tokens[index]
                        token.gcm_token = newid
                        token.put()
            else:
                if isset("error", result):
                    logging.warn("Error sending GCM message: " + result["error"])
                    if (result["error"] == "Unavailable"):
                        logging.warn("Token unavailable, retrying")
                        gcmhelper.sendGcmToTokenDeferred(tokens[index], message)
                    elif (result["error"] == "NotRegistered"):
                        logging.warn("Token not registered, deleting")
                        tokens[index].delete()
                    else:
                        logging.warn("Unrecoverable error: " + result["error"])
