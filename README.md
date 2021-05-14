# Monix

<img src="https://monix.io/public/images/monix-logo.png?ts=20161024" align="right" width="280" />

Asynchronous, Reactive Programming for Scala and [Scala.js](http://www.scala-js.org/).

[![Build](https://github.com/monix/monix/workflows/build/badge.svg?branch=series/3.x)](https://github.com/monix/monix/actions?query=branch%3Aseries%2F3.x+workflow%3Abuild) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monix/monix)

- [Overview](#overview)
- [Usage](#usage)
  - [Library dependency (sbt)](#library-dependency-sbt)
  - [Sub-projects](#sub-projects)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Adopters](#adopters)
- [License](#license)

## Overview

Monix is a high-performance Scala / Scala.js library for composing asynchronous,
event-based programs.

It started as a proper implementation of [ReactiveX](http://reactivex.io/),
with stronger functional programming influences and designed from the ground up
for  back-pressure and made to interact cleanly with Scala's standard library,
compatible out-of-the-box with the [Reactive Streams](http://www.reactive-streams.org/)
protocol. It then expanded to include abstractions for suspending side effects
and for resource handling, and is one of the parents and implementors of
[Cats Effect](https://typelevel.org/cats-effect/).

<a href="https://typelevel.org/"><img src="https://monix.io/public/images/typelevel.png" width="150" style="float:right;" align="right" /></a>

A [Typelevel project](http://typelevel.org/projects/), Monix proudly
exemplifies pure, typeful, functional programming in Scala, while being pragmatic,
and making no compromise on performance.

Highlights:

- exposes the kick-ass [Observable](https://monix.io/docs/current/reactive/observable.html), 
  [Iterant](https://monix.io/api/current/monix/tail/Iterant.html), 
  [Task](https://monix.io/docs/current/eval/task.html),
  [IO[E, A]](https://bio.monix.io/docs/introduction), and 
  [Coeval](https://monix.io/docs/current/eval/coeval.html) data types,
  along with all the support they need
- *modular*, split into multiple sub-projects, only use what you need
- designed for true asynchronicity, running on both the
  JVM and [Scala.js](http://scala-js.org)
- excellent test coverage, code quality, and API documentation
  as a primary project policy

## Usage

- Use **[monix-jvm-app-template.g8](https://github.com/monix/monix-jvm-app-template.g8)**
for quickly getting started with a Monix-driven app
- See **[monix-sample](https://github.com/monix/monix-sample)** for
a project exemplifying Monix used both on the server and on the client.

### Library dependency (sbt)

For the stable release (compatible with Cats, and Cats-Effect 2.x):
 
```scala
libraryDependencies += "io.monix" %% "monix" % "3.4.0"
```
  
### Sub-projects

Monix 3.x is modular by design. See the [sub-modules graph](https://monix.io/docs/current/intro/usage.html#sub-modules--dependencies-graph):

<img src="https://monix.io/public/misc/dependencies.svg"
  alt="Sub-modules graph" />

You can pick and choose:

- `monix-execution` exposes the low-level execution environment, or
  more precisely `Scheduler`, `Cancelable`, `Atomic`, `Local`, `CancelableFuture`
  and `Future` based abstractions from `monix-catnap`.
- `monix-catnap` exposes pure abstractions built on top of
   the [Cats-Effect](https://typelevel.org/cats-effect/) type classes;
   depends on `monix-execution`, Cats 1.x and Cats-Effect
- `monix-eval` exposes `Task`, `Coeval`;
  depends on `monix-execution`
- `monix-reactive` exposes `Observable` for modeling reactive,
  push-based streams with back-pressure; depends on `monix-eval`
- `monix-tail` exposes `Iterant` streams for purely functional pull
  based streaming; depends on `monix-eval` and makes heavy use of
  Cats-Effect
- `monix` provides all of the above

## Documentation

See:

- Website: [Monix.io](https://monix.io/)
- [Documentation (current)](https://monix.io/docs/current/) ([3.x](https://monix.io/docs/3x/))
- [Documentation for 2.x (old)](https://monix.io/docs/2x/)
- [Presentations](https://monix.io/presentations/)

API Documentation:

- [Current](https://monix.io/api/current/) 
- [3.3](https://monix.io/api/3.3/)
- [2.3](https://monix.io/api/2.3/)
- [1.2](https://monix.io/api/1.2/)

([contributions are welcome](https://github.com/monix/monix.io))

Related:

- [Typelevel Cats](https://typelevel.org/cats/)
- [Typelevel Cats-Effect](https://typelevel.org/cats-effect/)

## Contributing

The Monix project welcomes contributions from anybody wishing to
participate. You must license all code or documentation provided 
with the Apache License 2.0, see [LICENSE.txt](./LICENSE.txt).

You must follow the [Scala Code of Conduct](./CODE_OF_CONDUCT.md) when
discussing Monix on GitHub, Gitter channel, or other venues.

Feel free to open an issue if you notice a bug, have an idea for a
feature, or have a question about the code. Pull requests are also
gladly accepted. For more information, check out the
[contributor guide](CONTRIBUTING.md).

If you'd like to donate in order to help with ongoing maintenance:

<a href="https://www.patreon.com/bePatron?u=6102596"><img label="Become a Patron!" src="https://c5.patreon.com/external/logo/become_a_patron_button@2x.png" height="40" /></a>

## Adopters

Here's a (non-exhaustive) list of companies that use Monix in production. Don't see yours? 
Submit a PR ❤️ 

- [Abacus](https://abacusfi.com)
- [Agoda](https://www.agoda.com)
- [commercetools](https://commercetools.com)
- [Coya](https://www.coya.com/)
- [E.ON Connecting Energies](https://www.eon.com/)
- [eBay Inc.](https://www.ebay.com)
- [Eloquentix](http://eloquentix.com/)
- [Hypefactors](https://www.hypefactors.com)
- [Iterators](https://www.iteratorshq.com)
- [Sony Electronics](https://www.sony.com)
- [Tinkoff](https://tinkoff.ru)
- [Zalando](https://www.zalando.com)
- [Zendesk](https://www.zendesk.com)

## License

All code in this repository is licensed under the Apache License,
Version 2.0.  See [LICENCE.txt](./LICENSE.txt).
