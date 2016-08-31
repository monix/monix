#!/bin/sh

cd `dirname $0`/..

if [ -z "$MAIN_SCALA_VERSION" ]; then
    >&2 echo "Environment MAIN_SCALA_VERSION is not set. Check .travis.yml."
    exit 1
elif [ -z "$TRAVIS_SCALA_VERSION" ]; then
    >&2 echo "Environment TRAVIS_SCALA_VERSION is not set."
    exit 1
else
    echo "TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION"
    echo "MAIN_SCALA_VERSION=$MAIN_SCALA_VERSION"
    echo "EXPERIMENTAL_SCALA_VERSION=$EXPERIMENTAL_SCALA_VERSION"
fi

if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_SCALA_VERSION" ]; then
    echo "Testing with coverage for Scala $MAIN_SCALA_VERSION"
    exec sbt -Dsbt.profile=coverage ";clean;coverage;test:compile;test"
elif [ "$TRAVIS_SCALA_VERSION" = "$EXPERIMENTAL_SCALA_VERSION" ]; then
    echo "Testing without coverage for experimental Scala $TRAVIS_SCALA_VERSION"
    exec sbt ";++$TRAVIS_SCALA_VERSION;clean;coreJVM/test:compile;coreJS/test:compile;coreJVM/test;coreJS/test"
else
    echo "Testing without coverage for overridden Scala $TRAVIS_SCALA_VERSION"
    exec sbt ";++$TRAVIS_SCALA_VERSION;clean;test:compile;test"
fi
