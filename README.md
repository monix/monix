<img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/monifu-square.png" align="right" width="280" />

Idiomatic Reactive Extensions for Scala and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=master)](https://travis-ci.org/alexandru/monifu)
[![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=v1.0)](https://travis-ci.org/alexandru/monifu)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/alexandru/monifu?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Overview

Monifu is a high-performance Scala / Scala.js library for
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

See **[monifu-sample](https://github.com/alexandru/monifu-sample)** for
a project exemplifying Monifu used both on the server and on the client.

### Compatibility

Currently compiled for  *Scala 2.10.x*, *2.11.x* and *Scala.js 0.6.x*. 
Monifu's compatibility extends to the latest 2 major Scala versions and 
to the latest major Scala.js version. In other words support for *Scala 2.10.x* 
will be kept for as long as possible, but you should not rely on continued 
support long after Scala 2.12 is out.

### Dependencies

The packages are published on Maven Central. 

- Current stable release is: `1.0`

#### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "1.0"
```

#### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %%% "monifu" % "1.0"
```

### Example

In order for subscriptions to work, we need an implicit
[Scheduler](shared/src/main/scala/monifu/concurrent/Scheduler.scala#L33) imported in our
context. A `Scheduler` inherits from Scala's own [ExecutionContext](http://www.scala-lang.org/api/current/index.html#scala.concurrent.ExecutionContext)
and any `ExecutionContext` can be quickly converted into a `Scheduler`.
And then you're off ...

```scala
// scala.concurrent.ExecutionContext.Implicits.global
// is being used under the hood
import monifu.concurrent.Implicits.globalScheduler

// or we can simply convert our own execution context
// import play.api.libs.concurrent.Execution.Implicits.defaultContext
// implicit val scheduler = Scheduler(defaultContext)

import concurrent.duration._
import monifu.reactive._

val subscription = Observable.intervalAtFixedRate(1.second)
  .take(10)
  .subscription(x => println(x))
```

We can then try out more complex things:

```scala
import monifu.concurrent.Implicits.globalScheduler
import play.api.libs.ws._
import monifu.reactive._

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

There's actually a lot more to Monifu.

## Documentation

NOTE: The documentation is a work in progress.

API Documentation:

- [1.0](http://monifu.org/api/1.0/)

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/yklogo.png" align="right" />
YourKit supports the Monifu project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

<img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/logo-eloquentix@2x.png" align="right" width="130" />

Development of Monifu has been initiated by [Eloquentix](http://eloquentix.com/)
engineers, with Monifu being introduced at E.ON Connecting Energies,
powering the next generation energy grid solutions.
