<img src="docs/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives, functional programming and whatever makes life easier.

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=master)](https://travis-ci.org/alexandru/monifu)

## Documentation

Available docs:

* [Atomic References](docs/atomic.md) 
* [Schedulers](docs/schedulers.md) and [Cancelables](docs/cancelables.md)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.10 and Scala 2.11.0-RC3. Also cross-compiled to the latest Scala.js (at the moment of writing Scala.js 0.4.1).

Current stable release is: 0.3

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu-core" % "0.3"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %% "monifu-core-js" % "0.3"
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