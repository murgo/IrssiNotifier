import time
import uuid
from google.cloud import ndb
import logging
from datamodels import *
import random

OldMessageRemovalThreshold = 7 * 24 * 60 * 60

# Initialize NDB client
client = ndb.Client()

# fcm token stuff

def get_fcm_token_for_key(token_key):
    with client.context():
        return token_key.get()

def get_fcm_token_for_id(irssi_user, token_key):
    with client.context():
        query = GcmToken.query(GcmToken.gcm_token == token_key, ancestor=irssi_user.key)
        return query.get()

def get_fcm_tokens_for_user(user):
    return get_fcm_tokens_for_user_key(user.key, True)

def get_fcm_tokens_for_user_key(irssi_user_key, include_disabled=False):
    with client.context():
        query = GcmToken.query(ancestor=irssi_user_key)
        if not include_disabled:
            query = query.filter(GcmToken.enabled == True)  # must be ==
        tokensList = ndb.get_multi(query.fetch(keys_only=True))
        return tokensList

def remove_fcm_token(token):
    with client.context():
        token.key.delete()

def update_fcm_token(token, new_token_id):
    with client.context():
        token.gcm_token = new_token_id
        token.put()

# irssi user stuff

def get_irssi_user_for_api_token(token):
    #api_token_key = "api-token" + str(token)
    #user = get_memcache_value(api_token_key)
    #if user is not None:
    #    return user

    with client.context():
        query = IrssiUser.query(IrssiUser.api_token == token)
        user = query.get()

    #set_memcache_value(api_token_key, user)
    return user

# email parameter is a fallback since the user ids (PKs) changed when migrating from Users to OAuth2
def get_irssi_user_for_key_name(key_name, email):
    with client.context():
        #user = IrssiUser.get_by_id(key_name)
        user = IrssiUser.query(IrssiUser.user_id_oauth2 == key_name).get()
        if user is None:
            user = IrssiUser.query(IrssiUser.email == email).get()
            if user is not None:
                # update new id into user row
                user.user_id_oauth2 = key_name
                logging.info(f'Migrating {user.email} to have key {user.user_id_oauth2}')
                user.put()
        return user

def generate_api_token():
    return str(uuid.uuid4())

def add_irssi_user(email, user_id=None):
    with client.context():
        irssi_user = IrssiUser(id=user_id)
        irssi_user.user_id_oauth2 = user_id
        irssi_user.user_name = "Anonymous"
        irssi_user.email = email
        irssi_user.api_token = generate_api_token()
        irssi_user.registration_date = int(time.time())
        irssi_user.put()

    #api_token_key = "api-token" + str(irssi_user.api_token)
    #set_memcache_value(api_token_key, irssi_user)
    
    return irssi_user

def update_irssi_user_from_message(irssi_user, version):
    logging.debug("updating irssi user")
    modified = False
    if irssi_user.license_timestamp is not None:
        modified = True
        irssi_user.last_notification_time = int(time.time())
        if irssi_user.notification_count_since_licensed is None:
            irssi_user.notification_count_since_licensed = 1
        else:
            irssi_user.notification_count_since_licensed += 1

    if irssi_user.last_notification_time is None:
        modified = True
        irssi_user.last_notification_time = int(time.time())

    if irssi_user.irssi_script_version != version:
        modified = True
        irssi_user.irssi_script_version = version

    if modified:
        with client.context():
            irssi_user.put()
        #api_token_key = "api-token" + str(irssi_user.api_token)
        #set_memcache_value(api_token_key, irssi_user)

    return irssi_user

# message stuff

def get_messages(user, timestamp):
    logging.debug("Getting messages after: %s" % timestamp)
    with client.context():
        query = Message.query(Message.server_timestamp > int(timestamp), ancestor=user.key).order(Message.server_timestamp)
        m = query.fetch(50)
    logging.debug("Found %s messages" % len(m))
    return m

def add_message(irssi_user, message=None, channel=None, nick=None):
    with client.context():
        msg = Message(parent=irssi_user.key)
        msg.message = message
        msg.channel = channel
        msg.nick = nick
        msg.server_timestamp = int(time.time())
        if irssi_user.license_timestamp is not None:
            logging.debug("Licensed user, saving message")
            msg.put()
        else:
            logging.debug("Free user, not saving message")
    return msg

