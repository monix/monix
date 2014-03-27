<img src="docs/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives, functional programming and whatever makes life easier.

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=master)](https://travis-ci.org/alexandru/monifu)

## Documentation

Available docs:

* [Atomic References](docs/atomic.md)

## Usage

The first stable release is still pending. Compiled for Scala 2.10 and will be cross-compiled for Scala 2.11 as
soon as the general release is available. Also cross-compiled to the latest Scala.js (at the moment of writing
 Scala.js 0.4.1).

The only sub-project is right now `monifu-core`, but `monifu-rx`, `monifu-iteratees` and probably others will happen.

### For the JVM

```scala
resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += "org.monifu" %% "monifu-core" % "0.3-SNAPSHOT"
```

### For in-browser Scala.js

```scala
resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += "org.monifu" %% "monifu-core-js" % "0.3-SNAPSHOT"
```

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).