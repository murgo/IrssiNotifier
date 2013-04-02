from google.appengine.api import users
import logging
import dao


def get_irssi_user(params):
    logging.info("Login.getIrssiUser()")
    user = users.get_current_user()
    if not user:
        logging.debug("No Google user found, using API token")
        if 'apiToken' not in params:
            logging.debug("No API token found, failing")
            return None
        token = params['apiToken']
        logging.debug("apiToken %s" % token)
        return dao.get_irssi_user_for_api_token(token)

    logging.debug("Google user found")

    federated_identity = "%s%s" % (user.federated_provider(), user.federated_identity())
    user_id = user.user_id()
    if user_id is None:
        logging.warn("Strange, using federated identity %s" % federated_identity)
        user_id = federated_identity

    irssi_user = dao.get_irssi_user_for_key_name(user_id)
    if irssi_user is None:
        logging.debug("IrssiUser not found, adding new one")
        dao.add_irssi_user(user, user_id)

    return irssi_user