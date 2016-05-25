#!/bin/sh

cd `dirname $0`/..
MAIN_VERSION="2.11.8"

if [ "$TRAVIS_SCALA_VERSION" = "$MAIN_VERSION" ]; then
    echo "Testing with coverage for Scala $MAIN_VERSION"
    exec sbt clean coverage test
else
    echo "Testing without coverage for overriden Scala $TRAVIS_SCALA_VERSION"
    exec sbt ++$TRAVIS_SCALA_VERSION clean test
fi