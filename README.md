<img src="docs/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives and functional reactive programming. Targets both the JVM and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=v0.10.1)](https://travis-ci.org/alexandru/monifu)

## Documentation

Available docs:

* [Atomic References](docs/atomic.md) 
* [Schedulers](docs/schedulers.md) and [Cancelables](docs/cancelables.md)

API documentation:

* [monifu](http://www.monifu.org/monifu/current/api/)
* [monifu-js](http://www.monifu.org/monifu-js/current/api/)

Release Notes:

* [Version 0.10 - May 26, 2014](/docs/release-notes/0.10.md)
* [Version 0.9 - May 23, 2014](/docs/release-notes/0.9.md)
* [Version 0.8 - May 13, 2014](/docs/release-notes/0.8.md)
* [Version 0.7 - April 26, 2014](/docs/release-notes/0.7.md)
* [Older Releases](/docs/release-notes/)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.10 and Scala 2.11. Also cross-compiled to
the latest Scala.js (at the moment Scala.js 0.4.4). The targetted JDK version
for the published packages is version 6 (see 
[faq entry](https://github.com/alexandru/monifu/wiki/Frequently-Asked-Questions#what-javajdk-version-is-required)).

Current stable release is: `0.10.1`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "0.10.1"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %% "monifu-js" % "0.10.1"
```

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).
