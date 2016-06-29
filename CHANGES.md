## Version 2.0-RC8 (Jun 29, 2016)

**Critical bug fix:**

- [BUG #181](https://github.com/monixio/monix/issues/181): the default operators
  on `CancelableFuture` are triggering `StackOverflow` exceptions
  
New Features:

- [Issue #184](https://github.com/monixio/monix/pull/184): introducing the
  `Consumer` type, a factory of subscribers that makes it easier to specify 
  reusable and composable consumers and for example, it makes it possible, 
  out of the box, to load-balance the workload between multiple subscribers in
  parallel; see the description
- [Issue #186](https://github.com/monixio/monix/pull/186) (related to 
  [issue #168](https://github.com/monixio/monix/issues/168)): adds the 
  `Observable.interleave2` operator, similar with the one in `FS2` 
  (former scalaz-streams)
- [Issue #180](https://github.com/monixio/monix/issues/180): the `Observer.feed`
  function now has an overload that does not take a cancelable, because
  it's awkward coming up with one if it isn't needed; there's also a 
  `onNextAll` extension for both `Observer` and `Subscriber` which can push
  a whole collection of events
- [Issue #187](https://github.com/monixio/monix/issues/187): integrates
  the `MonadCombine` type-class from [Cats](http://typelevel.org/cats/),
  being similar to the Scalaz `MonadPlus`, as somehow this was missed in 
  the initial integration
- [Issue #177](https://github.com/monixio/monix/issues/177) reviews exposed
  traits and abstract classes, making sure they inherit from `Serializable` 
- [Issue #85](https://github.com/monixio/monix/issues/85): small change,  
  clarifies the ScalaDoc on the `RefCountCancelable` type
- [Issue #162](https://github.com/monixio/monix/issues/162): implements
  the `Observable.takeUntil(trigger: Observable[Any])` operator, an operator
  that takes from the source until another observable triggers an event
- [Issue #189](https://github.com/monixio/monix/issues/189): for `Observable`
  operators that return a single item (e.g. `sumF`, `foldLeftF`, etc.) adds
  variants that do the same thing, but return `Task` results instead, so now
  we have `foldLeftL`, `sumL`, etc. that return tasks
- [Issue #190](https://github.com/monixio/monix/issues/190): changes many 
  observable operators, that were taking by-name parameters or initial state
  parameters (e.g. `headOrElseF`, `foldLeftF`, etc), to take `Coeval` params
  instead, in order to have fine control over the evaluation model
- [Issue #191](https://github.com/monixio/monix/issues/191): introduces 
  by default an implicit conversion from `Any` to `Coeval.Now`, to make it 
  easier to use `Coeval` as function parameters - the default will thus 
  simply be strict evaluation, strictness being usually the default in Scala

## Version 2.0-RC7 (Jun 21, 2016)

**Bug fixes:**

- [BUG #170](https://github.com/monixio/monix/issues/170): 
  `Task.materializeAttempt` doesn't work for `BindAsync`, leading to `onErrorHandleWith`
  not working with errors triggered in `flatMap` on async tasks
 
New Features:

- [Issue #171](https://github.com/monixio/monix/issues/171): 
   Add Scheduler builder on the JVM that allows specifying 
   just the `ExecutionModel`, falling back `global` otherwise   
- [Issue #174](https://github.com/monixio/monix/issues/174): 
  [Scalaz](https://github.com/scalaz/scalaz) integration (in addition to the 
  Cats integration) for FP goddess
- [Issue #175](https://github.com/monixio/monix/issues/175):
  Reintroduce all of project `Sincron` into `monix-execution`, which means
  that now `monix-execution` exposes `Atomic` references. This project
  was split from Monix, but the decision didn't make sense and the exposed
  functionality is super useful and in the spirit of `monix-execution`
- [Issue #176](https://github.com/monixio/monix/issues/176): now that we
  have `Task`, we introduce `TaskApp`, a safe `App` type that allows one
  to specify pure programs
  
## Version 2.0-RC6 (Jun 15, 2016)

**Breaking changes:**

- [Issue #157](https://github.com/monixio/monix/issues/157): Renaming 
  `Observable.doOnCancel` to `doOnSubscriptionCancel`
  because its meaning has changed since Monifu 1.x and it will cause pain
- [Issue #160](https://github.com/monixio/monix/issues/160) (**breaking change**): 
  Revamp the buffer operators on `Observable`
- [Issue #161](https://github.com/monixio/monix/issues/161) (**breaking change**):
  Revamp the ConcurrentSubject and Pipe constructors

Bug fixes and enhancements:

- [Bug #153](https://github.com/monixio/monix/issues/153): already fixed in 
   `2.0-RC5` for `Task`, but now also fixed for `Coeval`
- [Issue #155](https://github.com/monixio/monix/issues/155): Coeval enhancements 
   and fixes
- [Issue #156](https://github.com/monixio/monix/issues/156): Adding `Observable.doOnTerminate`
  for executing a piece of logic when `onComplete` or `onError` happens, or
  when the subscriber stops the streaming with `Stop`
- [Issue #158](https://github.com/monixio/monix/issues/158): `Observable.fromIterator`
  takes now an optional `onFinish` callback for resource deallocation, because
  the `Iterator` interface doesn't provide a `close()` that could be used
- [Issue #163](https://github.com/monixio/monix/issues/163): added 
  `Observable.fromInputStream`, `fromCharsReader` and `fromLinesReader` for
  reading from `java.io.InputStream`, `java.io.Reader` and `java.io.BufferedReader`
  data sources
- [Issue #164](https://github.com/monixio/monix/issues/164): Add 
  `Observable.executeOn`, as alternative to `observeOn`
- [Issue #165](https://github.com/monixio/monix/issues/165): Simplification
  of the Cats integration

## Version 2.0-RC5 (May 31, 2016)

Critical bug fix:

- [Bug 153](https://github.com/monixio/monix/issues/153) - Task.sequence and Task.gather 
  return a shared mutable.ListBuffer

## Version 2.0-RC4 (May 31, 2016)

- [Issue #89](https://github.com/monixio/monix/issues/149) - reintroducing a minimal 
  Cats integration, along with tests based on `cats-laws`. We are splitting `monix.type` 
  into its own sub-project and `monix-cats` depends on it. This ensures that the pick what
  you use approach also works with `monix-cats`, as people wanting just `Task` should not
  get `Observable`, yet `Observable` needs integration as well.
- [Issue #149](https://github.com/monixio/monix/issues/149) - documentation related fixes
  - Improved the description of `Task`
  - `Task.unit` is now a final val
  - `Task.never` now shares the reference instead of building a new instance every time
  - Exposing `Task.unsafeStartNow` and `Task.unsafeStartAsync`, as otherwise `Task.unsafeCreate` 
    is useless. So we should expose all of them, or none at all.
  - `FutureUtils.Extensions.dematerialize` was named "materialize" (WTF!) and is renamed
  - Task should inherit just from `Serializable` and not from `Product`
- [Issue #150](https://github.com/monixio/monix/issues/150) - add a new `Task.doOnFinish` 
  operator that executes once a task is finished.
- [Issue #151](https://github.com/monixio/monix/issues/151) - changing `Future.sequence` to be 
  ordered in both execution and effects
- [Issue #152](https://github.com/monixio/monix/issues/152) - introduce `Task.gather` which 
  behaves like the previous `sequence` and `Task.gatherUnordered` which doesn't do ordering
  for results either.  

## Version 2.0-RC3

- [Issue #147](https://github.com/monixio/monix/issues/147) - Make `BehaviorSubject` and `ReplaySubject` 
  remove subscribers that triggered `Stop` while connecting, thus freeing
  the memory sooner, otherwise the GC cannot free the subscriber because
  its reference is kept captive until the next `Subject.onNext`
- Remove `tut` and `site` from `project/plugins`, as the documentation will
  be handled in the [monix.io](https://github.com/monixio/monix.io) repository
- Re-enable code coverage in Travis and fix the build to actually test Scala 2.10.6

## Version 2.0-RC2

Minor release with a single bug fix:

- [Issue #144](https://github.com/monixio/monix/issues/144) - fix Observable.zip

## Version 2.0-RC1

Feature freeze. There's lots of interesting stuff to work on, but I'd rather have
a stable 2.0 release first.

Bug fix:

- [Issue #143](https://github.com/monixio/monix/issues/143) - on RefCountObservable cancel and stop should be idempotent


## Version 2.0-M2

The theme and the big issue of this release has been redesigning `Task`'s 
implementation and introducing `Coeval`. See [Issue #141](https://github.com/monixio/monix/issues/141)
for details. 

List of changes:
 
- [Issue #88](https://github.com/monixio/monix/issues/88): the `Task` implementation
  has been redesigned from scratch
- [Issue #89](https://github.com/monixio/monix/issues/89): Cats integration 
  has been tried and yielded very positive results, but is being delayed 
- [Issue #96](https://github.com/monixio/monix/issues/96) 
  and [issue 99](https://github.com/monixio/monix/issues/99): add `MulticastStrategy` for
  safer building of multicast Observables
- [Issue #127](https://github.com/monixio/monix/issues/127):
  Introduce operators onErrorHandle and onErrorHandleWith
- [Issue #128](https://github.com/monixio/monix/issues/128): operators 
  materialize, dematerialize and memoize for `Task` (and `Coeval`)
- [Issue #113](https://github.com/monixio/monix/issues/113): Introduce the `bufferIntrospective`
  operator
- [Issue #123](https://github.com/monixio/monix/issues/123): underlying protocol 
  changes, did some fixes for the work that already happened for `M1`
- [Issue #131](https://github.com/monixio/monix/issues/131): renamed `Ack.Cancel`
  to `Ack.Stop` in order to differentiate it as a verb from `Cancelable.cancel`,
  because in version `2.0` they are two different actions 
  (and it's more semantically correct this way)
- [Issue #132](https://github.com/monixio/monix/issues/132): introduced the
   `Observable.onCancelTriggerError` operator
- [Issue #133](https://github.com/monixio/monix/issues/133): introduced the
   `Observable.doOnDownstreamStop` and `doOnCancel` 
   (which is one reason for #131)
- [Issue #134](https://github.com/monixio/monix/issues/134): 
   New operator `Observable.switchIfEmpty`
- [Issue #136](https://github.com/monixio/monix/issues/136): 
   Clarify reactive streams, initiated `monix.execution.rstreams` for 
   reusable subscription types and added the `ReactivePublisher` type-class
   for things that can be converted to `org.reactivestreams.Publisher`
   (both Observable and Task are instances)
- [Issue #140](https://github.com/monixio/monix/issues/140): 
   Add type-class hierarchy, to be integrated with both Cats and Scalaz
- [Issue #141](https://github.com/monixio/monix/issues/141): reimplement Task 
  from scratch, introduce Coeval, introduce Scheduler.executionModel

## Version 2.0-M1

Milestone release for version 2.0, supplied as a preview.
The final 2.0 release should not have major changes compared
to this first milestone, but API breakage may still happen.

This version is not production ready. Use at your own risk.

List of changes:

- [Issue #60](https://github.com/monixio/monix/issues/60) - initiated the `docs` sub-project with
  tutorials type-checked as part of the test process
- [Issue #88](https://github.com/monixio/monix/issues/88) - add Monix's own `Task`, an alternative
  to `Future` and the Scalaz `Task`
- [Issue #89](https://github.com/monixio/monix/issues/89) - initial integration 
  with [Cats](https://github.com/typelevel/cats)
- [Issue #90](https://github.com/monixio/monix/issues/90) - remove functionality that isn't
  relevant to the purpose of Monix and be supported
- [Issue #91](https://github.com/monixio/monix/issues/91) - project rename and reorganization (epic API breakage)
- [Issue #93](https://github.com/monixio/monix/issues/93) - renamed `Observable.onSubscribe` to
  `Observable.unsafeSubscribeFn`, because it shouldn't be used directly, unless you really, really
  know what you're doing
- [Issue #94](https://github.com/monixio/monix/issues/94) - fixed comments style to ScalaDoc
- [Issue #96](https://github.com/monixio/monix/issues/96) - removed `Observable.create` because
  it is unsafe
- [Issue #100](https://github.com/monixio/monix/issues/100) - enhanced the `Scheduler` interface
- [Issue #102](https://github.com/monixio/monix/issues/102) - brought back `sun.misc.Unsafe` 
  (actually this was done in separate project, as part of [issue #104](https://github.com/monixio/monix/issues/104))
- [Issue #104](https://github.com/monixio/monix/issues/104) - separated the `Atomic` implementations
  in the [Sincron](https://github.com/monixio/sincron)
- [Issue #106](https://github.com/monixio/monix/issues/106) - enabled code coverage reports
- [Issue #111](https://github.com/monixio/monix/issues/111) - improved test coverage
- [Issue #114](https://github.com/monixio/monix/issues/114) - initiated the `benchmarks` sub-project to
  track performance issues
- [Issue #115](https://github.com/monixio/monix/issues/115) - `Cancelable.cancel` now returns `Unit`
  and not `Boolean`
- [Issue #118](https://github.com/monixio/monix/issues/118) - Fix `materialize`, add `dematerialize`
- [Issue #119](https://github.com/monixio/monix/issues/119) - Introduced `Pipe`, 
  renamed `Channel` in `ConcurrentSubject`, renamed `SynchronousObserver` in `SyncObserver`,
  renamed `SynchronousSubscriber` in `SyncSubscriber`
- [Issue #121](https://github.com/monixio/monix/issues/121) - Clarified the contract for synchronous
  pipelines, got rid of the hacks from `internal`, replacing them with macro-driven extensions
  for `Future[Ack]`
- [Issue #123](https://github.com/monixio/monix/issues/123) - Changed the protocol of `Observable.subscribe`,
  back-pressuring `onComplete` and `onError` is now optional and `Observable.unsafeSubscribeFn` needs
  to return a usable `Cancelable` instead of `Unit` (big internal change)
- [Issue #125](https://github.com/monixio/monix/issues/125) - Modified contract of 
  `AsyncScheduler`, added a new `ExecutorScheduler`
  
## Version 1.2

- [Issue #113](https://github.com/monixio/monix/issues/113) - Introduce the `bufferIntrospective`
  operator

## Version 1.1

- [Issue #125](https://github.com/monixio/monix/issues/125) - Modify contract of AsyncScheduler, 
  and add a new Scheduler type based entirely on Java's ScheduledExecutor
- Update versions of SBT dependencies
- Documentation changes (fixed missing argument in Observable docs, add code of conduct mention)
