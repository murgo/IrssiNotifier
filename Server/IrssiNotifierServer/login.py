import logging
import os
import dao
from flask import request, session, redirect
from google.oauth2 import id_token
from google.auth.transport import requests as authrequests
import requests

import secret_manager


def oauth2callback():
    code = request.args.get('code')
    if not code:
        return 'Authorization failed.'

    LOGIN_REDIRECT_URI = os.getenv('LOGIN_REDIRECT_URI')
    CLIENT_ID = secret_manager.get_secret("oauth2-client-id")

    try:
        # Exchange the authorization code for an access token
        token_response = requests.post(
            'https://oauth2.googleapis.com/token',
            data={
                'code': code,
                'client_id': CLIENT_ID,
                'client_secret': secret_manager.get_secret('oauth2-client-secret'),
                'redirect_uri': LOGIN_REDIRECT_URI,
                'grant_type': 'authorization_code'
            }
        )

        token_data = token_response.json()
        logging.debug(token_data)

        # Check if the access token is present
        if 'id_token' in token_data:
            session['id_token'] = token_data['id_token']
            return redirect("/#profile");
        else:
            return f"Authorization failed. Error: {token_data.get('error_description', 'Unknown error')}"
    except Exception as e:
        logging.error(f"Error during token exchange: {str(e)}")
        return f"Authorization failed. Error: {str(e)}"

def logout():
    session.clear()
    return redirect("/");

def get_irssi_user(params, allowApiTokenUse):
    logging.info("Login.getIrssiUser()")

    if 'id_token' not in session:
        if not allowApiTokenUse:
            logging.debug("No OAuth Login and not allowing to use API token, failing")
            return None

        logging.debug("No OAuth Login, using API token")
        if 'apiToken' not in params:
            logging.debug("No API token found, failing")
            return None
        token = params['apiToken']
        logging.debug("apiToken %s" % token)
        irssi_user = dao.get_irssi_user_for_api_token(token)
        if irssi_user is not None:
            logging.debug('authorized as %s (%s)' % (irssi_user.user_name, irssi_user.email))
            return irssi_user
        logging.warning('No user found with the api token!')
        return None

    # try using oauth login from session info
    try:
        idToken = session['id_token'] # jwt token from openid
        logging.debug(f"Access token {idToken}")

        client_id = secret_manager.get_secret("oauth2-client-id")

        # Verify the ID token
        id_info = id_token.verify_oauth2_token(idToken, authrequests.Request(), client_id)
        logging.debug(f"Id_info: {id_info}")

        # ID token is valid, get user info
        user_id = id_info.get('sub')
        email = id_info.get('email')
        logging.debug(f"Got google login: {email}")

        irssi_user = dao.get_irssi_user_for_key_name(user_id, email)
        if irssi_user is None:
            logging.debug("IrssiUser not found, adding new one")
            irssi_user = dao.add_irssi_user(email, user_id)

        logging.debug(f"authorized as {irssi_user.user_name} ({irssi_user.email})")
        return irssi_user

    except ValueError as e:
        logging.warning(f'Invalid ID token! Error {e}')
        return None

