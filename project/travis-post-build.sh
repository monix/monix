#!/usr/bin/env sh

cd `dirname $0`/..

if [ -z "$MAIN_SCALA_VERSION" ]; then
    >&2 echo "Environment MAIN_SCALA_VERSION is not set. Check .travis.yml."
    exit 1
fi

if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_SCALA_VERSION" ]; then
    echo "Uploading coverage for Scala $TRAVIS_SCALA_VERSION"
    exec sbt coverageAggregate coverageReport && codecov
else
    echo "Skipping uploading coverage for Scala $TRAVIS_SCALA_VERSION"
fi
