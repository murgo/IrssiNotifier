import json
import logging
from google.appengine.ext import db


class IrssiUser(db.Model):
    user_name = db.StringProperty(indexed=False)
    email = db.StringProperty()
    user_id = db.StringProperty()
    api_token = db.StringProperty()
    registration_date = db.IntegerProperty(indexed=False)
    notification_count = db.IntegerProperty(indexed=False)
    last_notification_time = db.IntegerProperty(indexed=False)
    irssi_script_version = db.IntegerProperty(indexed=False)


class GcmToken(db.Model):
    gcm_token = db.StringProperty()
    enabled = db.BooleanProperty()
    name = db.StringProperty()
    registration_date = db.IntegerProperty()


class Message(db.Model):
    server_timestamp = db.IntegerProperty(indexed=True)
    message = db.TextProperty()
    channel = db.TextProperty()
    nick = db.TextProperty()

    def to_json(self):
        return json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': self.message,
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key().id()})

    def to_gcm_json(self):
        m = json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': self.message,
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key().id()})
        if len(m) < 3072:
            return m

        logging.warn("too big message %s, shortening" % len(m))
        return json.dumps(
            {'server_timestamp': '%f' % self.server_timestamp,
             'message': 'toolong',
             'channel': self.channel,
             'nick': self.nick,
             'id': self.key().id()})


class AuthKey(db.Model):
    gcm_authkey = db.StringProperty()
