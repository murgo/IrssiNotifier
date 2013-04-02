import weechat
import irssinotifier

_NAME = 'weechatnotifier'
_VERSION = '0.1'
_AUTHOR = 'Evgeni Golov <evgeni@golov.de>'
_LICENSE = 'Apache 2.0'
_DESCRIPTION = 'Use the IrssiNotifier service to push hilights and private messages to your Android device.'

settings = {
    'api_token': '',
    'enc_password': '',
    'away_only': '0',
}

notifier = None


def send_notification(data, buf, date, tags, displayed, hilight, prefix, msg):
    if not notifier:
        return weechat.WEECHAT_RC_OK

    away = weechat.buffer_get_string(buf, 'localvar_away')
    if not away and int(weechat.config_get_plugin('away_only')):
        return weechat.WEECHAT_RC_OK

    modes = '~+@!%'
    prefix = prefix.lstrip(modes)

    me = weechat.buffer_get_string(buf, "localvar_nick")
    if prefix == me:
        return weechat.WEECHAT_RC_OK

    if weechat.buffer_get_string(buf, "localvar_type") == "private":
        notifier.send_message(msg, prefix, prefix)
    elif hilight:
        channel = (weechat.buffer_get_string(buf, "short_name") or
                   weechat.buffer_get_string(buf, "name"))
        notifier.send_message(msg, channel, prefix)

    return weechat.WEECHAT_RC_OK


def reload_config(data, option, value):
    global notifier
    if option not in ['api_token', 'enc_password']:
        return weechat.WEECHAT_RC_OK
    api_token = weechat.config_get_plugin("api_token")
    enc_password = weechat.config_get_plugin("enc_password")
    notifier = None
    if not api_token:
        weechat.prnt('', _NAME+': The API token is not set yet.')
    elif not enc_password:
        weechat.prnt('', _NAME+': The encryption password is not set yet.')
    else:
        notifier = irssinotifier.IrssiNotifier(api_token, enc_password)
    return weechat.WEECHAT_RC_OK

weechat.register(_NAME, _AUTHOR, _VERSION, _LICENSE, _DESCRIPTION, "", "")

weechat.hook_config("plugins.var.python." + _NAME + ".*", "reload_config", "")
weechat.hook_print("", "irc_privmsg", "", 1, "send_notification", "")

for option, default_value in settings.items():
    if not weechat.config_is_set_plugin(option):
        weechat.config_set_plugin(option, default_value)

api_token = weechat.config_get_plugin("api_token")
enc_password = weechat.config_get_plugin("enc_password")
if api_token and enc_password:
    notifier = irssinotifier.IrssiNotifier(api_token, enc_password)
