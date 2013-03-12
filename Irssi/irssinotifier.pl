use strict;

use Irssi;
use IPC::Open2 qw(open2);
use POSIX;
use Encode;
use vars qw($VERSION %IRSSI);

$VERSION = "14";
%IRSSI   = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "IrssiNotifier",
    description => "Send notifications about irssi highlights to server",
    license     => "Apache License, version 2.0",
    url         => "http://irssinotifier.appspot.com",
    changed     => "2012-10-28"
);

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastAddress;
my $lastTarget;
my $lastWindow;
my $lastKeyboardActivity = time;

my $forked;
my @delay_queue = ();

sub private {
    my ( $server, $msg, $nick, $address ) = @_;
    $lastServer  = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = "!PRIVATE";
    $lastWindow  = $nick;
}

sub public {
    my ( $server, $msg, $nick, $address, $target ) = @_;
    $lastServer  = $server;
    $lastMsg     = $msg;
    $lastNick    = $nick;
    $lastAddress = $address;
    $lastTarget  = $target;
    $lastWindow  = $target;
}

sub print_text {
    my ($dest, $text, $stripped) = @_;
    
    if (should_send_notification($dest))
    {
        send_notification();
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

    # If specified, require a pattern to be matched before highlighting public
    # messages
    my $required_public_highlight_pattern_string = Irssi::settings_get_str("irssinotifier_required_public_highlight_patterns");
    if ($required_public_highlight_pattern_string ne '' && ($dest->{level} & MSGLEVEL_PUBLIC)) {
        my @required_patterns = split(/ /, $required_public_highlight_pattern_string);
        if (!(grep { $lastMsg =~ /$_/i } @required_patterns)) {
            return 0; # Required pattern not matched
        }
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

sub send_notification {
    if ($forked) {
      if (scalar @delay_queue < 10) {
        push @delay_queue, {
                            'msg' => $lastMsg,
                            'nick' => $lastNick,
                            'target' => $lastTarget,
                            'added' => time,
                            };
      } else {
        Irssi::print("IrssiNotifier: previous send still in progress and queue full, skipping notification");
      }
      return 0;
    }

    my ($rh,$wh);
    pipe $rh, $wh;
    $forked = 1;
    my $pid = fork();

    unless (defined($pid)) {
      Irssi::print("IrssiNotifier: couldn't fork - abort");
      close $rh; close $wh;
      return 0;
    }

    if ($pid > 0) {
      close $wh;
      Irssi::pidwait_add($pid);
      my $target = {fh => $$rh, tag => undef};
      $target->{tag} = Irssi::input_add(fileno($rh), INPUT_READ, \&read_pipe, $target);
    } else {
      eval {
        my $api_token = Irssi::settings_get_str('irssinotifier_api_token');
    
        my $encryption_password = Irssi::settings_get_str('irssinotifier_encryption_password');
        $lastMsg    = encrypt(Encode::encode_utf8(Irssi::strip_codes($lastMsg)));
        $lastNick   = encrypt(Encode::encode_utf8($lastNick));
        $lastTarget = encrypt(Encode::encode_utf8($lastTarget));

        my $data = "--post-data=apiToken=$api_token\\&message=$lastMsg\\&channel=$lastTarget\\&nick=$lastNick\\&version=$VERSION";
        my $result = `wget --tries=1 --timeout=5 --no-check-certificate -qO- /dev/null $data https://irssinotifier.appspot.com/API/Message`;
        if (($? >> 8) != 0) {
          # Something went wrong, might be network error or authorization issue. Probably no need to alert user, though.
          print $wh "0 FAIL\n";
        } else {
          print $wh "1 OK\n";
        }
      }; # end eval

      if ($@) {
        print $wh "-1 IrssiNotifier internal error: $@\n";
      }

      close $rh; close $wh;
      POSIX::_exit(1);
    }
    return 1;
}

sub read_pipe {
    my $target = shift;
    my $rh = $target->{fh};

    my $output = <$rh>;
    chomp($output);

    close($target->{fh});
    Irssi::input_remove($target->{tag});
    $forked = 0;

    check_delay_queue();

    $output =~ /^(-?\d+) (.*)$/;
    my $ret = $1;
    $output = $2;

    if ($ret < 0) {
      Irssi::print($IRSSI{name} . ": Error: send crashed: $output");
      return 0;
    }

    if (!$ret) {
      #Irssi::print($IRSSI{name} . ": Error: send failed: $output");
      return 0;
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

    my $tmp = $/;
    undef $/;    # read full output at once
    my $result = readline $out;
    waitpid $pid, 0;
    $/ = $tmp;

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

    return 1;
}

sub check_delay_queue {
    if (scalar @delay_queue > 0) {
      my $item = shift @delay_queue;
      if (time - $item->{'added'} > 60) {
          check_delay_queue();
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

sub event_key_pressed {
    $lastKeyboardActivity = time;
}

Irssi::settings_add_str('irssinotifier', 'irssinotifier_encryption_password', 'password');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_api_token', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_servers', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_channels', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_nicks', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_ignored_highlight_patterns', '');
Irssi::settings_add_str('irssinotifier', 'irssinotifier_required_public_highlight_patterns', '');
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_ignore_active_window', 0);
Irssi::settings_add_bool('irssinotifier', 'irssinotifier_away_only', 0);
Irssi::settings_add_int('irssinotifier', 'irssinotifier_require_idle_seconds', 0);

# these commands are renamed
Irssi::settings_remove('irssinotifier_ignore_server');
Irssi::settings_remove('irssinotifier_ignore_channel');

Irssi::signal_add( 'message irc action', 'public');
Irssi::signal_add( 'message public',     'public');
Irssi::signal_add( 'message private',    'private');
Irssi::signal_add( 'print text',         'print_text');
Irssi::signal_add( 'setup changed',      'are_settings_valid');
