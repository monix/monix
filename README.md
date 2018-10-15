# Monix

<img src="https://monix.io/public/images/monix-logo.png?ts=20161024" align="right" width="280" />

Asynchronous, Reactive Programming for Scala and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/monix/monix.svg?branch=master)](https://travis-ci.org/monix/monix)
[![Coverage Status](https://codecov.io/gh/monix/monix/coverage.svg?branch=master)](https://codecov.io/gh/monix/monix?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/io.monix/monix_2.12.svg)](http://search.maven.org/#search|gav|1|g%3A%22io.monix%22%20AND%20a%3A%22monix_2.12%22)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monix/monix?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Overview

Monix is a high-performance Scala / Scala.js library for composing asynchronous,
event-based programs.

It started as a proper implementation of [ReactiveX](http://reactivex.io/),
with stronger functional programming influences and designed from the ground up
for  back-pressure and made to cleanly interact with Scala's standard library,
compatible out-of-the-box with the [Reactive Streams](http://www.reactive-streams.org/)
protocol. It then expanded to include abstractions for suspending side effects
and for resource handling, being one of the parents and implementors of
[cats-effect](https://typelevel.org/cats-effect/).

<a href="https://typelevel.org/"><img src="https://monix.io/public/images/typelevel.png" width="150" style="float:right;" align="right" /></a>

A [Typelevel project](http://typelevel.org/projects/), Monix proudly
exemplifies pure, typeful, functional programming in Scala, while making no
compromise on performance.

Highlights:

- exposes the kick-ass `Observable`, `Iterant`, `Task` and `Coeval` data types,
  along with all the support they need
- modular, only use what you need
- designed for true asynchronicity, running on both the
  JVM and [Scala.js](http://scala-js.org)
- really good test coverage, code quality and API documentation
  as a primary project policy

## Usage

See **[monix-sample](https://github.com/monix/monix-sample)** for
a project exemplifying Monix used both on the server and on the client.

### Dependencies

The packages are published on Maven Central.

- Stable release: `2.3.3`
- Current release candidate: `3.0.0-RC1`
  (compatible with Cats-Effect 0.10)
- Super experimental version: `3.0.0-RC2-c84f485`
  (compatible with Cats-Effect 1.0.0)

For the 3.x series (that works with Cats `1.x` and Cats-Effect `0.10`):

```scala
libraryDependencies += "io.monix" %% "monix" % "3.0.0-RC1"
```

For the 2.x series:

```scala
libraryDependencies += "io.monix" %% "monix" % "2.3.3"
```

### Sub-projects

Monix 3.x is modular by design, so you can pick and choose:

- `monix-execution` exposes the low-level execution environment, or
  more precisely `Scheduler`, `Cancelable`, `Atomic` and
  `CancelableFuture`; depends on Cats 1.x and Cats-Effect
- `monix-eval` exposes `Task`, `Coeval`;
  depends on `monix-execution`
- `monix-reactive` exposes `Observable` for modeling reactive,
  push-based streams with back-pressure; depends on `monix-eval`
- `monix-tail` exposes `Iterant` streams for purely functional pull
  based streaming; depends on `monix-eval` and makes heavy use of
  Cats-Effect
- `monix` provides all of the above

### Versioning Scheme

The versioning scheme follows the
[Semantic Versioning](http://semver.org/) (semver) specification,
meaning that stable versions have the form `$major.$minor.$patch`,
such that:

1. `$major` version updates make binary incompatible API changes
2. `$minor` version updates adds functionality in a
   backwards-compatible manner, and
3. `$patch` version updates makes backwards-compatible bug fixes

For development snapshots may be published to Sonatype at any time.
Development versions have the form: `$major.$minor.$patch-$hash`
(example `3.0.0-d3288bb`).

The `$hash` is the 7 character git hash prefix of the commit from
which the snapshot was published.  Thus, "snapshots" can be used as
repeatable upstream dependencies if you're feeling courageous.  NO
GUARANTEE is made for upgrades of development versions, use these at
your own risk.

## Documentation

See:

- Website: [Monix.io](https://monix.io/)
- [Documentation for 3.x](https://monix.io/docs/3x/)
- [Documentation for 2.x](https://monix.io/docs/2x/)
- [Presentations](https://monix.io/presentations/)

API Documentation:

- [3.0](https://monix.io/api/3.0/)
- [2.3](https://monix.io/api/2.3/)
- [1.2](https://monix.io/api/1.2/)

([contributions are welcome](https://github.com/monix/monix.io))

Related:

- [Typelevel Cats](https://typelevel.org/cats/)
- [Typelevel Cats-Effect](https://typelevel.org/cats-effect/)

## Contributing

The Monix project welcomes contributions from anybody wishing to
participate.  All code or documentation that is provided must be
licensed with the same license that Monix is licensed with (Apache
2.0, see LICENSE.txt).

People are expected to follow the
[Scala Code of Conduct](./CODE_OF_CONDUCT.md) when
discussing Monix on GitHub, Gitter channel, or other venues.

Feel free to open an issue if you notice a bug, have an idea for a
feature, or have a question about the code. Pull requests are also
gladly accepted. For more information, check out the
[contributor guide](CONTRIBUTING.md).

If you'd like to donate in order to help with ongoing maintenance:

<a href="https://www.patreon.com/bePatron?u=6102596"><img label="Become a Patron!" src="https://c5.patreon.com/external/logo/become_a_patron_button@2x.png" height="40" /></a>

## License

All code in this repository is licensed under the Apache License,
Version 2.0.  See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/monix/monix/assets/yklogo.png" align="right" />

YourKit supports the Monix project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

<img src="https://raw.githubusercontent.com/wiki/monix/monix/assets/logo-eloquentix@2x.png" align="right" width="130" />

Development of Monix has been initiated by [Eloquentix](http://eloquentix.com/)
engineers, with Monix being introduced at E.ON Connecting Energies,
powering the next generation energy grid solutions.
