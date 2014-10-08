<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives and functional reactive programming. Targets both the JVM and [Scala.js](http://www.scala-js.org/) (for targetting Javascript see its [Monifu.js](https://github.com/monifu/monifu.js)).

[![Build Status](https://travis-ci.org/monifu/monifu.png?branch=v0.14.0.RC1)](https://travis-ci.org/monifu/monifu)

## Teaser

[Reactive Extensions](https://github.com/monifu/monifu/wiki/Reactive-Extensions-(Rx))

```scala
import monifu.concurrent.Implicits.globalScheduler
import play.api.libs.ws._
import monifu.reactive._

// emits an auto-incremented number, every second
Observable.interval(1.second)
  // drops the first 10 emitted events
  .drop(10) 
  // takes the first 100 emitted events  
  .take(100) 
  // per second, makes requests and concatenates the results
  .flatMap(x => WS.request(s"http://some.endpoint.com/request?tick=$x").get())
  // filters only valid responses
  .filter(response => response.status == 200) 
  // processes response, selecting the body
  .map(response => response.body) 
  // creates subscription, foreach response print it
  .foreach(x => println(x)) 
```

[Atomic References](https://github.com/monifu/monifu/wiki/Atomic-References)

```scala
import monifu.concurrent.atomic.Atomic

val queue = Atomic(Queue.empty[String])

queue.transform(queue.enqueue("first item"))
queue.transform(queue.enqueue("second item"))

queue.transformAndExtract(queue.dequeue)
//=> "first item"

queue.transformAndExtract(queue.dequeue)
//=> "second item"

val number = Atomic(BigInt(1))

number.incrementAndGet
//=> res: scala.math.BigInt = 2
```

[Schedulers](https://github.com/monifu/monifu/wiki/Schedulers)

```scala
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.Scheduler.{computation => s}

val loop = Atomic(0) // we don't actually need an atomic or volatile here

s.scheduleRecursive(1.second, 5.seconds, { reschedule =>
  if (loop.incrementAndGet < 10) {
    println(s"Counted: $counted")
    // do next one
    reschedule()    
  }
})
```

## Documentation

The available documentation is maintained as a [GitHub's Wiki](https://github.com/monifu/monifu/wiki).
Work in progress.

* [Reactive Extensions (Rx)](https://github.com/monifu/monifu/wiki/Reactive-Extensions-%28Rx%29)
* [Atomic References](https://github.com/monifu/monifu/wiki/Atomic-References) 
* [Schedulers](https://github.com/monifu/monifu/wiki/Schedulers) 

API documentation:

* [monifu](http://www.monifu.org/monifu/current/api/)
* [monifu-js](http://www.monifu.org/monifu-js/current/api/)

Release Notes:

* [Version 0.13 - Jun 19, 2014](https://github.com/monifu/monifu/wiki/0.13)
* [Version 0.12 - May 31, 2014](https://github.com/monifu/monifu/wiki/0.12)
* [Other Releases](https://github.com/monifu/monifu/wiki/Release-Notes)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.10 and 2.11. Also cross-compiled to
the latest Scala.js (at the moment Scala.js 0.5.5). The targeted JDK version
for the published packages is version 6 (see 
[faq entry](https://github.com/monifu/monifu/wiki/Frequently-Asked-Questions#what-javajdk-version-is-required)).

- Current stable release is: `0.13.0`
- In-development release: `0.14.0.RC1`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "0.14.0.RC1"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %%% "monifu-js" % "0.14.0.RC1"
```

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/yklogo.png" align="right" />
YourKit supports the Monifu project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

