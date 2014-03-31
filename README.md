<img src="docs/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives, functional programming and whatever makes life easier.

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=0.5-SNAPSHOT)](https://travis-ci.org/alexandru/monifu)

## Documentation

Available docs:

* [Atomic References](docs/atomic.md) 
* [Schedulers](docs/schedulers.md) and [Cancelables](docs/cancelables.md)

API documentation:

* [monifu-core](http://www.monifu.org/monifu-core/current/api/)
* [monifu-core-js](http://www.monifu.org/monifu-core-js/current/api/)

Release Notes:

* [Version 0.4 - March 31, 2014](/docs/release-notes/0.4.md)
* [Version 0.3 - March 27, 2014](/docs/release-notes/0.3.md)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.10 and Scala 2.11.0-RC3. Also cross-compiled to
the latest Scala.js (at the moment Scala.js 0.4.1).

Current stable release is: 0.4

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu-core" % "0.4"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %% "monifu-core-js" % "0.4"
```

### Trying out a Snapshot Release

To play with snapshot releases that aren't on Maven Central, you might want to add this 
resolver for Sonatype snapshots:

```scala
resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
```

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).