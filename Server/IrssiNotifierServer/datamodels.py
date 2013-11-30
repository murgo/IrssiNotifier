import json
import logging
from google.appengine.ext import ndb


class Secret(ndb.Model):
    secret = ndb.StringProperty()


class IrssiUser(ndb.Model):
    user_name = ndb.StringProperty(indexed=False)
    email = ndb.StringProperty(indexed=True)
    user_id = ndb.StringProperty(indexed=True)
    api_token = ndb.StringProperty(indexed=True)
    registration_date = ndb.IntegerProperty(indexed=False)
    notification_count_since_licensed = ndb.IntegerProperty(indexed=False)
    last_notification_time = ndb.IntegerProperty(indexed=False)
    irssi_script_version = ndb.IntegerProperty(indexed=False)
    license_timestamp = ndb.IntegerProperty(indexed=False)


class GcmToken(ndb.Model):
    gcm_token = ndb.StringProperty(indexed=True)
    enabled = ndb.BooleanProperty(indexed=True)
    name = ndb.StringProperty(indexed=False)
    registration_date = ndb.IntegerProperty(indexed=False)


class Message(ndb.Model):
    _use_memcache = False
    _use_cache = False

    server_timestamp = ndb.IntegerProperty(indexed=True)
    message = ndb.TextProperty(indexed=False)
    channel = ndb.TextProperty(indexed=False)
    nick = ndb.TextProperty(indexed=False)

    def to_json(self):
        return json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': self.message,
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key.integer_id()})

    def to_gcm_json(self):
        values = {'server_timestamp': '%f' % self.server_timestamp,
                  'message': self.message,
                  'channel': self.channel,
                  'nick': self.nick,
                  'id': self.key.integer_id()}
        #if self.key.integer_id() is not None:
        #    values['id'] = self.key.integer_id() #this breaks free apps prior to version 13
        m = json.dumps(values)
        if len(m) < 3072:
            return m

        logging.warn("too big message %s, shortening" % len(m))
        values['message'] = 'toolong'
        return json.dumps(values)


class Nonce(ndb.Model):
    nonce = ndb.IntegerProperty()
    issue_timestamp = ndb.IntegerProperty()


class License(ndb.Model):
    response_code = ndb.IntegerProperty(indexed=False)
    nonce = ndb.IntegerProperty(indexed=False)
    package_name = ndb.TextProperty(indexed=False)
    version_code = ndb.TextProperty(indexed=False)
    user_id = ndb.TextProperty(indexed=False)
    timestamp = ndb.IntegerProperty(indexed=False)
    extra_data = ndb.TextProperty(indexed=False)
    receive_timestamp = ndb.IntegerProperty()
