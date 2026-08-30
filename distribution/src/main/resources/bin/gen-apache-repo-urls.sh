#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

shopt -s expand_aliases

TIKA_VERSION=3.3.2

if [ ! -f $DRAT_HOME/lib/tika-app-$TIKA_VERSION.jar ]; then
    pushd $DRAT_HOME/lib
    # https, not http: repo1 has refused plain HTTP since 2020, so the old
    # fetch failed silently and left the alias pointing at a missing jar.
    curl -O https://repo1.maven.org/maven2/org/apache/tika/tika-app/$TIKA_VERSION/tika-app-$TIKA_VERSION.jar
    popd
fi

alias tika="java -jar $DRAT_HOME/lib/tika-app-$TIKA_VERSION.jar"
REPOS=`tika -t "https://gitbox.apache.org/repos/asf" | grep \.git | cut -d\? -f2 | cut -d\; -f1 | cut -d\= -f2 | cut -d\" -f1 | cut -d"." -f1 | sort | uniq`
for REPO in $REPOS; do
    echo $REPO.git | sed -e 's/^[[:space:]]*//' ;
done

