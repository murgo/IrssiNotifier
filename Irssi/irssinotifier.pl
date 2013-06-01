use strict; use warnings;

use Irssi;
use IPC::Open2 qw(open2);
use POSIX;
use Encode;
use vars qw($VERSION %IRSSI);

$VERSION = "17";
%IRSSI   = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "IrssiNotifier",
    description => "Send notifications about irssi highlights to server",
    license     => "Apache License, version 2.0",
    url         => "https://irssinotifier.appspot.com",
    changed     => "2013-05-23"
);

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastTarget;
my $lastWindow;
my $lastKeyboardActivity = time;
my $forked;
my $lastDcc = 0;
my $notifications_sent = 0;
my @delayQueue = ();

my $screen_socket_path;

sub private {
    my ( $server, $msg, $nick, $address ) = @_;
    $lastServer  = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastTarget  = "!PRIVATE";
    $lastWindow  = $nick;
    $lastDcc = 0;
}

sub joined {
    my ( $server, $target, $nick, $address ) = @_;
    $lastServer  = $server;
    $lastMsg     = "joined";
    $lastNick    = $nick;
    $lastTarget  = $target;
    $lastWindow  = $target;
    $lastDcc = 0;
}

sub public {
    my ( $server, $msg, $nick, $address, $target ) = @_;
    $lastServer  = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastTarget  = $target;
    $lastWindow  = $target;
    $lastDcc = 0;
}

sub dcc {
    my ( $dcc, $msg ) = @_;
    $lastServer  = $dcc->{server};
    $lastMsg     = $msg;
    $lastNick    = $dcc->{nick};
    $lastTarget  = "!PRIVATE";
    $lastWindow  = $dcc->{target};
    $lastDcc = 1;
}

sub print_text {
    my ($dest, $text, $stripped) = @_;

    if (!defined $lastMsg || index($text, $lastMsg) == -1)
    {
        # text doesn't contain the message, so printed text is about something else and notification doesn't need to be sent
        return;
    }

    if (should_send_notification($dest))
    {
        send_notification();
    }
}

sub should_send_notification {
    my $dest = @_ ? shift : $_;

    my $opt = MSGLEVEL_HILIGHT | MSGLEVEL_MSGS;
    if (!$lastDcc && (!($dest->{level} & $opt) || ($dest->{level} & MSGLEVEL_NOHILIGHT))) {
        return 0; # not a hilight and not a dcc message
    }

    if (!are_settings_valid()) {
        return 0; # invalid settings
    }

    if (Irssi::settings_get_bool("irssinotifier_away_only") && !$lastServer->{usermode_away}) {
        return 0; # away only
    }

    if ($lastDcc && !Irssi::settings_get_bool("irssinotifier_enable_dcc")) {
        return 0; # dcc is not enabled
    }

    if (Irssi::settings_get_bool('irssinotifier_screen_detached_only') && screen_attached()) {
        return 0; # screen attached
    }

    if (Irssi::settings_get_bool("irssinotifier_ignore_active_window") && $dest->{window}->{refnum} == Irssi::active_win()->{refnum}) {
        return 0; # ignore active window
    }

    my $ignored_servers_string = Irssi::settings_get_str("irssinotifier_ignored_servers");
    if ($ignored_servers_string) {
        my @ignored_servers = split(/ /, $ignored_servers_string);
        my $server;

        foreach $server (@ignored_servers) {
            if (lc($server) eq lc($lastServer->{tag})) {
                return 0; # ignored server
            }
        }
    }

    my $ignored_channels_string = Irssi::settings_get_str("irssinotifier_ignored_channels");
    if ($ignored_channels_string) {
        my @ignored_channels = split(/ /, $ignored_channels_string);
        my $channel;

        foreach $channel (@ignored_channels) {
            if (lc($channel) eq lc($lastWindow)) {
                return 0; # ignored channel
            }
        }
    }

    # Ignore any highlights from given nicks
    my $ignored_nicks_string = Irssi::settings_get_str("irssinotifier_ignored_nicks");
    if ($ignored_nicks_string ne '') {
        my @ignored_nicks = split(/ /, $ignored_nicks_string);
        if (grep { lc($_) eq lc($lastNick) } @ignored_nicks) {
            return 0; # Ignored nick
        }
    }

    # Ignore any highlights that match any specified patterns
    my $ignored_highlight_pattern_string = Irssi::settings_get_str("irssinotifier_ignored_highlight_patterns");
    if ($ignored_highlight_pattern_string ne '') {
        my @ignored_patterns = split(/ /, $ignored_highlight_pattern_string);
        if (grep { $lastMsg =~ /$_/i } @ignored_patterns) {
            return 0; # Ignored pattern
        }
    }

    # If specified, require a pattern to be matched before highlighting public messages
    my $required_public_highlight_pattern_string = Irssi::settings_get_str("irssinotifier_required_public_highlight_patterns");
    if ($required_public_highlight_pattern_string ne '' && ($dest->{level} & MSGLEVEL_PUBLIC)) {
        my @required_patterns = split(/ /, $required_public_highlight_pattern_string);
        if (!(grep { $lastMsg =~ /$_/i } @required_patterns)) {
            return 0; # Required pattern not matched
        }
    }

    my $timeout = Irssi::settings_get_int('irssinotifier_require_idle_seconds');
    if ($timeout > 0 && (time - $lastKeyboardActivity) <= $timeout && screen_attached()) {
        return 0; # not enough idle seconds
    }

    return 1;
}

