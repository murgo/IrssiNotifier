import uuid
from google.appengine.api import users
import logging
from datamodels import IrssiUser

class Login():
    def generateApiToken(self):
        return str(uuid.uuid4())
    
    def getIrssiUser(self, params):
        logging.info("Login.getIrssiUser()")
        user = users.get_current_user()
        if not user:
            logging.debug("No Google user found")
            if 'apiToken' not in params:
                logging.debug("No token found, failing")
                return None
            token = params['apiToken']
            logging.debug("apiToken %s" % token)
            irssi_users = IrssiUser.all()
            irssi_users.filter('api_token = ', token)
            return irssi_users.get()
        
        logging.debug("Google user found")
        #logging.debug("%s %s %s %s %s" % (user.nickname(), user.user_id(), user.email(), user.federated_provider(), user.federated_identity()))
        
        feder = "%s%s" % (user.federated_provider(), user.federated_identity())
        user_id = user.user_id() if user.user_id() is not None else feder
    
        irssi_user = IrssiUser.get_by_key_name(user_id)
        if irssi_user is None:
            logging.debug("IrssiUser not found, adding new one")
            irssi_user = IrssiUser(key_name=user_id)
            irssi_user.user_id = user_id
            irssi_user.user_name = user.nickname()
            irssi_user.email = user.email()
            irssi_user.api_token = self.generateApiToken() 
            irssi_user.put()
        else:
            logging.debug("IrssiUser found")
        
        return irssi_user