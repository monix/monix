#!/usr/bin/env bash

set -e

cd "$(dirname "$0")/../.." || exit

echo
echo "--------------------------------"
echo "SCALA_VERSION   = $SCALA_VERSION"
echo "SBT_COMMAND     = $SBT_COMMAND"
echo "PLATFORM        = $PLATFORM"
echo "--------------------------------"
echo

if [[ -z "$SCALA_VERSION" ]]; then
    echo "ERROR: environment value SCALA_VERSION is not set!" 1>&2
    exit 1
fi

if [[ -z "$SBT_COMMAND" ]]; then
    echo "ERROR: environment value SBT_COMMAND is not set!" 1>&2
    exit 1
fi

sbt -J-Xmx6144m ++$SCALA_VERSION $SBT_COMMAND
