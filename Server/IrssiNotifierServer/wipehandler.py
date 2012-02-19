from datamodels import C2dmToken
import logging

class WipeHandler(object):
    def handle(self, user):
        logging.info("Wiping everything for user %s" % user.user_id)
        query = Entry().all()
        query.ancestor(user)
        db.delete(query)
