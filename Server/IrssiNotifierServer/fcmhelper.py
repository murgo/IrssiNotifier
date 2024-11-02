import traceback
import logging
import json
import os
from google.cloud import tasks_v2
import base64

from fcm import FCM
import login

# Read environment variables
PROJECT_NAME = os.getenv('PROJECT_NAME').lower()
LOCATION = os.getenv('LOCATION')
QUEUE_NAME = os.getenv('QUEUE_NAME')
BASE_URL = os.getenv('BASE_URL')

# Construct the URL dynamically
SERVICE = os.getenv('GAE_SERVICE', 'default')
VERSION = os.getenv('GAE_VERSION', 'v1')
INSTANCE = os.getenv('GAE_INSTANCE', 'instance')

def create_task(payload):
    client = tasks_v2.CloudTasksClient()

    # Convert the payload to JSON and then to base64
    payload = json.dumps(payload)
    converted_payload = base64.b64encode(payload.encode()).decode()

    # Construct the request body.
    task = tasks_v2.Task(
        http_request=tasks_v2.HttpRequest(
            http_method=tasks_v2.HttpMethod.POST,
            url=f'{BASE_URL}/API/DeferredMessage',
            headers={"Content-type": "application/json"},
            body=converted_payload,
        ),
    )

    # THIS TASK IS CREATED AS THE DEFAULT COMPUTE SERVICE ACCOUNT, NOT THE USUAL ACCOUNT?

    # Use the client to build and send the task.
    logging.info(f'Creating deferred message task, queue path: {client.queue_path(PROJECT_NAME, LOCATION, QUEUE_NAME)}...')
    #logging.info(f'creating task {task}')
    return client.create_task(
        tasks_v2.CreateTaskRequest(
            parent=client.queue_path(PROJECT_NAME, LOCATION, QUEUE_NAME),
            task=task,
        )
    )

def send_fcm_to_user_deferred(data):
    logging.info("Queuing deferred task for sending message to user")
    try:
        create_task(data)
    except Exception as e:
        logging.warn("Error creating task: %s" % traceback.format_exc())

def _send_fcm_to_user(request):
    #logging.debug(f'request: {request}')
    logging.debug(f'data: {request.data}')
    #payload = json.loads(base64.b64decode(request.data).decode())
    payload = json.loads(request.data)
    irssi_user = login.get_irssi_user(payload, True)
    fcm_message = payload['fcm_message']
    logging.info("Executing deferred task: _send_fcm_to_user, %s, %s" % (irssi_user.email, fcm_message))
    fcm = FCM()
    fcm.send_fcm_to_user(irssi_user.key, fcm_message)

'''
def send_fcm_to_token_deferred(token, message):
    logging.info("Queuing deferred task for sending message to token %s" % token.gcm_token)
    key = token.key
    try:
        payload = {
            'function': '_send_fcm_to_token',
            'key': key,
            'message': message
        }
        create_task(payload)
    except Exception as e:
        logging.warn("Error creating task: %s" % traceback.format_exc())

def _send_fcm_to_token(request):
    payload = json.loads(base64.b64decode(request.data).decode())
    token_key = payload['key']
    message = payload['message']
    logging.info("Executing deferred task: _send_fcm_to_token, %s, %s" % (token_key, message))
    token = dao.get_fcm_token_for_key(token_key)

    fcm = FCM(dao, sys.modules[__name__])
    fcm.send_fcm([token], message)
'''