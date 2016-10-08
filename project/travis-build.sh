#!/bin/bash
set -e

cd `dirname $0`/..

if [ -z "$MAIN_SCALA_VERSION" ]; then
    >&2 echo "Environment MAIN_SCALA_VERSION is not set. Check .travis.yml."
    exit 1
elif [ -z "$TRAVIS_SCALA_VERSION" ]; then
    >&2 echo "Environment TRAVIS_SCALA_VERSION is not set."
    exit 1
else
    echo
    echo "TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION"
    echo "MAIN_SCALA_VERSION=$MAIN_SCALA_VERSION"
    echo "EXPERIMENTAL_SCALA_VERSION=$EXPERIMENTAL_SCALA_VERSION"
fi

function buildJVM {
    INIT=";++$TRAVIS_SCALA_VERSION;clean"
    COMPILE="coreJVM/test:compile;scalaz72JVM/test:compile;tckTests/test:compile"
    TEST="coreJVM/test;scalaz72JVM/test;tckTests/test"

    if [ "$TRAVIS_SCALA_VERSION" = "$EXPERIMENTAL_SCALA_VERSION" ]; then
        EXTRA_COMPILE=""
        EXTRA_TEST=""
    else
        EXTRA_COMPILE=";catsJVM/test:compile"
        EXTRA_TEST=";catsJVM/test"
    fi

    if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_SCALA_VERSION" ]; then
        COMMAND="$INIT;coverage;$COMPILE$EXTRA_COMPILE;$TEST$EXTRA_TEST"
        echo "Executing JVM tests (with coverage): sbt -Dsbt.profile=coverage $COMMAND"
        sbt -Dsbt.profile=coverage "$COMMAND"
    else
        COMMAND="$INIT;$COMPILE$EXTRA_COMPILE;$TEST$EXTRA_TEST"

        echo
        echo "Executing JVM tests: sbt $COMMAND"
        echo
        sbt "$COMMAND"
    fi
}

function buildJS {
    INIT=";++$TRAVIS_SCALA_VERSION;clean"
    COMPILE="coreJS/test:compile;scalaz72JS/test:compile"
    TEST="coreJS/test;scalaz72JS/test"

    if [ "$TRAVIS_SCALA_VERSION" = "$EXPERIMENTAL_SCALA_VERSION" ]; then
        EXTRA_COMPILE=""
        EXTRA_TEST=""
    else
        EXTRA_COMPILE=";catsJS/test:compile"
        EXTRA_TEST=";catsJS/test"
    fi

    COMMAND="$INIT;$COMPILE$EXTRA_COMPILE;$TEST$EXTRA_TEST"

    echo
    echo "Executing JS tests: sbt $COMMAND"
    echo
    sbt "$COMMAND"
}

# Execute everything
buildJS && buildJVM