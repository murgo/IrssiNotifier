use strict;

use Irssi;
use IPC::Open2 qw(open2);
use POSIX;
use vars qw($VERSION %IRSSI);

$VERSION = "8";
%IRSSI   = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "IrssiNotifier",
    description => "Send notifications about irssi highlights to server",
    license     => "Apache License, version 2.0",
    url         => "http://irssinotifier.appspot.com",
    changed     => "2012-08-24"
);

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastAddress;
my $lastTarget;
my $lastKeyboardActivity = time;

sub private {
    my ( $server, $msg, $nick, $address ) = @_;
    $lastServer = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = "!PRIVATE";
}

sub public {
    my ( $server, $msg, $nick, $address, $target ) = @_;
    $lastServer = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = $target;
}

sub print_text {
    my ($dest, $text, $stripped) = @_;
    
    if (should_send_notification($dest))
    {
        hilite();
    }
}

sub should_send_notification {
    my $dest = @_ ? shift : $_;

    my $opt = MSGLEVEL_HILIGHT | MSGLEVEL_MSGS;
    if (!($dest->{level} & $opt) || ($dest->{level} & MSGLEVEL_NOHILIGHT)) {
        return 0; # not a hilight
    }

    if (!are_settings_valid()) {
        return 0; # invalid settings
    }

    if (Irssi::settings_get_bool("irssinotifier_away_only") && !$lastServer->{usermode_away}) {
        return 0; # away only
    }

    if (Irssi::settings_get_bool("irssinotifier_ignore_active_window") && $dest->{window}->{refnum} == Irssi::active_win()->{refnum}) {
        return 0; # ignore active window
    }

    my $ignore_server_string = Irssi::settings_get_str("irssinotifier_ignore_server");
    if ($ignore_server_string) {
        my @ignored_servers = split(/ /, $ignore_server_string);
        my $server;

        foreach $server (@ignored_servers) {
            if (lc($server) eq lc($lastServer->{tag})) {
                return 0; # ignored server
            }
        }
    }

    my $ignore_channel_string = Irssi::settings_get_str("irssinotifier_ignore_channel");
    if ($ignore_channel_string) {
        my @ignored_channels = split(/ /, $ignore_channel_string);
        my $channel;

        foreach $channel (@ignored_channels) {
            if (lc($channel) eq lc($lastTarget)) {
                return 0; # ignored channel
            }
        }
    }

    if (Irssi::settings_get_str("irssinotifier_ignore_channel") && Irssi::settings_get_str("irssinotifier_ignore_channel") eq $lastTarget) {
        return 0; # ignored channel
    }

    my $timeout = Irssi::settings_get_int('irssinotifier_require_idle_seconds');
    if ($timeout > 0 && (time - $lastKeyboardActivity) <= $timeout) {
        return 0; # not enough idle seconds
    }
    
    return 1;
}

sub is_dangerous_string {
    my $s = @_ ? shift : $_;
    return $s =~ m/"/ || $s =~ m/`/ || $s =~ m/\\/;
}

sub hilite {
    my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
    
    my $encryption_password = Irssi::settings_get_str('irssinotifier_encryption_password');
    $lastMsg    = encrypt(Irssi::strip_codes($lastMsg));
    $lastNick   = encrypt($lastNick);
    $lastTarget = encrypt($lastTarget);

    my $data = "--post-data=apiToken=$api_token\\&message=$lastMsg\\&channel=$lastTarget\\&nick=$lastNick\\&version=$VERSION";
    my $result = `/usr/bin/env wget --no-check-certificate -qO- /dev/null $data https://irssinotifier.appspot.com/API/Message`;
    if ($? != 0) {
        # Something went wrong, might be network error or authorization issue. Probably no need to alert user, though.
        # Irssi::print("IrssiNotifier: Sending hilight to server failed, check http://irssinotifier.appspot.com for updates");
        return;
    }
    
    if (length($result) > 0) {
        Irssi::print("IrssiNotifier: $result");
    }
}

sub encrypt {
    my ($text) = @_ ? shift : $_;
    
    local $ENV{PASS} = Irssi::settings_get_str('irssinotifier_encryption_password');
    my $pid = open2 my $out, my $in, qw(
        openssl enc -aes-128-cbc -salt -base64 -A -pass env:PASS
    );

    print $in "$text ";
    close $in;
    undef $/;    # read full output at once
    my $result = readline $out;
    waitpid $pid, 0;

    $result =~ tr[+/][-_];
    $result =~ s/=//g;
    return $result;
}

sub are_settings_valid {
    Irssi::signal_remove( 'gui key pressed', 'event_key_pressed' );
    if (Irssi::settings_get_int('irssinotifier_require_idle_seconds') > 0) {
        Irssi::signal_add( 'gui key pressed', 'event_key_pressed' );
    }

    if (!Irssi::settings_get_str('irssinotifier_api_token')) {
        Irssi::print("IrssiNotifier: Set API token to send notifications: /set irssinotifier_api_token [token]");
        return 0;
    }

    unless (-x "/usr/bin/openssl") {
        Irssi::print("IrssiNotifier: /usr/bin/openssl not found.");
        return 0;
    }

    unless (-x "/usr/bin/wget") {
        Irssi::print("IrssiNotifier: /usr/bin/wget not found.");
        return 0;
    }

    my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
    if (!$api_token) {
        Irssi::print("IrssiNotifier: Set API token to send notifications (check your token at https://irssinotifier.appspot.com): /set irssinotifier_api_token [token]");
        return 0;
    } elsif (is_dangerous_string($api_token)) {
        Irssi::print("IrssiNotifier: API token cannot contain backticks, double quotes or backslashes");
        return 0;
    }

    my $encryption_password  = Irssi::settings_get_str('irssinotifier_encryption_password');
    if (!$encryption_password) {
        Irssi::print("IrssiNotifier: Set encryption password to send notifications (must be same as in the Android device): /set irssinotifier_encryption_password [password]");
        return 0;
    } elsif (is_dangerous_string $encryption_password ) {
        Irssi::print("IrssiNotifier: Encryption password cannot contain backticks, double quotes or backslashes");
        return 0;
    }

    return 1;
}

sub event_key_pressed {
    $lastKeyboardActivity = time;
}

Irssi::settings_add_str('irssinotifier', 'irssinotifier_encryption_password', 'password');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_api_token', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignore_server', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignore_channel', '');
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_away_only', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_ignore_active_window', 0);
Irssi::settings_add_int('irssinotifier', 'irssinotifier_require_idle_seconds', 0);

Irssi::signal_add( 'message irc action', 'public');
Irssi::signal_add( 'message public',     'public');
Irssi::signal_add( 'message private',    'private');
Irssi::signal_add( 'print text',         'print_text');
Irssi::signal_add( 'setup changed',      'are_settings_valid');

