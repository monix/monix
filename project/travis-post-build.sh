#!/usr/bin/env sh

cd `dirname $0`/..
MAIN_VERSION="2.11.8"

if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_VERSION" ]; then
    echo "Uploading coverage for Scala $TRAVIS_SCALA_VERSION"
    exec sbt coverageAggregate coverageReport && codecov
else
    echo "Skipping uploading coverage for Scala $TRAVIS_SCALA_VERSION"
fi