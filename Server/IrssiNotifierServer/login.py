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
        irssi_user = dao.get_irssi_user_for_api_token(token)
        if irssi_user is not None:
            logging.debug('authorized as %s (%s)' % (irssi_user.user_name, irssi_user.email))
            return irssi_user
        logging.warning('No user found with the api token!')
        return None

    logging.debug("Google user found")

    user_id = user.user_id()
    if user_id is None:
        federated_identity = "%s%s" % (user.federated_provider(), user.federated_identity())
        logging.warn("Strange, using federated identity %s" % federated_identity)
        user_id = federated_identity

    irssi_user = dao.get_irssi_user_for_key_name(user_id)
    if irssi_user is None:
        logging.debug("IrssiUser not found, adding new one")
        irssi_user = dao.add_irssi_user(user, user_id)

    logging.debug('authorized as %s (%s)' % (irssi_user.user_name, irssi_user.email))
    return irssi_user