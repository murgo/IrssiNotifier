#!/usr/bin/env python
#
# Copyright 2012 Lauri Härsilä
# based on EmailLoggingHandler, Copyright 2011 Pamela Fox
# based on XMPPLoggingHandler, Copyright 2011 Calvin Rien,
# based on ExceptionRecordHandler, Copyright 2007 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""A logging handler that sends errors over email.

Example usage:

In your handler script(s), add:

    import logging
    import email_logger

    email_logger.register_logger('recipient@gmail.address')        
"""

import hashlib
import logging
import os

from google.appengine.api import memcache
from google.appengine.api import mail

LOG_FORMAT = '%(levelname)-8s %(asctime)s %(filename)s:%(lineno)s] %(message)s'

MAX_SIGNATURE_LENGTH = 256

debug = os.environ.get('SERVER_SOFTWARE', '').startswith('Dev')
app_id = os.environ.get('APPLICATION_ID')
app_ver = os.environ.get('CURRENT_VERSION_ID')

class EmailLoggingHandler(logging.Handler):
    """A handler that sends log messages out over XMPP.    
    """

    def __init__(self, recipients, log_interval=350, log_level=logging.NOTSET):
        """
        Constructs a new EmailLoggingHandler.
        
        @param recipients: can be email address, 
                           list of email addresses, 
                           tuple ('email_address', logging_level), 
                           or dictionary {'email_address':logging_level, ...}
        @param log_interval: How long before a log entry will generate another message.
                            This is to prevent spamming if tons of requests 
                            are generating the same log message.
        @param log_level: default log level that will be sent over XMPP
        """
        self.log_interval = log_interval

        if isinstance(recipients, basestring):
            recipients = recipients.split(',')
        elif isinstance(recipients, tuple):
            recipients = dict([recipients])

        if isinstance(recipients, (list, set)):
            self.recipients = {}
            for recipient in recipients:
                self.recipients[recipient] = {}
        elif isinstance(recipients, dict):
            self.recipients = recipients
        else:
            raise Exception('recipients argument must be string, list, or dict')

        for address, params in self.recipients.items():
            if not isinstance(params, dict):
                params = {'level': params}

            if 'level' not in params:
                params['level'] = log_level

            self.recipients[address] = params

        logging.Handler.__init__(self)

        self.setFormatter(logging.Formatter(LOG_FORMAT))

    @classmethod
    def __GetRecordSignature(cls, record):
        """Returns a unique signature string for a record.

        Args:
            record: The record object being logged.

        Returns:
            A unique signature string for the record.
        """
        signature = ':'.join([str(e) for e in [record.levelno, record.pathname, record.lineno, record.funcName]])
        if len(signature) > MAX_SIGNATURE_LENGTH:
            signature = 'hash:%s' % hashlib.sha256(signature).hexdigest()

        return signature

    def emit(self, record):
        """Send a log error over email.

        Args:
            The logging.LogRecord object.
                See http://docs.python.org/library/logging.html#logging.LogRecord
        """
        if debug or record.levelno < logging.WARNING:
            # NOTE: You don't want to try this on dev_appserver. Trust me.
            return

        signature = self.__GetRecordSignature(record)

        if not memcache.add(signature, True, time=self.log_interval):
            return

        formatted_record = self.format(record)
            
        try:
            for jid, params in self.recipients.items():
                if record.levelno < params['level']:
                    continue

                sender = 'errors@%s.appspotmail.com' % (app_id)
                message = mail.EmailMessage(sender=sender, to=self.recipients)
                message.subject = '(%s) error reported for %s, version %s' % (record.levelname, app_id, app_ver)
                message.body = formatted_record
                message.send()

        except Exception:
            self.handleError(record)


def register_logger(recipients, logger=None):
    """
    @param recipients: can be email address, 
                       list of email addresses, 
                       tuple ('email_address', logging_level), 
                       or dictionary {'email_address':logging_level, ...}
    @param logger: optional logger to add the handler to.    

    """
    if not logger:
        logger = logging.getLogger()
    handler = EmailLoggingHandler(recipients)
    logger.addHandler(handler)
    return handler