#!/bin/bash
set -e

echo "RELEASE"
sbt -mem 4096 release

VERSION=`git tag | grep "^v2." | sort | tail -n 1`
echo "Publishing for Scala 2.12.0-RC1"
echo

git checkout "$VERSION"
sbt -mem 4096 ";++2.12.0-RC1;clean;coreJVM/publishSigned;coreJS/publishSigned;scalaz72JVM/publishSigned;scalaz72JS/publishSigned"
git checkout master
