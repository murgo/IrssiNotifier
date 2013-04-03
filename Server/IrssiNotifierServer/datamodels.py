import json
import logging
from google.appengine.ext import ndb


class AuthKey(ndb.Model):
    gcm_authkey = ndb.StringProperty()


class IrssiUser(ndb.Model):
    user_name = ndb.StringProperty(indexed=False)
    email = ndb.StringProperty()
    user_id = ndb.StringProperty()
    api_token = ndb.StringProperty()
    registration_date = ndb.IntegerProperty(indexed=False)
    notification_count = ndb.IntegerProperty(indexed=False)
    last_notification_time = ndb.IntegerProperty(indexed=False)
    irssi_script_version = ndb.IntegerProperty(indexed=False)


class GcmToken(ndb.Model):
    gcm_token = ndb.StringProperty()
    enabled = ndb.BooleanProperty()
    name = ndb.StringProperty()
    registration_date = ndb.IntegerProperty()


class Message(ndb.Model):
    server_timestamp = ndb.IntegerProperty(indexed=True)
    message = ndb.TextProperty()
    channel = ndb.TextProperty()
    nick = ndb.TextProperty()

    def to_json(self):
        return json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': self.message,
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key.integer_id()})

    def to_gcm_json(self):
        m = json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': self.message,
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key.integer_id()})
        if len(m) < 3072:
            return m

        logging.warn("too big message %s, shortening" % len(m))
        return json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': 'toolong',
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key.integer_id()})
