import logging
from datamodels import GcmToken
from fcm import FCM
import json
import unittest


class MockDao():
    def __init__(self):
        self.removed_tokens = []
        self.updated_tokens = []

    def load_fcm_auth_key(self):
        return '123'

    def remove_fcm_token(self, token):
        self.removed_tokens.append(token)

    def update_fcm_token(self, token, new_token_id):
        self.updated_tokens.append((token, new_token_id))


class MockFcmHelper():
    def __init__(self):
        self.sent_tokens = []

    def send_fcm_to_token_deferred(self, token, message):
        self.sent_tokens.append((token, message))


class TestFcm(unittest.TestCase):

    def test_canonical_ids(self):
        logging.root.setLevel(logging.DEBUG)

        mock_dao = MockDao()
        mock_helper = MockFcmHelper()
        fcm = FCM(mock_dao, mock_helper)
        fcm.tokens = [GcmToken(fcm_token='0'), GcmToken(fcm_token='1'), GcmToken(fcm_token='2'),
                      GcmToken(fcm_token='3'), GcmToken(fcm_token='4'), GcmToken(fcm_token='5'),
                      GcmToken(fcm_token='6'), GcmToken(fcm_token='7')]

        message = 'testing testing 1 2 3'

        response = {'multicast_id': 666, 'success': 4, 'canonical_ids': 2,
                    'results': [
                        {'error': 'something else'},
                        {'message_id': '11'},  # success
                        {'message_id': '22', 'registration_id': '3'},  # message with already existing canonical id
                        {'message_id': '33'},  # canonical id for previous
                        {'message_id': '44', 'registration_id': '123'},  # totally new canonical id
                        {'error': 'NotRegistered'},  # not registered
                        {'error': 'Unavailable'},
                        {'error': 'something else'}
                    ]}

        js = json.dumps(response)

        response_json = json.loads(js)
        results = response_json["results"]
        index = -1
        for result in results:
            index += 1
            token = fcm.tokens[index]
            fcm.handle_fcm_result(result, token, message)

        self.assertEqual(2, len(mock_dao.removed_tokens))
        self.assertEqual('2', mock_dao.removed_tokens[0].gcm_token)
        self.assertEqual('5', mock_dao.removed_tokens[1].gcm_token)

        self.assertEqual(1, len(mock_dao.updated_tokens))
        self.assertEqual('4', mock_dao.updated_tokens[0][0].gcm_token)
        self.assertEqual('123', mock_dao.updated_tokens[0][1])

        self.assertEqual(1, len(mock_helper.sent_tokens))
        self.assertEqual('6', mock_helper.sent_tokens[0][0].gcm_token)
        self.assertEqual(message, mock_helper.sent_tokens[0][1])
