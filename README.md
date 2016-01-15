# Monix

<img src="https://raw.githubusercontent.com/wiki/monifu/monix/assets/monifu-square.png" align="right" width="280" />

Reactive Programming for Scala and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/monifu/monix.svg?branch=master)](https://travis-ci.org/monifu/monix)
[![Coverage Status](http://codecov.io/github/monifu/monix/coverage.svg?branch=master)](http://codecov.io/github/monifu/monix?branch=master)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.monifu/monifu_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.monifu/monifu_2.11)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monifu/monix?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**NOTE: renamed from Monifu, see [issue #91](https://github.com/monifu/monix/issues/91) for details.**

## Overview

Monix is a high-performance Scala / Scala.js library for
composing asynchronous and event-based programs using observable sequences
that are exposed as asynchronous streams, expanding on the
[observer pattern](https://en.wikipedia.org/wiki/Observer_pattern),
strongly inspired by [Reactive Extensions (Rx)](http://reactivex.io/),
but designed from the ground up  for back-pressure and made to cleanly interact
with Scala's standard library and compatible out-of-the-box with the
[Reactive Streams](http://www.reactive-streams.org/) protocol.

Highlights:

- zero dependencies
- clean and user-friendly API, with the observer interface using `Future` for back-pressure purposes
- Observable operators exposed in a way that's idiomatic to Scala
- compatible with for-comprehensions
- compatible with [Scalaz](https://github.com/scalaz/scalaz)
- designed to be completely asynchronous - Rx operators that are
  blocking or that are not compatible with back-pressure semantics  
  are not going to be supported
- does not depend on any particular mechanism for asynchronous
  execution and can be made to work with threads, actors, event loops,
  or whatnot, running perfectly both on top of the JVM or in Node.js
  or the browser
- really good test coverage as a project policy

## Usage

See **[monix-sample](https://github.com/monifu/monix-sample)** for
a project exemplifying Monix used both on the server and on the client.

Currently compiled for  *Scala 2.10.x*, *2.11.x* and *Scala.js 0.6.x*.
Monix's compatibility extends to the latest 2 major Scala versions and
to the latest major Scala.js version. In other words support for *Scala 2.10.x*
will be kept for as long as possible, but you should not rely on continued
support long after Scala 2.12 is out.

### Dependencies

The packages are published on Maven Central.

- Current stable release is: `1.0`

For the JVM:

```scala
libraryDependencies += "org.monifu" %% "monifu" % "1.0"
```

For targeting Javascript runtimes with Scala.js:

```scala
libraryDependencies += "org.monifu" %%% "monifu" % "1.0"
```

### Example

NOTE: this sample works for the 1.0 version.

In order for subscriptions to work, we need an implicit
`Scheduler` imported in our context. A `Scheduler` inherits from Scala's own [ExecutionContext](http://www.scala-lang.org/api/current/index.html#scala.concurrent.ExecutionContext)
and any `ExecutionContext` can be quickly converted into a `Scheduler`.
And then you're off ...

```scala
// scala.concurrent.ExecutionContext.Implicits.global
// is being used under the hood
import monifu.concurrent.Implicits.globalScheduler

// or we can simply convert our own execution context
// import play.api.libs.concurrent.Execution.Implicits.defaultContext
// implicit val scheduler = Scheduler(defaultContext)

import monifu.reactive._
import scala.concurrent.duration._

val subscription = Observable.intervalAtFixedRate(1.second)
  .take(10)
  .subscribe(x => println(x))
```

We can then try out more complex things:

```scala
import monifu.reactive._
import monifu.concurrent.Implicits.globalScheduler
import play.api.libs.ws._

// emits an auto-incremented number, every second
Observable.interval(1.second)
  // drops the items emitted over the first 5 secs
  .dropByTimespan(5.seconds)
  // takes the first 100 emitted events  
  .take(100)
  // per second, makes requests and concatenates the results
  .flatMap(x => WS.request(s"http://some.endpoint.com/request?tick=$x").get())
  // filters only valid responses
  .filter(response => response.status == 200)
  // samples by 3 seconds, repeating previous results in case of nothing new
  .sampleRepeated(3.seconds)
  // processes response, selecting the body
  .map(response => response.body)
  // creates subscription, foreach response print it
  .foreach(x => println(x))
```

## Documentation

NOTE: The documentation is a work in progress.
API Documentation:

- [1.0](http://monix.io/api/1.0/)

## Maintainers

The current maintainers (people who can help you) are:

- Alexandru Nedelcu ([@alexandru](https://github.com/alexandru))
- Andrei Oprisan ([@aoprisan](https://github.com/aoprisan))

## Contributing

The Monix project welcomes contributions from anybody wishing to participate.
All code or documentation that is provided must be licensed with the same
license that Monix is licensed with (Apache 2.0, see LICENSE.txt).

People are expected to follow the [Typelevel Code of Conduct](http://typelevel.org/conduct.html)
when discussing Monix on the Github page, Gitter channel, or other venues.

We hope that our community will be respectful, helpful, and kind. If you find
yourself embroiled in a situation that becomes heated, or that fails to live up
to our expectations, you should disengage and contact one of the project maintainers
in private. We hope to avoid letting minor aggressions and misunderstandings
escalate into larger problems.

Feel free to open an issue if you notice a bug, have an idea for a feature, or
have a question about the code. Pull requests are also gladly accepted. For more information,
check out the [contributor guide](CONTRIBUTING.md).

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/monifu/monix/assets/yklogo.png" align="right" />
YourKit supports the Monix project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

<img src="https://raw.githubusercontent.com/wiki/monifu/monix/assets/logo-eloquentix@2x.png" align="right" width="130" />

Development of Monix has been initiated by [Eloquentix](http://eloquentix.com/)
engineers, with Monix being introduced at E.ON Connecting Energies,
powering the next generation energy grid solutions.
