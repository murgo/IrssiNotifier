#use strict;
#use warnings;

use Irssi;
use POSIX;
use vars qw($VERSION %IRSSI);

$VERSION = "3";
%IRSSI = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "IrssiNotifier",
    description => "Send notifications about irssi highlights to server",
    license     => "Apache License, version 2.0",
    url         => "http://irssinotifier.appspot.com",
    changed     => "2012-03-20"
);

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastAddress;
my $lastTarget;
my $lastKeyboardActivity = time;

sub private {
    my ($server, $msg, $nick, $address) = @_;
    $lastMsg = $msg;
    $lastServer = $server;
    $lastNick = $nick;
    $lastAddress = $address;
    $lastTarget = "!PRIVATE";
}

sub public {
    my ($server, $msg, $nick, $address, $target) = @_;
    $lastMsg = $msg;
    $lastServer = $server;
    $lastNick = $nick;
    $lastAddress = $address;
    $lastTarget = $target;
}

sub print_text {
    my ($dest, $text, $stripped) = @_;

    my $opt = MSGLEVEL_HILIGHT|MSGLEVEL_MSGS;
    if (
        ($dest->{level} & ($opt)) && (($dest->{level} & MSGLEVEL_NOHILIGHT) == 0) &&
        (!Irssi::settings_get_bool("irssinotifier_away_only") || $lastServer->{usermode_away}) &&
        (!Irssi::settings_get_bool("irssinotifier_ignore_active_window") || ($dest->{window}->{refnum} != (Irssi::active_win()->{refnum}))) &&
        activity_allows_hilight()
    ) {
        hilite();
    }
}

sub activity_allows_hilight {
    my $timeout = Irssi::settings_get_int('irssinotifier_require_idle_seconds');
    return ($timeout <= 0 || (time - $lastKeyboardActivity) > $timeout);
}

sub hilite {
    if (!Irssi::settings_get_str('irssinotifier_api_token')) {
        Irssi::print("IrssiNotifier: Set API token to send notifications: /set irssinotifier_api_token [token]");
        return;
    }

    `/usr/bin/env openssl version`;
    if ($? != 0) {
        Irssi::print("IrssiNotifier: You'll need to install OpenSSL to use IrssiNotifier");
        return;
    }

    `/usr/bin/env wget --version`;
    if ($? != 0) {
        Irssi::print("IrssiNotifier: You'll need to install Wget to use IrssiNotifier");
        return;
    }

    my $encryption_password = Irssi::settings_get_str('irssinotifier_encryption_password');
    if ($encryption_password) {
        $lastMsg = encrypt($lastMsg);
        $lastNick = encrypt($lastNick);
        $lastTarget = encrypt($lastTarget);
    } else {
        Irssi::print("IrssiNotifier: Set encryption password to send notifications (must be same as in the Android device): /set irssinotifier_encryption_password [password]");
    }

    my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
    my $data = "--post-data=apiToken=$api_token\\&message=$lastMsg\\&channel=$lastTarget\\&nick=$lastNick\\&version=$VERSION";
    my $result = `/usr/bin/env wget --no-check-certificate -qO- /dev/null $data https://irssinotifier.appspot.com/API/Message`;
    if ($? != 0) {
        if ($? != 4) {
            Irssi::print("IrssiNotifier: Unauthorized, please check your api token");
            return;
        }
        Irssi::print("IrssiNotifier: Sending hilight to server failed, check http://irssinotifier.appspot.com for updates");
        return;
    }
    
    if (length($result) > 0) {
        Irssi::print("IrssiNotifier: $result");
    }
}

sub sanitize {
    my $str = @_ ? shift : $_;
    $str =~ s/((?:^|[^\\])(?:\\\\)*)'/$1\\'/g;
    $str =~ s/\\'/´/g; # stupid perl
    $str =~ s/'/´/g; # stupid perl
    return "'$str'";
}

sub encrypt {
    my $text = $_[0];
    $text = sanitize $text;
    my $encryption_password = Irssi::settings_get_str('irssinotifier_encryption_password');
    my $result = `echo $text| /usr/bin/env openssl enc -aes-128-cbc -salt -base64 -A -k $encryption_password | tr -d '\n'`;
    $result =~ s/=//g;
    $result =~ s/\+/-/g;
    $result =~ s/\//_/g;
    chomp($result);
    return $result;
}

sub decrypt {
    my $text = $_[0];
    $text = sanitize $text;
    my $encryption_password = Irssi::settings_get_str('irssinotifier_encryption_password');
    my $result = `echo $text| /usr/bin/env openssl enc -aes-128-cbc -d -salt -base64 -A -k $encryption_password`;
    chomp($result);
    return $result;
}

sub setup_keypress_handler {
    Irssi::signal_remove('gui key pressed', 'event_key_pressed');
    if (Irssi::settings_get_int('irssinotifier_require_idle_seconds') > 0) {
        Irssi::signal_add('gui key pressed', 'event_key_pressed');
    }
}

sub event_key_pressed {
    $lastKeyboardActivity = time;
}

Irssi::settings_add_str('IrssiNotifier', 'irssinotifier_encryption_password', 'password');
Irssi::settings_add_str('IrssiNotifier', 'irssinotifier_api_token', '');
Irssi::settings_add_bool('IrssiNotifier', 'irssinotifier_away_only', false);
Irssi::settings_add_bool('IrssiNotifier', 'irssinotifier_ignore_active_window', false);
Irssi::settings_add_int('IrssiNotifier', 'irssinotifier_require_idle_seconds', 0);

Irssi::signal_add('message public', 'public');
Irssi::signal_add('message private', 'private');
Irssi::signal_add('print text', 'print_text');
Irssi::signal_add('setup changed', 'setup_keypress_handler');

setup_keypress_handler();
