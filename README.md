<img src="docs/assets/monifu.png" align="right" />

Branch for exploring an implementation for Reactive Extensions (Rx).

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=rx)](https://travis-ci.org/alexandru/monifu)

## Documentation

Available docs:

* [Atomic References](docs/atomic.md) 
* [Schedulers](docs/schedulers.md) and [Cancelables](docs/cancelables.md)

API documentation:

* [monifu-core](http://www.monifu.org/monifu-core/current/api/)
* [monifu-core-js](http://www.monifu.org/monifu-core-js/current/api/)

Release Notes:

* [Version 0.8 - May 13, 2014](/docs/release-notes/0.8.md)
* [Version 0.7 - April 26, 2014](/docs/release-notes/0.7.md)
* [Version 0.6 - April 23, 2014](/docs/release-notes/0.6.md)
* [Version 0.5 - April 10, 2014](/docs/release-notes/0.5.md)
* [Version 0.4 - March 31, 2014](/docs/release-notes/0.4.md)
* [Version 0.3 - March 27, 2014](/docs/release-notes/0.3.md)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.10 and Scala 2.11. Also cross-compiled to
the latest Scala.js (at the moment Scala.js 0.4.3). The targetted JDK version
for the published packages is version 6 (see 
[faq entry](https://github.com/alexandru/monifu/wiki/Frequently-Asked-Questions#what-javajdk-version-is-required)).

Current stable release is: `0.8.0`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu-core" % "0.8.0"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %% "monifu-core-js" % "0.8.0"
```
>>>>>>> master

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).
