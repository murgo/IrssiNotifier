import base64
import logging
from Crypto.Hash import SHA1  # Updated to SHA1 for compatibility
from Crypto.PublicKey import RSA
from Crypto.Signature import pkcs1_15  # Updated to pkcs1_15 for compatibility
import dao
import secret_manager

#untested

class Licensing(object):
    public_key = None
    public_key_base64 = None

    def __init__(self):
        if Licensing.public_key_base64 is None:
            Licensing.public_key_base64 = secret_manager.get_secret("licensing_public_key")
            if Licensing.public_key_base64 is None:
                raise Exception("No key for licensing!")

            # Key from Google Play is a X.509 subjectPublicKeyInfo DER SEQUENCE.
            Licensing.public_key = RSA.import_key(base64.standard_b64decode(Licensing.public_key_base64))

    def check_license(self, irssi_user, signed_data, signature):
        signed_data = signed_data.replace('%3D', '=').replace('%26', '&').replace('%2F', '/').replace('%2B', '+')
        signature = signature.replace('%3D', '=').replace('%26', '&').replace('%2F', '/').replace('%2B', '+')

        try:
            split = signed_data.split(':')
            main_data = split[0]
            extra_data = ''
            if len(split) > 1:
                extra_data = split[1]

            fields = main_data.split('|')
            response_code = int(fields[0])
            nonce = int(fields[1])
            package_name = fields[2]
            version_code = fields[3]
            user_id = fields[4]
            timestamp = int(fields[5])
        except Exception as e:
            logging.error("Malformed data in licensing: %s" % signed_data)
            return False

        if response_code != 0 and response_code != 2:
            logging.error("Invalid response code: %s" % response_code)
            return False

        old_nonce = dao.get_nonce(irssi_user, nonce)
        if old_nonce is None:
            logging.error("Nonces do not match! given: %s" % nonce)
            return False

        h = SHA1.new()
        h.update(signed_data.encode('utf-8'))  # Ensure data is encoded to bytes
        # Scheme is RSASSA-PKCS1-v1_5.
        verifier = pkcs1_15.new(Licensing.public_key)
        # The signature is base64 encoded.
        signature = base64.standard_b64decode(signature)
        try:
            verifier.verify(h, signature)
            verified = True
        except (ValueError, TypeError):
            verified = False

        logging.info("Verified: %s " % verified)

        if verified:
            dao.save_license(irssi_user, response_code, nonce, package_name, version_code, user_id, timestamp, extra_data)

        return verified
