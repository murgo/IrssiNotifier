import time
import uuid
from datamodels import *

OldMessageRemovalThreshold = 604800


# gcm token stuff

def get_gcm_token_for_key(token_key):
    return token_key.get()


def get_gcm_tokens_for_user(user):
    return get_gcm_tokens_for_user_key(user.key, True)


def get_gcm_tokens_for_user_key(irssi_user_key, include_disabled=False):
    query = GcmToken.query(ancestor=irssi_user_key)
    if not include_disabled:
        query = query.filter(GcmToken.enabled == True)  # must be ==
    tokensList = query.fetch(10)
    return tokensList


def remove_gcm_token(token):
    token.key.delete()


def update_gcm_token(token, new_token_id):
    token.gcm_token = new_token_id
    token.put()


# irssi user stuff

def get_irssi_user_for_api_token(token):
    query = IrssiUser.query(IrssiUser.api_token == token)
    return query.get()


def get_irssi_user_for_key_name(key_name):
    return IrssiUser.get_by_id(key_name)


def generate_api_token():
    return str(uuid.uuid4())


def add_irssi_user(user, user_id=None):
    irssi_user = IrssiUser(id=user_id)
    irssi_user.user_id = user_id
    irssi_user.user_name = user.nickname()
    irssi_user.email = user.email()
    irssi_user.api_token = generate_api_token()
    irssi_user.registration_date = int(time.time())
    irssi_user.notification_count = 0
    irssi_user.put()
    return irssi_user


def update_irssi_user(irssi_user, version):
    logging.debug("updating irssi user")
    if irssi_user.notification_count is None:
        irssi_user.notification_count = 1
    else:
        irssi_user.notification_count += 1
    irssi_user.last_notification_time = int(time.time())
    irssi_user.irssi_script_version = version
    irssi_user.put()


# gcm auth key stuff

def load_gcm_auth_key():
    key = AuthKey.get_by_id("GCM_AUTHKEY")
    if key is None:
        return None
    return key.gcm_authkey


def add_gcm_auth_key():
    key = AuthKey(id="GCM_AUTHKEY")
    with open("secret.txt") as f:
        authkey = f.readlines()
    logging.info(authkey)
    key.gcm_authkey = authkey[0].split('\n')[0]
    key.put()
    return key


# message stuff

def get_messages(user, timestamp):
    logging.debug("Getting messages after: %s" % timestamp)
    query = Message.query(Message.server_timestamp > int(timestamp), ancestor=user.key).order(Message.server_timestamp)

    m = query.fetch(50)
    logging.debug("Found %s messages" % len(m))
    return m


def add_message(irssi_user, message=None, channel=None, nick=None):
    msg = Message(parent=irssi_user.key)
    msg.message = message
    msg.channel = channel
    msg.nick = nick
    msg.server_timestamp = int(time.time())
    msg.put()
    return msg


def clear_old_messages():
    query = Message.query(Message.server_timestamp < int(time.time()) - OldMessageRemovalThreshold)
    query.delete()


# settings stuff

def save_settings(user, token_id, enabled, name):
    query = GcmToken.query(GcmToken.gcm_token == token_id, ancestor=user.key)
    token = query.get()

    if token is not None:
        logging.debug("Updating token: " + token_id)
        token.enabled = enabled
        token.name = name
        token.put()
        return token

    logging.debug("Adding new token: " + token_id)
    tokenToAdd = GcmToken(parent=user.key)
    tokenToAdd.gcm_token = token_id
    tokenToAdd.enabled = enabled
    tokenToAdd.name = name
    tokenToAdd.registration_date = int(time.time())
    tokenToAdd.put()
    return tokenToAdd


def wipe_user(user):
    logging.info("Wiping everything for user %s" % user.user_id)

    query = GcmToken.query(ancestor=user.key)
    query.delete()

    query = Message.query(ancestor=user.key)
    query.delete()

    user.delete()
