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
