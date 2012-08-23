use strict;

#use warnings;

use Irssi;
use IPC::Open2 qw(open2);
use POSIX;
use LWP::UserAgent;
use vars qw($VERSION %IRSSI);

$VERSION = "7";
%IRSSI   = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "IrssiNotifier",
    description => "Send notifications about irssi highlights to server",
    license     => "Apache License, version 2.0",
    url         => "http://irssinotifier.appspot.com",
    changed     => "2012-07-12"
);

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastAddress;
my $lastTarget;
my $lastKeyboardActivity = time;
my $valid                = 0;
my $ua = LWP::UserAgent->new( agent => "irssinotifier/$VERSION" );
$ua->timeout(3);

sub private {
    my ( $server, $msg, $nick, $address ) = @_;
    $lastServer = $server;
    if (   Irssi::settings_get_str("irssinotifier_ignore_server")
        && Irssi::settings_get_str("irssinotifier_ignore_server") eq
        $lastServer->{tag} )
    {
        return;
    }
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = "!PRIVATE";
}

sub public {
    my ( $server, $msg, $nick, $address, $target ) = @_;
    $lastServer = $server;
    if (   Irssi::settings_get_str("irssinotifier_ignore_server")
        && Irssi::settings_get_str("irssinotifier_ignore_server") eq
        $lastServer->{tag} )
    {
        return;
    }
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = $target;
}

sub print_text {
    my ( $dest, $text, $stripped ) = @_;

    my $opt = MSGLEVEL_HILIGHT | MSGLEVEL_MSGS;
    if (
           ( $dest->{level} & ($opt) )
        && ( ( $dest->{level} & MSGLEVEL_NOHILIGHT ) == 0 )
        && ( !Irssi::settings_get_bool("irssinotifier_away_only")
            || $lastServer->{usermode_away} )
        && ( !Irssi::settings_get_bool("irssinotifier_ignore_active_window")
            || (
                $dest->{window}->{refnum} != ( Irssi::active_win()->{refnum} ) )
        )
        && (   Irssi::settings_get_str("irssinotifier_ignore_server")
            && Irssi::settings_get_str("irssinotifier_ignore_server") ne
            $lastServer->{tag} )
        && activity_allows_hilight()
      )
    {
        hilite();
    }
}

sub activity_allows_hilight {
    my $timeout = Irssi::settings_get_int('irssinotifier_require_idle_seconds');
    return ( $timeout <= 0 || ( time - $lastKeyboardActivity ) > $timeout );
}

sub dangerous_string {
    my $s = @_ ? shift : $_;
    return $s =~ m/"/ || $s =~ m/`/ || $s =~ m/\\/;
}

sub hilite {
    return unless $valid;

    my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
    my $encryption_password =
      Irssi::settings_get_str('irssinotifier_encryption_password');
    if ($encryption_password) {
        if (dangerous_string $encryption_password) {
            Irssi::print("IrssiNotifier: Encryption password cannot contain backticks, double quotes or backslashes");
            return;
        }
        $lastMsg    = encrypt(Irssi::strip_codes($lastMsg));
        $lastNick   = encrypt($lastNick);
        $lastTarget = encrypt($lastTarget);
    }

    my $res = $ua->post(
        "https://irssinotifier.appspot.com/API/Message",
        {
            apiToken => $api_token,
            message  => $lastMsg,
            channel  => $lastTarget,
            nick     => $lastNick,
            version  => $VERSION,
        }
    );
    if ( $res->is_error ) {

        # Something went wrong, might be network error or authorization issue.
        # Probably no need to alert user, though.
        # Irssi::print( "IrssiNotifier: Sending hilight to server failed, " .
        #               "check http://irssinotifier.appspot.com for updates");
        return;
    }

    if ( length( $res->decoded_content ) > 0 ) {
        Irssi::print( "IrssiNotifier: " . $res->decoded_content );
    }
}

sub encrypt {
    my ($text) = @_;
    local $ENV{PASS} =
      Irssi::settings_get_str('irssinotifier_encryption_password');
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

sub setup_keypress_handler {
    $valid = 1;
    Irssi::signal_remove( 'gui key pressed', 'event_key_pressed' );
    if ( Irssi::settings_get_int('irssinotifier_require_idle_seconds') > 0 ) {
        Irssi::signal_add( 'gui key pressed', 'event_key_pressed' );
    }

    if ( !Irssi::settings_get_str('irssinotifier_api_token') ) {
        Irssi::print(
"IrssiNotifier: Set API token to send notifications: /set irssinotifier_api_token [token]"
        );
        $valid = 0;
    }

    unless ( -x "/usr/bin/openssl" ) {
        Irssi::print("IrssiNotifier: /usr/bin/openssl not found.");
        $valid = 0;
    }

    if ( dangerous_string Irssi::settings_get_str('irssinotifier_api_token') ) {
        Irssi::print(
"IrssiNotifier: Api token cannot contain backticks, double quotes or backslashes"
        );
        $valid = 0;
    }

    my $encryption_password =
      Irssi::settings_get_str('irssinotifier_encryption_password');
    if ( $encryption_password and dangerous_string $encryption_password) {
        Irssi::print(
"IrssiNotifier: Encryption password cannot contain backticks, double quotes or backslashes"
        );
        $valid = 0;
    } elsif ( not $encryption_password ) {
        Irssi::print(
"IrssiNotifier: Set encryption password to send notifications (must be same as in the Android device): /set irssinotifier_encryption_password [password]"
        );
        $valid = 0;
    }

    unless ($valid) {
        Irssi::print("IrssiNotifier: invalid settings, notifications disabled");
    }
}

sub event_key_pressed {
    $lastKeyboardActivity = time;
}

Irssi::settings_add_str( 'IrssiNotifier', 'irssinotifier_encryption_password',
    'password' );
Irssi::settings_add_str( 'IrssiNotifier', 'irssinotifier_api_token',     '' );
Irssi::settings_add_str( 'IrssiNotifier', 'irssinotifier_ignore_server', '' );
Irssi::settings_add_bool( 'IrssiNotifier', 'irssinotifier_away_only', 0 );
Irssi::settings_add_bool( 'IrssiNotifier', 'irssinotifier_ignore_active_window',
    0 );
Irssi::settings_add_int( 'IrssiNotifier', 'irssinotifier_require_idle_seconds',
    0 );

Irssi::signal_add( 'message irc action', 'public' );
Irssi::signal_add( 'message public',     'public' );
Irssi::signal_add( 'message private',    'private' );
Irssi::signal_add( 'print text',         'print_text' );
Irssi::signal_add( 'setup changed',      'setup_keypress_handler' );

setup_keypress_handler();
