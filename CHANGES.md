## Version 2.0-M2

(work in progress)

- [Issue #134](https://github.com/monixio/monix/issues/134): new operator `Observable.switchIfEmpty`

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