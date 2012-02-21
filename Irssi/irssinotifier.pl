#use strict;
#use warnings;

use Irssi;
use POSIX;
use vars qw($VERSION %IRSSI);
use File::Path qw(make_path);

$VERSION = "1.0";
%IRSSI = (
	authors	 => "Lauri \'murgo\' Härsilä",
	contact	 => "murgo\@iki.fi",
	name		=> "IrssiNotifier",
	description => "Send notifications about irssi highlights to server",
	license	 => "Public Domain",
	url		 => "http://github.com/murgo/irssinotifier",
	changed	 => "2012-02-18"
);

my $api_token;
my $encryption_password;

my $lastMsg;
my $lastServer;
my $lastNick;
my $lastAddress;
my $lastTarget;

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
	if(($dest->{level} & ($opt)) && ($dest->{level} & MSGLEVEL_NOHILIGHT) == 0) {
		hilite();
	}
}

sub hilite {
	if (!$api_token) {
		Irssi::print("Set api token to send andoid notifications: /irssinotifier apitoken [token]");
		return;
	}
	
	if ($encryption_password) {
		$lastMsg = encrypt($lastMsg);
		#$lastServer = encrypt($lastServer);
		$lastNick = encrypt($lastNick);
		#$lastAddress = encrypt($lastAddress);
		$lastTarget = encrypt($lastTarget);
	} else {
		Irssi::print("Set encryption password to send android notifications: /irssinotifier encryptionpassword [password");
	}

#	my $time = strftime(Irssi::settings_get_str('timestamp_format')." ", localtime);
	my $time = localtime;
	my $data = "--post-data=apiToken=$api_token&message=$lastMsg&channel=$lastTarget&nick=$lastNick&timestamp=$time";

	@args = ("wget", "--no-check-certificate", "-q", "-O", "/dev/null", $data, "https://irssinotifier.appspot.com/API/Message");
	system (@args);
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
	my $result = `echo $text| /usr/bin/openssl enc -aes-128-cbc -salt -base64 -A -k $encryption_password | tr -d '\n'`;
	$result =~ s/=//g;
	$result =~ s/\+/-/g;
	$result =~ s/\//_/g;
	chomp($result);
	return $result;
}
 
sub decrypt {
	my $text = $_[0];
	$text = sanitize $text;
	my $result = `echo $text| /usr/bin/openssl enc -aes-128-cbc -d -salt -base64 -A -k $encryption_password`;
	chomp($result);
	return $result;
}

sub print_help {
	Irssi::print("Usage: /irssinotifier operation parameter");
	Irssi::print("Operations:");
	Irssi::print("    ApiToken:           Shows current token, or changes token to parameter");
	Irssi::print("    EncyptionPassword:  Shows the current encryption password, or changes password to parameter");
}

sub load_settings {
	open SETTINGS, "$ENV{HOME}/.irssi/irssinotifier_settings";
	my @lines = <SETTINGS>;
	$api_token = $lines[0];
	$encryption_password = $lines[1];
	chomp($api_token);
	chomp($encryption_password);
	close SETTINGS;
}

sub save_settings {
	open SETTINGS, ">$ENV{HOME}/.irssi/irssinotifier_settings";
	print SETTINGS "$api_token\n$encryption_password";
	close SETTINGS;
}

sub cmd {
	my ($data, $server, $witem) = @_;
	my ($op, $param) = split " ", $data;
	
	$op = lc $op;
	if (!$op) {
		Irssi::print ("No operation given");
		print_help;
	} elsif ($op eq "apitoken") {
		if (!$param) {
			Irssi::print("Api token: $api_token");
		} else {
			$api_token = $param;
			Irssi::print("Api token set to: $api_token");
			save_settings;
		}
	} elsif ($op eq "encryptionpassword") {
		if (!$param) {
			Irssi::print("Encryption password: $encryption_password");
		} else {
			$encryption_password = $param;
			Irssi::print("Encryption password set to: $encryption_password");
			save_settings;
		}
	} else {
		Irssi::print ("Invalid operation: $op");
		print_help;
	}
}

load_settings;

Irssi::command_bind('irssinotifier', 'cmd');
Irssi::signal_add('message public', 'public');
Irssi::signal_add('message private', 'private');
Irssi::signal_add('print text', 'print_text');
