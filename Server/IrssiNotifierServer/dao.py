import time
import uuid
from datamodels import *
from google.appengine.ext import db

OldMessageRemovalThreshold = 604800


# gcm token stuff

def get_gcm_token_for_key(token_key):
    return GcmToken.get(token_key)


def get_gcm_tokens_for_user(user):
    return get_gcm_tokens_for_user_key(user.key(), True)


def get_gcm_tokens_for_user_key(irssi_user_key, include_disabled=False):
    tokens = GcmToken.all()
    tokens.ancestor(irssi_user_key)
    if not include_disabled:
        tokens.filter("enabled =", True)
    tokensList = tokens.fetch(10)
    return tokensList


def remove_gcm_token(token):
    token.delete()


def update_gcm_token(token, new_token_id):
    token.gcm_token = new_token_id
    token.put()


# irssi user stuff

def get_irssi_user_for_api_token(token):
    irssi_users = IrssiUser.all()
    irssi_users.filter('api_token = ', token)
    return irssi_users.get()


def get_irssi_user_for_key_name(key_name):
    return IrssiUser.get_by_key_name(key_name)


def generate_api_token():
    return str(uuid.uuid4())


def add_irssi_user(user, user_id=None):
    irssi_user = IrssiUser(key_name=user_id)
    irssi_user.user_id = user_id
    irssi_user.user_name = user.nickname()
    irssi_user.email = user.email()
    irssi_user.api_token = generate_api_token()
    irssi_user.registration_date = int(time.time())
    irssi_user.notification_count = 0
    irssi_user.put()
    return irssi_user


def update_irssi_user(irssi_user, version):
    if irssi_user.notification_count is None:
        irssi_user.notification_count = 1
    else:
        irssi_user.notification_count += 1
    irssi_user.last_notification_time = int(time.time())
    irssi_user.irssi_script_version = version
    irssi_user.put()


# gcm auth key stuff

def load_gcm_auth_key():
    key = AuthKey.get_by_key_name("GCM_AUTHKEY")
    if key is None:
        return None
    return key.gcm_authkey


def add_gcm_auth_key():
    key = AuthKey(key_name="GCM_AUTHKEY")
    with open("secret.txt") as f:
        authkey = f.readlines()
    logging.info(authkey)
    key.gcm_authkey = authkey[0].split('\n')[0]
    key.put()
    return key


# message stuff

def get_messages(user, timestamp):
    logging.debug("Getting messages after: %s" % timestamp)
    messages = Message.all()
    messages.ancestor(user)
    messages.filter("server_timestamp >", int(timestamp))
    messages.order("server_timestamp")

    m = messages.fetch(50)
    logging.debug("Found %s messages" % len(m))
    return m


def add_message(irssi_user, message=None, channel=None, nick=None):
    msg = Message(parent=irssi_user.key())
    msg.message = message
    msg.channel = channel
    msg.nick = nick
    msg.server_timestamp = int(time.time())
    msg.put()
    return msg


def clear_old_messages():
    messages = Message.all()
    messages.filter("server_timestamp <", int(time.time()) - OldMessageRemovalThreshold)
    db.delete(messages)

    logging.info("Deleted %s rows" % (firstCount - lastCount))


# settings stuff

def save_settings(user, token_id, enabled, name):
    tokens = GcmToken.all()
    tokens.ancestor(user.key())
    tokens.filter("gcm_token =", token_id)
    token = tokens.get()

    if token is not None:
        logging.debug("Updating token: " + token_id)
        token.enabled = enabled
        token.name = name
        token.put()
        return token

    logging.debug("Adding new token: " + token_id)
    tokenToAdd = GcmToken(parent=user.key())
    tokenToAdd.gcm_token = token_id
    tokenToAdd.enabled = enabled
    tokenToAdd.name = name
    tokenToAdd.registration_date = int(time.time())
    tokenToAdd.put()
    return tokenToAdd


def wipe_user(user):
    logging.info("Wiping everything for user %s" % user.user_id)

    query = GcmToken.all()
    query.ancestor(user)
    db.delete(query)

    query = Message.all()
    query.ancestor(user)
    db.delete(query)

    user.delete()

