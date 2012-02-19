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
    # TODO some device info perhaps
    # TODO maybe it's better to use more generic names than c2dm, and extract c2dm sender into it's own controller
    # TODO also token types are nice, how to set default stuff to Google


class Message(db.Model):
    server_timestamp = db.FloatProperty()
    timestamp = db.StringProperty()
    message = db.StringProperty()
    channel = db.StringProperty()
    nick = db.StringProperty()
    def ToJson(self):
        return json.dumps({'server_timestamp': self.server_timestamp, 'timestamp': self.timestamp, 'message': self.message, 'channel': self.channel, 'nick': self.nick})


class AuthKey(db.Model):
    sid = db.StringProperty()
    lsid = db.StringProperty()
    auth = db.StringProperty()