import time
import traceback
import uuid
from Crypto.Random import random
from google.appengine.api import memcache

from datamodels import *
from google.appengine.ext import ndb
import yaml

OldMessageRemovalThreshold = 7 * 24 * 60 * 60


# gcm token stuff

def get_gcm_token_for_key(token_key):
    return token_key.get()


def get_gcm_token_for_id(token_key):
    query = GcmToken.query(GcmToken.gcm_token == token_key)
    return query.get()


def get_gcm_tokens_for_user(user):
    return get_gcm_tokens_for_user_key(user.key, True)


def get_gcm_tokens_for_user_key(irssi_user_key, include_disabled=False):
    query = GcmToken.query(ancestor=irssi_user_key)
    if not include_disabled:
        query = query.filter(GcmToken.enabled == True)  # must be ==
    tokensList = ndb.get_multi(query.fetch(keys_only=True))
    return tokensList


def remove_gcm_token(token):
    token.key.delete()


def update_gcm_token(token, new_token_id):
    token.gcm_token = new_token_id
    token.put()


# irssi user stuff

def get_irssi_user_for_api_token(token):
    api_token_key = "api-token" + str(token)
    user = memcache.get(api_token_key)
    if user is not None:
        return user

    query = IrssiUser.query(IrssiUser.api_token == token)
    user = query.get()

    memcache.set(api_token_key, user)
    return user


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

    api_token_key = "api-token" + str(irssi_user.api_token)
    memcache.set(api_token_key, irssi_user)

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

    api_token_key = "api-token" + str(irssi_user.api_token)
    memcache.set(api_token_key, irssi_user)

    return irssi_user


# gcm auth key stuff

def load_gcm_auth_key():
    key = Secret.get_by_id("GCM_AUTHKEY")
    if key is None:
        key = add_gcm_auth_key()
        if key is None:
            return None
    return key.secret


def add_gcm_auth_key():
    authkey = get_secret('google_api_key')
    logging.info("GCM Auth Key: %s" % authkey)

    key = Secret(id="GCM_AUTHKEY")
    key.secret = authkey
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
    MaxAmount = 500
    amount = MaxAmount

    logging.info("Clearing old messages")
    while amount == MaxAmount:
        query = Message.query(Message.server_timestamp < (int(time.time()) - OldMessageRemovalThreshold))
        keys = query.fetch(MaxAmount, keys_only=True)
        ndb.delete_multi(keys)
        amount = len(keys)
        logging.info("Deleted %s messages" % amount)


# settings stuff

def save_settings(user, token_id, enabled, name):
    token = get_gcm_token_for_id(token_id)

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

    key = user.key
    MaxAmount = 500

    amount = MaxAmount
    logging.info("Wiping messages")
    while amount == MaxAmount:
        query = Message.query(ancestor=key)
        keys = query.fetch(MaxAmount, keys_only=True)
        ndb.delete_multi(keys)
        amount = len(keys)
        logging.info("Deleted %s messages" % amount)

    amount = MaxAmount
    logging.info("Wiping GCM tokens")
    while amount == MaxAmount:
        query = GcmToken.query(ancestor=key)
        keys = query.fetch(MaxAmount, keys_only=True)
        ndb.delete_multi(keys)
        amount = len(keys)
        logging.info("Deleted %s tokens" % amount)

    logging.info("Wiping user")
    user.key.delete()


def get_new_nonce(user):
    query = Nonce.query(ancestor=user.key).order(-Nonce.issue_timestamp)
    nonces = query.fetch(1)
    logging.debug("Found %s nonces" % len(nonces))

    nonce_expiration_time = 20

    nonce = None
    if len(nonces) != 0:
        nonce = nonces[0]
        if nonce.issue_timestamp + nonce_expiration_time > time.time():
            logging.debug("Returning old nonce, issue_timestamp: %s" % nonce.issue_timestamp)
            return nonce

    rand = random.randint(-2147483648, 2147483647)
    if nonce is None:
        logging.debug("Old nonce doesn't exist, generating new one: %s." % rand)
    else:
        logging.debug("Old nonce is too old, generating new one: %s. Old: %s, now: %s" % (rand, nonce, int(time.time())))

    nonce = Nonce(parent=user.key)
    nonce.issue_timestamp = int(time.time())
    nonce.nonce = rand
    nonce.put()

    return nonce


def get_nonce(user, nonce):
    query = Nonce.query(Nonce.nonce == nonce, ancestor=user.key).order(-Nonce.issue_timestamp)
    nonces = query.fetch(1)
    if len(nonces) > 0:
        return nonces[0]
    return None


def load_licensing_public_key():
    key = Secret.get_by_id("LICENSING_PUBLIC_KEY")
    if key is None:
        key = add_licensing_public_key()
        if key is None:
            return None
    return key.secret


def add_licensing_public_key():
    authkey = get_secret('licensing_public_key')
    logging.info("Licensing public key: %s" % authkey)

    key = Secret(id="LICENSING_PUBLIC_KEY")
    key.secret = authkey
    key.put()
    return key


def get_secret(secret_name):
    try:
        with open("secrets.yaml") as f:
            secrets = yaml.load(f)
            return secrets[secret_name]
    except:
        logging.error("Unable to read secrets.yaml %s" % traceback.format_exc())
        return None


def save_license(user, response_code, nonce, package_name, version_code, user_id, timestamp, extra_data):
    logging.info("User %s licensed!" % user.email)

    current_time = int(time.time())
    user.license_timestamp = current_time
    user.put()

    l = License(parent=user.key)
    l.response_code = response_code
    l.nonce = nonce
    l.package_name = package_name
    l.version_code = version_code
    l.user_id = user_id
    l.timestamp = timestamp
    l.extra_data = extra_data
    l.receive_timestamp = current_time
    l.put()
