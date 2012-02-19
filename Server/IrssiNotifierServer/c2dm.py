import urllib2
import logging
from datamodels import C2dmToken, AuthKey

class C2DM(object):
    def loadAuthKey(self):
        logging.debug("Loading auth key from datastore")
        key = AuthKey.get_by_key_name("AUTHKEY")
        if key is None:
            return None
        
        return key.auth 
    
    def saveAuthKey(self, lines):
        logging.debug("Saving auth key to datastore")
        key = AuthKey(key_name = "AUTHKEY")
        key.sid = lines[0][4:-1]
        key.lsid = lines[1][5:-1]
        key.auth = lines[2][5:-1]
        key.put()
    
    def getAuthKey(self):
        logging.info("Generating c2dm auth key")
        
        f = open("secret.txt", 'r')
        line = f.readline()
        f.close()
        
        split = line.split(' ')
        email = split[0]
        passwd = split[1]
        
        request = urllib2.Request("https://www.google.com/accounts/ClientLogin")
        request.add_data('Email=%s&Passwd=%s&service=ac2dm' % (email, passwd))
        
        # TODO: errors from the next line, possible email alarms
        response = urllib2.urlopen(request)
        lines = response.readlines()
        logging.debug("auth key response: %s" % lines)
        #sid = lines[0][4:-1]
        #lsid = lines[1][5:-1]
        auth = lines[2][5:-1]
        self.saveAuthKey(lines)
        return auth
    
    def sendC2dmToUser(self, irssiuser, message):
        tokens = C2dmToken.all()
        tokens.ancestor(irssiuser.key())
        tokensList = tokens.fetch(10)
        for token in tokensList:
            if token.enabled:
                self.sendC2dm(token.c2dm_token, message)
        
    def sendC2dm(self, token, message):
        logging.debug("Sending c2dm message")
        authkey = self.loadAuthKey()
        if authkey is None:
            authkey = self.getAuthKey()
            
        logging.debug("Auth key: %s" % authkey)

        request = urllib2.Request("https://android.apis.google.com/c2dm/send")
        request.add_header('Authorization', 'GoogleLogin auth=%s' % authkey)
        request.add_data('registration_id=%s&data.message=%s&collapse_key=%s' % (token, message, "ck"))
        
        # TODO: errors from the next line, possible email alarms
        response = urllib2.urlopen(request)
        text = response.read()
        if "id=" in text: return True
        
        # TODO "Error="
        logging.warning(text)
