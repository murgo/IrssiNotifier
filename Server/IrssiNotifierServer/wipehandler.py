import logging
from datamodels import C2dmToken, Message, AuthKey
from google.appengine.ext import db

class WipeHandler(object):
    def handle(self, user):
        logging.info("Wiping everything for user %s" % user.user_id)
        
        query = C2dmToken.all()
        query.ancestor(user)
        db.delete(query)

        query = Message.all()
        query.ancestor(user)
        db.delete(query)

        query = AuthKey.all()
        query.ancestor(user)
        db.delete(query)

        user.delete()