import json
from google.auth.transport.requests import Request
from google.oauth2 import service_account

import secret_manager

def getBearerToken():
    # Scopes required for FCM
    SCOPES = ['https://www.googleapis.com/auth/firebase.messaging']

    service_account_info = json.loads(secret_manager.get_secret("firebase-messaging-service-account-info"))

    # Load the service account credentials
    credentials = service_account.Credentials.from_service_account_info(
        service_account_info, scopes=SCOPES)

    # Refresh the token (if necessary)
    credentials.refresh(Request())

    # Get the OAuth 2.0 token
    token = credentials.token
    print("OAuth 2.0 Bearer Token:", token)
    return token
