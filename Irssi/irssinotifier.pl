use Irssi;
use POSIX;
use vars qw($VERSION %IRSSI);
use File::Path qw(make_path);

$VERSION = "0.01";
%IRSSI = (
    authors     => "Lauri \'murgo\' Härsilä",
    contact     => "murgo\@iki.fi",
    name        => "notify",
    description => "",
    license     => "",
    url         => "",
    changed     => "Thu Jun 30 12:00:00 BST 2011"
);

$token = "1980cb66-b8a9-4fda-83a1-c9899944a024";

sub hilite {
    my ($dest, $asd, $text) = @_;

    my $opt = MSGLEVEL_HILIGHT|MSGLEVEL_MSGS;

    if(
        ($dest->{level} & ($opt)) &&
        ($dest->{level} & MSGLEVEL_NOHILIGHT) == 0
    ) {
        if ($dest->{level} & MSGLEVEL_PUBLIC) {
            $text = $dest->{target}.": ".$text;
        }
        $text = strftime(
            Irssi::settings_get_str('timestamp_format')." ",
            localtime
        ).$text;

        $text =~ s/`//g;
        $text =~ s/|//g;
        $text =~ s/;//g;

        $result = `wget --no-check-certificate -qO- --post-data="apiToken=$token&message=$text" https://irssinotifier.appspot.com/Api/Message`;

    }
}

Irssi::signal_add('print text', 'hilite');
