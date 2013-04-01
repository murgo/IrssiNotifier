import urllib
import urllib2
import base64
from hashlib import md5

from Crypto.Cipher import AES
from Crypto import Random

_VERSION = 13


class IrssiNotifier:

    """
    A Python Interface for the IrssiNotifier for Android.

    >>> notifier = IrssiNotifier(API_KEY, PASSWORD)
    >>> notifier.send_message("message", "#channel", "nick")
    """

    def __init__(self, api_token, enc_password):
        """
        Initialize the IrssiNotifier.

        @param api_token: the API token you got from the site.
        @param enc_password: the encryption password you set on your
                             device.
        @type api_token: C{str}
        @type enc_password: C{str}
        """
        self._api_base = 'https://irssinotifier.appspot.com/API'
        self._api_token = api_token
        self._enc_password = enc_password

    def _send_request(self, endpoint, data):
        data = urllib.urlencode(data)
        url = '%s/%s' % (self._api_base, endpoint)
        urllib2.urlopen(url, data)

    def _encrypt_text(self, text, password):
        BS = 16
        pad = lambda s: s + (BS - len(s) % BS) * chr(BS - len(s) % BS)

        def EVP_ByteToKey(password, salt, key_len, iv_len):
            """
            Derive the key and the IV from the given password and salt.
            From http://stackoverflow.com/questions/13907841
            """
            dtot = md5(password + salt).digest()
            d = [dtot]
            while len(dtot) < (iv_len+key_len):
                d.append(md5(d[-1] + password + salt).digest())
                dtot += d[-1]
            return dtot[:key_len], dtot[key_len:key_len+iv_len]

        """
        The space is needed for OpenSSL compatibility.
        The last char of the string gets removed in
          irssinotifier/Crypto.java
        So let this last char be a space and not valuable information :)
        """
        TEXT = pad(text+" ")

        SALT = Random.get_random_bytes(8)
        KEY, IV = EVP_ByteToKey(password, SALT, 16, 16)

        c = AES.new(KEY, AES.MODE_CBC, IV)
        v = c.encrypt(TEXT)

        s = "Salted__"+SALT+v
        return base64.urlsafe_b64encode(s)

    def send_message(self, message, channel, nick):
        """
        Send notification about a message from nick in channel.

        @param message: the message the hilight is about.
        @param channel: the channel in which the hilight happened.
        @param nick: the sender of the message.

        @type message: C{str}
        @type channel: C{str}
        @type nick: C{str}
        """
        message = self._encrypt_text(message, self._enc_password)
        channel = self._encrypt_text(channel, self._enc_password)
        nick = self._encrypt_text(nick, self._enc_password)
        data = {'apiToken': self._api_token, 'version': _VERSION,
                'message': message, 'channel': channel, 'nick': nick}
        self._send_request('Message', data)

__all__ = ['IrssiNotifier']
