<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/monifu-square.png" align="right" width="280" />

Idiomatic Reactive Extensions for Scala and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/monifu/monifu.png?branch=master)](https://travis-ci.org/monifu/monifu)
[![Build Status](https://travis-ci.org/monifu/monifu.png?branch=v1.0-RC1)](https://travis-ci.org/monifu/monifu)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monifu/monifu?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

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

See **[monifu-sample](https://github.com/monifu/monifu-sample)** for
a project exemplifying Monifu used both on the server and on the client.

The packages are published on Maven Central. Compiled for Scala 2.11.5
and Scala.js 0.6.0. Older versions are no longer supported.

- Current stable release is: `1.0-RC1`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "1.0-RC1"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %%% "monifu" % "1.0-RC1"
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

The documentation is a work in progress.

[API Documentation](http://monifu.org/api/current/)

## Design Concepts
A key design decision for Monifu can best be described with an analogy. A stream of information is like a river. Does
the river care who observes it or who drinks from it? No, it doesn’t. Sometimes you need to share the source between
multiple listeners, sometimes you want to create new sources for each listener. But the listener shouldn’t care what
sort of producer it has on its hands or vice-versa. We feel that reasoning about graphs and making those graphs explicit
doesn’t lead to better code.

### Hot and Cold
In Monifu / Rx you’ve got hot observables (hot data sources shared between an unlimited number of subscribers) and cold
observables (each subscriber gets its very own private data source). You can also convert any cold data source into a
hot one by using the multicast operator, in combination with Subjects that dictate behavior (e.g. Publish, Behavior,
Async or Replay). The [ConnectableObservable]() is meant for hot data sources. In our sample above, we are using as dad
as da sdshare(), an operator that transforms our data source into a hot one and then applies reference counting on its
subscribers to know when to stop it. This is what encapsulation is all about.

### High-level comparison to Akka Streams
In Akka Streams the sources have a “single output” port and what you do is you build “flow graphs” and sinks. Akka
Streams is thus all about modeling how streams are split. They call it “explicit fan-out” and it’s a design choice.
Monifu take the opinion that this is an encapsulation leak that makes things way more complicated than they should be
and defeats the purpose of using a library for streams manipulation in the first place. In Rx (Rx.NET / RxJava / Monifu)
terms, this is like having single-subscriber `Observables` and then working with `Subjects` (which is both a listener
and a producer) and people that have used Rx know that working with Subjects sucks and when you do, you usually
encapsulate it really, really well.

Monifu’s implementation is conceptually elegant. You’ve got the `Scheduler` that is used to execute tasks (an evolved
ExecutionContext), the `Observable` interface which is the producer, characterized solely by its onSubscribe function,
then you’ve got the `Observer` which represents the consumer and has the back-pressure protocol baked in its API, the
`Subject` that is both a producer and a consumer, the `Channel` which represents a way to build Observables in an
imperative way without back-pressure concerns and the `ConnectableObservable`, which represents hot data-sources that
are shared between multiple subscribers. Monifu’s internals are self-explanatory and (I hope) a joy to go through.

### Back-Pressure
Monifu will never, ever blow out the stack. For example, for `zip`, we take the value of the first source that emits,
then we apply back-pressure until the second one emits.
### Schedulers
Schedulers are enhanced execution contexts (the Scheduler inherits from ExecutionContext). They can be anything you
want, as long as they give you the possibility of scheduling tasks for asynchronous execution. Monifu always starts to
execute tasks asynchronously and will not freeze the current thread (except in cases where the operation is really fast,
like Observable.unit).
### `map`
The implementation of "map" is not doing asynchronous execution, as "map" is inherently synchronous. This doesn't mean
that all events passing through "map" are handled on the same thread, this being the responsibility of the upstream
source that is pushing events through "map". On the other hand, there are operations that are inherently asynchronous,
like concat/concatMap and some operations are inherently concurrent, like "zip" or merge/mergeMap. It's there that the
Scheduler is being used. You will also not see schedulers being taken as parameters by the Observable operators because
Observables are pure until you "subscribe". That's the sane way to do it and it's one thing I don't like about Future
sometimes. It also makes Observable compatible with any Monad or Applicative Functor implementation you want. And before
you ask, flatMap also behaves as you'd want it to behave.
### Observables Raison d'etre 
The whole purpose, the raison d'etre of Observables is to help you handle concurrency with higher level abstractions.
Basically if you like Future, then Observable is like a Future on illegal steroids. Our belief is that the Rx approach
coupled with my improvements lends itself to better parallelism.
### Channel
Channel performs buffering. The difference between Monifu and RxJava is that `Subjects` are bound by the back-pressure
contract that is baked in the Observer interface, so you cannot use Subjects the way you'd use them in RxJava. There is
no such thing as "back-pressure not supported" in Monifu - if a piece of functionality cannot support back-pressure and
cannot be redefined in a way that does, then that feature does not get implemented (the groupBy operator almost didn't
make it).
### Status and Example Use Case
Monifu is being used for in production. An example is gathering signals coming from industrial machines, from multiple
sources and then analyzing them in real-time. These signals are used to model state-machines that reflect the behavior
of those machines on a per-machine level, creating streams of higher-level state updates that then get signaled to other
micro-services, like another component that monitors and controls those industrial machines as an aggregate, like it's a
big virtual appliance. In the process we're doing plenty of splitting and merging. It's not automatic though. And for
example Monifu doesn't handle the actual communication between the nodes (Monifu is not even being used in all
components). For the actual communication we are still using Akka actors or WebSocket connections, though Monifu does
handle the back-pressure side according to the reactive streams protocol and also the buffering.
### Example
Here is a [sample](https://github.com/monifu/monifu-sample) that shows 4 server-side streams being merged in one graph
on the client-side, with the server-side implementation being based on the Akka actors support in Play framework,
exposing Monifu Observables on that WebSocket, then consuming them client-side. But then, after you take care of the
protocol for that communication between nodes (which might very well be Akka-based), you don't care from where that data
is comming from and then you can merge it and split it and send it somewhere else.


## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/yklogo.png" align="right" />
YourKit supports the Monifu project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/logo-eloquentix@2x.png" align="right" width="130" />

Development of Monifu has been initiated by [Eloquentix](http://eloquentix.com/)
engineers, with Monifu being introduced at E.ON Connecting Energies,
powering the next generation energy grid solutions.
