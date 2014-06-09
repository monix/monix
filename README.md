<img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/monifu.png" align="right" />

Extensions to Scala's standard library for multi-threading primitives and functional reactive programming. Targets both the JVM and [Scala.js](http://www.scala-js.org/).

- [![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=v0.12.2)](https://travis-ci.org/alexandru/monifu) (stable version)
- [![Build Status](https://travis-ci.org/alexandru/monifu.png?branch=v0.13.0-RC1)](https://travis-ci.org/alexandru/monifu) (in-development version)

## Feature Overview

[Reactive Extensions](https://github.com/alexandru/monifu/wiki/Reactive-Extensions-(Rx))

```scala
import monifu.concurrent.Scheduler.Implicits.global
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

[Atomic References](https://github.com/alexandru/monifu/wiki/Atomic-References)

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

[Schedulers](https://github.com/alexandru/monifu/wiki/Schedulers)

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

The available documentation is maintained as a [GitHub's Wiki](https://github.com/alexandru/monifu/wiki).
Work in progress:

* [Reactive Extensions (Rx)](https://github.com/alexandru/monifu/wiki/Reactive-Extensions-%28Rx%29)
* [Atomic References](https://github.com/alexandru/monifu/wiki/Atomic-References) 
* [Schedulers](https://github.com/alexandru/monifu/wiki/Schedulers) 

API documentation:

* [monifu](http://www.monifu.org/monifu/current/api/)
* [monifu-js](http://www.monifu.org/monifu-js/current/api/)

Release Notes:

* [Version 0.13 - in development](https://github.com/alexandru/monifu/wiki/0.13)
* [Version 0.12 - May 31, 2014](https://github.com/alexandru/monifu/wiki/0.12)
* [Version 0.11 - May 28, 2014](https://github.com/alexandru/monifu/wiki/0.11)
* [Version 0.10 - May 26, 2014](https://github.com/alexandru/monifu/wiki/0.10)
* [Version 0.9 - May 23, 2014](https://github.com/alexandru/monifu/wiki/0.9)
* [Other Releases](https://github.com/alexandru/monifu/wiki/Release-Notes)

## Usage

The packages are published on Maven Central.

Compiled for Scala 2.11. Also cross-compiled to
the latest Scala.js (at the moment Scala.js 0.5.0-RC1). The targeted JDK version
for the published packages is version 6 (see 
[faq entry](https://github.com/alexandru/monifu/wiki/Frequently-Asked-Questions#what-javajdk-version-is-required)).

- Current stable release is: `0.12.2`
- In-development release: `0.13.0-RC1`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "0.12.2"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %% "monifu-js" % "0.12.2"
```

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).
