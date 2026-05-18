#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

if ! command -v unzip > /dev/null; then
    die "ERROR: unzip not found on your PATH."
fi

if [ "$APP_HOME" ] ; then
    CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
else
    CLASSPATH=gradle/wrapper/gradle-wrapper.jar
fi

exec "$JAVACMD" "$@" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
