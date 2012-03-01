import json
from google.appengine.ext import db

class IrssiUser(db.Model):
    user_name = db.StringProperty()
    email = db.StringProperty()
    user_id = db.StringProperty()
    api_token = db.StringProperty()


class C2dmToken(db.Model):
    c2dm_token = db.StringProperty()
    enabled = db.BooleanProperty()
    name = db.StringProperty()


class Message(db.Model):
    server_timestamp = db.FloatProperty(indexed=True)
    timestamp = db.StringProperty()
    message = db.TextProperty()
    channel = db.StringProperty()
    nick = db.StringProperty()
    def ToJson(self):
        return json.dumps({'server_timestamp': '%f' % self.server_timestamp, 'timestamp': self.timestamp, 'message': self.message, 'channel': self.channel, 'nick': self.nick, 'id': self.key().id()})
    def ToC2dmJson(self):
        m = json.dumps({'server_timestamp': '%f' % self.server_timestamp, 'timestamp': self.timestamp, 'message': self.message, 'channel': self.channel, 'nick': self.nick, 'id': self.key().id()})
        if len(m) < 1024:
            return m
        return json.dumps({'server_timestamp': '%f' % self.server_timestamp, 'timestamp': self.timestamp, 'message': 'toolong', 'channel': self.channel, 'nick': self.nick, 'id': self.key().id()})

class AuthKey(db.Model):
    sid = db.StringProperty()
    lsid = db.StringProperty()
    auth = db.StringProperty()