def clear_old_messages():
    MaxAmount = 500
    amount = MaxAmount

    logging.info("Clearing old messages")
    with client.context():
        while amount == MaxAmount:
            query = Message.query(Message.server_timestamp < (int(time.time()) - OldMessageRemovalThreshold))
            keys = query.fetch(MaxAmount, keys_only=True)
            ndb.delete_multi(keys)
            amount = len(keys)
            logging.info("Deleted %s messages" % amount)

# settings stuff

def save_settings(user, token_id, enabled, name):
    token = get_fcm_token_for_id(user, token_id)

    if token is not None:
        logging.debug("Updating token: " + token_id)
        token.enabled = enabled
        token.name = name
        with client.context():
            token.put()
        return token

    logging.debug("Adding new token: " + token_id)
    with client.context():
        tokenToAdd = GcmToken(parent=user.key)
        tokenToAdd.gcm_token = token_id
        tokenToAdd.enabled = enabled
        tokenToAdd.name = name
        tokenToAdd.registration_date = int(time.time())
        tokenToAdd.put()
    return tokenToAdd

def wipe_user(user):
    logging.info("Wiping everything for user %s" % user.email)

    key = user.key
    MaxAmount = 500

    #api_token_key = "api-token" + str(user.api_token)
    #set_memcache_value(api_token_key, None)

    amount = MaxAmount
    logging.info("Wiping messages")
    with client.context():
        while amount == MaxAmount:
            query = Message.query(ancestor=key)
            keys = query.fetch(MaxAmount, keys_only=True)
            ndb.delete_multi(keys)
            amount = len(keys)
            logging.info("Deleted %s messages" % amount)

    amount = MaxAmount
    logging.info("Wiping FCM tokens")
    with client.context():
        while amount == MaxAmount:
            query = GcmToken.query(ancestor=key)
            keys = query.fetch(MaxAmount, keys_only=True)
            ndb.delete_multi(keys)
            amount = len(keys)
            logging.info("Deleted %s tokens" % amount)

    logging.info("Wiping user")
    with client.context():
        user.key.delete()

def get_new_nonce(user):
    with client.context():
        query = Nonce.query(ancestor=user.key).order(-Nonce.issue_timestamp)
        nonce = query.get()

    nonce_expiration_time = 20 * 60

    if nonce is not None and nonce.issue_timestamp + nonce_expiration_time > time.time():
        logging.debug("Returning old nonce, issue_timestamp: %s" % nonce.issue_timestamp)
        return nonce

    rand = random.randint(-2147483648, 2147483647)
    if nonce is None:
        logging.debug("Old nonce doesn't exist, generating new one: %s." % rand)
    else:
        logging.debug("Old nonce is too old, generating new one: %s. Old: %s, now: %s" % (rand, nonce, int(time.time())))

    with client.context():
        nonce = Nonce(parent=user.key)
        nonce.issue_timestamp = int(time.time())
        nonce.nonce = rand
        nonce.put()

    return nonce

def get_nonce(user, nonce):
    with client.context():
        query = Nonce.query(Nonce.nonce == nonce, ancestor=user.key)
        return query.get()

def save_license(irssi_user, response_code, nonce, package_name, version_code, user_id, timestamp, extra_data):
    logging.info("User %s licensed!" % irssi_user.email)

    current_time = int(time.time())

    if irssi_user.license_timestamp is None:
        logging.info("User %s first time licensing, cool. Current time: %s" % (irssi_user.email, current_time))
        irssi_user.license_timestamp = current_time
        with client.context():
            irssi_user.put()

    #api_token_key = "api-token" + str(irssi_user.api_token)
    #set_memcache_value(api_token_key, irssi_user)

    with client.context():
        l = License(parent=irssi_user.key)
        l.response_code = response_code
        l.nonce = nonce
        l.package_name = package_name
        l.version_code = version_code
        l.user_id = user_id
        l.timestamp = timestamp
        l.extra_data = extra_data
        l.receive_timestamp = current_time
        l.put()