sub screen_attached {
    if (!$screen_socket_path || !defined($ENV{STY})) {
        return 1;
    }
    my $socket = $screen_socket_path . "/" . $ENV{'STY'};
    if (-e $socket && ((stat($socket))[2] & 00100) != 0) {
        return 1;
    }
    return 0;
}

sub is_dangerous_string {
    my $s = @_ ? shift : $_;
    return $s =~ m/"/ || $s =~ m/`/ || $s =~ m/\\/;
}

sub send_notification {
    if ($forked) {
        if (scalar @delayQueue < 10) {
            push @delayQueue, {
                            'msg' => $lastMsg,
                            'nick' => $lastNick,
                            'target' => $lastTarget,
                            'added' => time,
                            };
        } else {
            Irssi::print("IrssiNotifier: previous send is still in progress and queue is full, skipping notification");
        }
        return 0;
    }
    send_to_api();
}

sub send_command {
    my $cmd = shift || return;
    return if ($forked); # no need to queue commands?
    send_to_api("cmd", $cmd);
}

sub send_to_api {
    my $type = shift || "notification";

    my $command;
    if ($type eq "cmd") {
        $command = shift || return;
    }

    my ($readHandle,$writeHandle);
    pipe $readHandle, $writeHandle;
    $forked = 1;
    my $pid = fork();

    unless (defined($pid)) {
        Irssi::print("IrssiNotifier: couldn't fork - abort");
        close $readHandle; close $writeHandle;
        return 0;
    }

    if ($pid > 0) {
        close $writeHandle;
        Irssi::pidwait_add($pid);
        my $target = {fh => $$readHandle, tag => undef, type => $type};
        $target->{tag} = Irssi::input_add(fileno($readHandle), INPUT_READ, \&read_pipe, $target);
    } else {
        eval {
            my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
            my $proxy     = Irssi::settings_get_str('irssinotifier_https_proxy');

            if($proxy) {
                $ENV{https_proxy} = $proxy;
            }

            my $wget_cmd = "wget --tries=2 --timeout=10 --no-check-certificate -qO- /dev/null";
            my $api_url;
            my $data;

            if ($type eq 'notification') {
                $lastMsg = Irssi::strip_codes($lastMsg);

                encode_utf();
                $lastMsg    = encrypt($lastMsg);
                $lastNick   = encrypt($lastNick);
                $lastTarget = encrypt($lastTarget);

                $data = "--post-data=apiToken=$api_token\\&message=$lastMsg\\&channel=$lastTarget\\&nick=$lastNick\\&version=$VERSION";
                $api_url = "https://irssinotifier.appspot.com/API/Message";
            } elsif ($type eq 'cmd') {
                $command = encrypt($command);
                $data    = "--post-data=apiToken=$api_token\\&command=$command";
                $api_url = "https://irssinotifier.appspot.com/API/Command";
            }

            my $result =  `$wget_cmd $data $api_url`;
            if (($? >> 8) != 0) {
                # Something went wrong, might be network error or authorization issue. Probably no need to alert user, though.
                print $writeHandle "0 FAIL\n";
            } else {
                print $writeHandle "1 OK\n";
            }
        }; # end eval

        if ($@) {
            print $writeHandle "-1 IrssiNotifier internal error: $@\n";
        }

        close $readHandle; close $writeHandle;
        POSIX::_exit(1);
    }
    return 1;
}

sub encode_utf {
    # encode messages to utf8 if terminal is not utf8 (irssi's recode should be on)
    my $encoding;
    eval {
        require I18N::Langinfo;
        $encoding = lc(I18N::Langinfo::langinfo(I18N::Langinfo::CODESET()));
    };
    if ($encoding && $encoding !~ /^utf-?8$/i) {
        $lastMsg    = Encode::encode_utf8($lastMsg);
        $lastNick   = Encode::encode_utf8($lastNick);
        $lastTarget = Encode::encode_utf8($lastTarget);
    }
}

