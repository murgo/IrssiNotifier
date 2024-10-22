import logging
import os
#from google.cloud import memcache_v1beta2 as memcache
from google.cloud import secretmanager

# Initialize Secret Manager client
secret_client = secretmanager.SecretManagerServiceClient()

# Initialize Memcache client
#memcache_client = memcache.CloudMemcacheClient()

# Use environment variables for project ID and location
PROJECT_ID = os.getenv('PROJECT_ID')
LOCATION = os.getenv('LOCATION', 'default_location')

#def get_memcache_value(key):
#    try:
#        response = memcache_client.get_instance(name=f"projects/{PROJECT_ID}/locations/{LOCATION}/instances/YOUR_INSTANCE_ID")
#        return response.get(key)
#    except Exception as e:
#        logging.error("Unable to get memcache value for key %s: %s" % (key, str(e)))
#        return None

#def set_memcache_value(key, value):
#    try:
#        response = memcache_client.get_instance(name=f"projects/{PROJECT_ID}/locations/{LOCATION}/instances/YOUR_INSTANCE_ID")
#        response.set(key, value)
#    except Exception as e:
#        logging.error("Unable to set memcache value for key %s: %s" % (key, str(e)))

def get_secret(secret_name):
    try:
        name = f"projects/{PROJECT_ID}/secrets/{secret_name}/versions/latest"
        response = secret_client.access_secret_version(name=name)
        return response.payload.data.decode('UTF-8')
    except Exception as e:
        logging.error("Unable to read secret %s: %s" % (secret_name, str(e)))
    return None
