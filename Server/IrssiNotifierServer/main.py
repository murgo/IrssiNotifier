from flask import Flask, request
import logging
from controllers import *
import secret_manager

app = Flask(__name__)
app.secret_key = secret_manager.get_secret('flask-secret')
logging.basicConfig(level=logging.DEBUG)

@app.errorhandler(404)
def handle_404(error):
    logging.warning(f"404'd, error: {error}")
    return "lol 404'd", 404

@app.route('/oauth2callback')
def oauth2callback():
    return login.oauth2callback()

@app.route('/logout')
def logout():
    return login.logout();

@app.route('/')
def web_controller():
    return WebController().get()

@app.route('/API/Settings', methods=['GET', 'POST'])
def settings_controller():
    return SettingsController().get() if request.method == 'GET' else SettingsController().post()

@app.route('/API/Message', methods=['GET', 'POST'])
def message_controller():
    return MessageController().get() if request.method == 'GET' else MessageController().post()

@app.route('/API/DeferredMessage', methods=['GET', 'POST'])
def deferred_message_controller():
    return DeferredMessageController().get() if request.method == 'GET' else DeferredMessageController().post()

@app.route('/API/Command', methods=['GET', 'POST'])
def command_controller():
    return CommandController().get() if request.method == 'GET' else CommandController().post()

@app.route('/API/Wipe', methods=['GET', 'POST'])
def wipe_controller():
    return WipeController().get() if request.method == 'GET' else WipeController().post()

@app.route('/API/Nonce', methods=['GET', 'POST'])
def nonce_controller():
    return NonceController().get() if request.method == 'GET' else NonceController().post()

@app.route('/API/License', methods=['GET', 'POST'])
def licensing_controller():
    return LicensingController().get() if request.method == 'GET' else LicensingController().post()

if __name__ == '__main__':
    logging.debug("loaded main")
    app.run(debug=True)
