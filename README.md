<img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/monifu.png" align="right" />

Idiomatic Reactive Extensions for Scala. Targets both the JVM and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/monifu/monifu.png?branch=v1.0-M1)](https://travis-ci.org/monifu/monifu)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monifu/monifu?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Teaser

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
  // samples by 3 seconds, repeating previous results in case of nothing new
  .sampleRepeated(3.seconds)
  // processes response, selecting the body
  .map(response => response.body)
  // creates subscription, foreach response print it
  .foreach(x => println(x))
```

There's actually a lot more to Monifu.

## Documentation

**NOTE (Feb 27):** the documentation is a little obsolete and incomplete in places.
Currently the project is marching towards a stable 1.0 and properly documented
version. Be patient :-)

The available documentation is maintained as a [GitHub's Wiki](https://github.com/monifu/monifu/wiki).
Work in progress.

* [Reactive Extensions (Rx)](https://github.com/monifu/monifu/wiki/Reactive-Extensions-%28Rx%29)
* [Atomic References](https://github.com/monifu/monifu/wiki/Atomic-References)
* [Schedulers](https://github.com/monifu/monifu/wiki/Schedulers)

API documentation:

* [monifu](http://www.monifu.org/monifu/current/api/)
* [monifu-js](http://www.monifu.org/monifu-js/current/api/)

Release Notes:

* [Version 1.0-M1 - Feb 27, 2015](https://github.com/monifu/monifu/wiki/1.0-M1)
* [Other Versions](https://github.com/monifu/monifu/wiki/Release-Notes)

## Usage

The packages are published on Maven Central. Compiled for Scala 2.11.5
and Scala.js 0.6.0. Older versions are no longer supported.

- Current stable release is: `1.0-M1`

### For the JVM

```scala
libraryDependencies += "org.monifu" %% "monifu" % "1.0-M1"
```

### For targeting Javascript runtimes with Scala.js

```scala
libraryDependencies += "org.monifu" %%% "monifu" % "1.0-M1"
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
