#!/bin/sh

cd `dirname $0`/..

if [ -z "$MAIN_SCALA_VERSION" ]; then
    >&2 echo "Environment MAIN_SCALA_VERSION is not set. Check .travis.yml."
    exit 1
fi

if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_SCALA_VERSION" ]; then
    echo "Testing with coverage for Scala $MAIN_SCALA_VERSION"
    exec sbt ";clean;coverage;test:compile;test"
else
    echo "Testing without coverage for overriden Scala $TRAVIS_SCALA_VERSION"
    exec sbt ";++$TRAVIS_SCALA_VERSION;clean;test:compile;test"
fi
