from datamodels import GcmToken
import logging

class SettingsHandler(object):
    def handle(self, user, array):
        logging.info("SettingsHandler.handle()")
        newToken = array["RegistrationId"]

        tokens = GcmToken.all()
        tokens.ancestor(user.key())
        tokens.filter("gcm_token =", newToken)
        t = tokens.get()
        
        if not t:
            logging.debug("Adding new token: " + newToken)
            tokenToAdd = GcmToken(parent = user.key())
            tokenToAdd.gcm_token = newToken
            tokenToAdd.enabled = bool(int(array["Enabled"]))
            tokenToAdd.name = array["Name"]
            tokenToAdd.put()
        else:
            logging.debug("Updating token: " + newToken)
            t.enabled = bool(int(array["Enabled"]))
            t.name = array["Name"]
            t.put()
