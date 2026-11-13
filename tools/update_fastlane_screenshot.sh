#! /bin/sh
url_start_screen='https://github.com/zoff99/satstat/releases/download/nightly/android_screen01_33.png'

_HOME2_=$(dirname $0)
export _HOME2_
_HOME_=$(cd $_HOME2_;pwd)
export _HOME_

basedir="$_HOME_""/../"

cd "$basedir"
wget "$url_start_screen" -O ./fastlane/metadata/android/en-US/images/phoneScreenshots/010.png
