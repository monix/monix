#!/usr/bin/env sh

cd `dirname $0`/..
REQUIRED_VERSION="$1"

if [ $# -eq 0 ]; then
    echo "ERROR: Scala version is required as argument!"
    exit 1
elif [ "$TRAVIS_SCALA_VERSION" = "$REQUIRED_VERSION" ]; then
    echo "Uploading coverage for Scala $REQUIRED_VERSION"
    exec sbt coverageAggregate coverageReport && codecov
else
    echo "Skipping uploading coverage for Scala $TRAVIS_SCALA_VERSION"
fi