sub read_pipe {
    my $target = shift;
    my $readHandle = $target->{fh};

    my $output = <$readHandle>;
    chomp($output);

    close($target->{fh});
    Irssi::input_remove($target->{tag});
    $forked = 0;

    $output =~ /^(-?\d+) (.*)$/;
    my $ret = $1;
    $output = $2;

    if ($ret < 0) {
        Irssi::print($IRSSI{name} . ": Error: send crashed: $output");
    } elsif (!$ret) {
        #Irssi::print($IRSSI{name} . ": Error: send failed: $output");
    }

    if (Irssi::settings_get_bool('irssinotifier_clear_notifications_when_viewed')
        && $target->{type} eq 'notification') {
      $notifications_sent++;
    }

    check_delayQueue();
}

sub encrypt {
    my ($text) = @_ ? shift : $_;
    my $password = Irssi::settings_get_str('irssinotifier_encryption_password');

    chdir();
    my $fifo = ".irssinotifier_fifo";
    unlink $fifo; # just in case
    POSIX::mkfifo($fifo, 0700);

    my $password_pid = fork();

    if ($password_pid == 0) {
        # child process
        open (my $handle, "> $fifo"); # this line blocks until a reader (openssl) is spawned
        print $handle $password;
        close $handle;
        unlink $fifo;
        POSIX::_exit(1);
    }

    Irssi::pidwait_add($password_pid);

    my $pid = open2(my $out, my $in, qw(openssl enc -aes-128-cbc -salt -base64 -A -pass), "file:$fifo");

    print $in "$text ";
    close $in;

    local $/; # read full output at once
    my $result = readline $out;
    waitpid $pid, 0;
    waitpid $password_pid, 0;
    
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

    `openssl version`;
    if ($? != 0) {
        Irssi::print("IrssiNotifier: openssl not found.");
        return 0;
    }

    `wget --version`;
    if ($? != 0) {
        Irssi::print("IrssiNotifier: wget not found.");
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

    $notifications_sent = 0 unless (Irssi::settings_get_bool('irssinotifier_clear_notifications_when_viewed'));

    return 1;
}

sub check_delayQueue {
    if (scalar @delayQueue > 0) {
      my $item = shift @delayQueue;
      if (time - $item->{'added'} > 60) {
          check_delayQueue();
          return 0;
      } else {
          $lastMsg = $item->{'msg'};
          $lastNick = $item->{'nick'};
          $lastTarget = $item->{'target'};
          send_notification();
          return 0;
      }
    }
    return 1;
}

sub check_window_activity {
    return if (!$notifications_sent);

    my $act = 0;
    foreach (Irssi::windows()) {
        # data_level 3 means window has unseen hilight
        if ($_->{data_level} == 3) {
            $act++; last;
        }
    }

    if (!$act) {
        send_command("clearNotifications");
        $notifications_sent = 0;
    }
}

sub event_key_pressed {
    $lastKeyboardActivity = time;
}

my $screen_ls = `LC_ALL="C" screen -ls`;
if ($screen_ls !~ /^No Sockets found/s) {
    $screen_ls =~ /^.+\d+ Sockets? in ([^\n]+)\.\n.+$/s;
    $screen_socket_path = $1;
} else {
    $screen_ls =~ /^No Sockets found in ([^\n]+)\.\n.+$/s;
    $screen_socket_path = $1;
}

Irssi::settings_add_str('irssinotifier', 'irssinotifier_encryption_password', 'password');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_api_token', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_https_proxy', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_servers', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_channels', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_nicks', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_highlight_patterns', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_required_public_highlight_patterns', '');
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_ignore_active_window', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_away_only', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_screen_detached_only', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_clear_notifications_when_viewed', 0);
Irssi::settings_add_int('irssinotifier', 'irssinotifier_require_idle_seconds', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_enable_dcc', 1);

# these commands are renamed
Irssi::settings_remove('irssinotifier_ignore_server');
Irssi::settings_remove('irssinotifier_ignore_channel');

Irssi::signal_add('message irc action', 'public');
Irssi::signal_add('message public',     'public');
Irssi::signal_add('message private',    'private');
Irssi::signal_add('message join',       'joined');
Irssi::signal_add('message dcc',        'dcc');
Irssi::signal_add('message dcc action', 'dcc');
Irssi::signal_add('print text',         'print_text');
Irssi::signal_add('setup changed',      'are_settings_valid');
Irssi::signal_add('window changed',     'check_window_activity');
