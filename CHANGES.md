## Version 2.3.3 (Jan 21, 2018)

Release is binary backwards compatible with series `2.3.x`.

Bug fixes:

- [Issue #468](https://github.com/monix/monix/issues/468):
  Observables created using `concat` don't get canceled
  (**critical**)
- [Issue #483](https://github.com/monix/monix/issues/483):
  stack overflow error on `MVar.put`
- [Issue #541](https://github.com/monix/monix/issues/541):
  `observable.take(0)` shouldn't subscribe to the source at all
- [Issue #475](https://github.com/monix/monix/pull/475):
  `Observable.fromAsyncStateAction` does not protect against
  exceptions thrown in use code

Issue #468 in particular is pretty serious as it can lead to
resource leaks. Read the [pull request](https://github.com/monix/monix/pull/469)
for more details.

Upgrade to `2.3.3` is recommended!

## Version 3.0.0-M3 (Jan 7, 2017)

Final milestone release before the RC and the final and stable `3.0.0`.

Special thanks to Leandro Bolivar for implementing propagation of
"local vars" (aka `Local` and `TaskLocal`, the equivalents of 
`ThreadLocal`, but for usage with `Future` and `Task`).

This release also lands a long awaited feature for `Task`: pure 
cancellation, aka `Task.cancel`. It's building on top of the current
`Task` implementation, however it changes the API — e.g. in order to 
keep `Task` pure, the `chooseFirstOf` operator is now gone, being
replaced with an equivalent `racePair` that operates with tasks and
pure functions.

The other highlight of the release are the performance improvements
for `Task`, an on-going process to make sure that Monix's `Task` 
remains the best implementation in Scala's ecosystem.

We now depend on Cats `1.0.1` and cats-effect `0.8`.

Full list of PRs:

- [PR #464](https://github.com/monix/monix/pull/464):
  updates dependencies, Scala to `2.12.4` and `2.11.12`, JCTools to 
  `2.1.1`, Minitest to `2.0.0`, Scala.js to `0.6.21`
- [PR #462](https://github.com/monix/monix/pull/462):
  Fix for `timeoutTo` to cancel source task directly after timeout
- [PR #444](https://github.com/monix/monix/pull/444):
  Add `localContextPropagation` to `Task.Options`, implement tracing 
  `Local` vars
- [PR 470](https://github.com/monix/monix/pull/470):
  increase test coverage
- [PR #473](https://github.com/monix/monix/pull/473):
  Fix issue where `fromAsyncStateAction` is not safe for user code
- [PR #485](https://github.com/monix/monix/pull/485) and 
  [PR #489](https://github.com/monix/monix/pull/489):
  Updates Cats to `1.0.1` and cats-effect to `0.8`
- [PR #474](https://github.com/monix/monix/pull/474):
  `Task` / `Coeval` Run-loop Optimizations, First Batch
- [PR #492](https://github.com/monix/monix/pull/492):
  Second batch of optimizations
- [PR #494](https://github.com/monix/monix/pull/494):
  `Task.cancel` as a pure action, along with `.start`, 
  `.race` and `.uncancelable`

## Version 3.0.0-M2 (Nov 9, 2017)

The highlight of this release is the upgrade to Cats 1.0-RC1,
bringing a `cats.Parallel[Task, Task]` instance that replaces
the need for `Task.nondeterminism`, now removed.

List of changes:

- [PR #437](https://github.com/monix/monix/pull/437): 
  Added `Iterant.zipWithIndex`
- [PR #439](https://github.com/monix/monix/pull/439):
  Added `Iterant.dump`
- [PR #441](https://github.com/monix/monix/pull/441):
  `Observable.mapParallelUnordered` needs configurable overflow strategy
- [PR #445](https://github.com/monix/monix/pull/445):
  Added `ShiftedRunnable` in `monix.execution`
- [PR #443](https://github.com/monix/monix/pull/443):
  Iterant `maxByL` and `minByL` ops
- [PR #442](https://github.com/monix/monix/pull/442):
  small fixes in `takeWhileNotCanceled` and `runAsyncGetFirst` docs
- [PR #450](https://github.com/monix/monix/pull/450):
  Minor test description fix
- [PR #458](https://github.com/monix/monix/pull/458):
  Updates to cats 1.0-RC1 and cats-effect 0.5, along with
  redesigning that integration, e.g. added `cats.Parallel` instance,
  `Iterant.parZip` and `Iterant.parZipMap`

## Version 3.0.0-M1 (Sep 15, 2017)

This is a major release that breaks both binary and source 
compatibility. The major themes of this release:

1. deep integration with [Typelevel Cats](https://typelevel.org/cats)
2. the `Iterant` data type in a new `monix-tail` sub-project
3. API refactoring, eliminated deprecations
4. major improvements to `Observable`, `Task` and `CancelableFuture`

Typelevel Cats integration:

- [PR #370](https://github.com/monix/monix/pull/370): introduced
  Cats and `cats-effect` as direct dependencies
- [PR #377](https://github.com/monix/monix/pull/377): added
  Cats related conversions, along with naming changes for consistency
  (e.g. renamed `Coeval.Attempt` to `Coeval.Eager`)
- [PR #387](https://github.com/monix/monix/pull/387): updated Cats to 
  `1.0.0-MF`, removed deprecated functions and classes
- [PR #397](https://github.com/monix/monix/pull/397): standardizes
  on Cats-related naming, removes `Coeval`'s `Comonad` implementation
- [PR #398](https://github.com/monix/monix/pull/398): re-adds
  instances for `CoflatMap`
- [PR #427](https://github.com/monix/monix/pull/427): adds
  conversions from Cats to Observable
  
New `monix-tail` sub-project, exposing `monix.tail.Iterant[F[_], A]`:

- [PR #280](https://github.com/monix/monix/pull/280): introduces
  `Iterant[F[_], A]` for pull-based streaming based on `Task` / `Coeval`
- [PR #396](https://github.com/monix/monix/pull/396):
  adds the `Iterant.scan` operator
- [PR #403](https://github.com/monix/monix/pull/403):
  adds `Iterant.foldWhileLeftL` and `Iterant.foldWhileLeftEvalL`
  operators
- [PR #404](https://github.com/monix/monix/pull/404):
  adds Iterant `existsL`, `forallL`, changes `Observable.foldWhileL`
  (breaking change)
- [PR #402](https://github.com/monix/monix/pull/402):
  adds `Iterant.foldRightL` operator
- [PR #405](https://github.com/monix/monix/pull/405):
  adds `Iterant` ops - `findL`, `foldL`, `maxL`, `minL`, `reduceL`
- [PR #407](https://github.com/monix/monix/pull/407):
  adds `Iterant` ops - `countL`, `distinctUntilChanged`, 
  `distinctUntilChangedByKey`
- [PR #412](https://github.com/monix/monix/pull/412):
  adds `scanEval` on both `Iterant` and `Observable`
- [PR #411](https://github.com/monix/monix/pull/411):
  another naming consistency change between `Observable` 
  and `Iterant`
- [PR #413](https://github.com/monix/monix/pull/413):
  `Iterant.bufferSliding`, `bufferTumbling` and `batched`
  operators
- [PR #417](https://github.com/monix/monix/pull/417) and
  [PR #418](https://github.com/monix/monix/pull/418):
  Reactive Streams implementation for `Iterant`
  
Improvements for `monix-execution` and `CancelableFuture`:

- [PR #390](https://github.com/monix/monix/pull/390): changes for
  `flatMap` on `CancelableFuture` to cancel all involved futures
  (thanks to [@larsrh](https://github.com/larsrh))
- [PR #395](https://github.com/monix/monix/pull/395): adds 
  Cats type class implementations for `CancelableFuture`
- [PR #431](https://github.com/monix/monix/pull/431): improvements
  to `CancelableFuture` to get rid of memory leak, also adding utils
  like `CancelableFuture.async`
- [PR #432](https://github.com/monix/monix/pull/432): further 
  fixes to `CancelableFuture`, since describing a cancellable `flatMap`
  is a hard problem
- [PR #418](https://github.com/monix/monix/pull/418):
  adds flip convenience method to `AtomicBoolean`
  (thanks to `@Wogan`) 
  
Improvements for `monix-reactive` and `Observable`:

- [PR #391](https://github.com/monix/monix/pull/391):
  makes Observable concatenation (++) stack safe
- [PR #408](https://github.com/monix/monix/pull/408):
  changes for `Iterant` and Cats consistency (make use of `Eq` and
  `Order` type classes, add `foldF` and `foldL`, remove `distinct`
  and `distinctByKey`)
- [PR #368](https://github.com/monix/monix/pull/368): added 
  the `Observable.intersperse` operator (thanks to 
  [@omainegra](https://github.com/omainegra))
- [PR #384](https://github.com/monix/monix/pull/384): added `contramap` 
  method to Callback (thanks to [@Wogan](https://github.com/Wogan))  
- [PR #425](https://github.com/monix/monix/pull/425): gets rid of 
  `ObservableLike`, makes `Observable` an `abstract class` where
  the operators are final, `Pipe` no longer has `Observable`'s 
  operators, just `transform`
  
Improvements for `monix-eval`, `Task` and `Coeval`:

- [PR #410](https://github.com/monix/monix/pull/410): `Task` and
  `Coeval` performance optimisations
- [PR #422](https://github.com/monix/monix/pull/422): adds `Task.shift`,
  an innovation inspired by `cats.effect.IO`
- [PR #424](https://github.com/monix/monix/pull/424):
  `Task` refactoring, gets rid of `Task.OnFinish` type alias
- [PR #430](https://github.com/monix/monix/pull/430):
  `Coeval` and `Task` refactoring to the `run` methods for
  consistency, introduced `map2`, `map3`...`map6` on both
  `Task` and `Coeval`, renamed `zipMap*` to `parMap2`, `parMap3`...
  `parMap6` for `Task`

Administrative and build changes:

- [PR #372](https://github.com/monix/monix/pull/372): configured
  project for Git hash versioning (for enabling automatic releases)
- [PR #378](https://github.com/monix/monix/pull/378):
  dropped Scala 2.10 support
- enabled automatic deployments through Travis-ci, wrote a blog post
  documenting the necessarily steps, see 
  [Automatic Releases to Maven Central with Travis and SBT](https://alexn.org/blog/2017/08/16/automatic-releases-sbt-travis.html)
- [PR #423](https://github.com/monix/monix/pull/423): updates Scala.js
  to 0.6.20, the final in the series before 1.0.0

## Version 2.3.1 (Sep 20, 2017)

Release is binary backwards compatible with series `2.3.x`.

This is a service release, fixing a minor issue in `AsyncScheduler`
and upgrading Scala.js:

- [PR #436](https://github.com/monix/monix/pull/436):
  adds reusable `ShiftedRunnable`, leaving no room for error in
  `BatchingScheduler` types by usage of `TrampolinedRunnable`;
  also upgrades Scala.js to 0.6.20

## Version 2.3.1 (Sep 20, 2017)

Release is binary backwards compatible with series `2.3.x`.

This is a service release, fixing a minor issue in `AsyncScheduler`
and upgrading Scala.js:

- [PR #436](https://github.com/monix/monix/pull/436):
  adds reusable `ShiftedRunnable`, leaving no room for error in
  `BatchingScheduler` types by usage of `TrampolinedRunnable`;
  also upgrades Scala.js to 0.6.20

## Version 2.3.0 (May 3, 2017)

Release is binary backwards compatible with series `2.2.x`.

List of changes:

- [Issue #340](https://github.com/monix/monix/issues/340): 
  Optimization of `TaskSemaphore`
- [Issue #349](https://github.com/monix/monix/issues/349):
  Replace usage of `scala.util.control.NonFatal` in handling
  fatal exceptions to `monix.execution.misc.NonFatal`
- [Issue #347](https://github.com/monix/monix/issues/347):
  Add `Task.deferAction` builder
- [Issue #339](https://github.com/monix/monix/issues/339):
  Add `Observable.observeOn` method
- [Issue #338](https://github.com/monix/monix/issues/338):
  `Cancelable` refs built with `Cancelable.collection` should
  use `Cancelable.cancelAll` in its implementation
- [Issue #350](https://github.com/monix/monix/issues/350):
  Change `BackPressure` buffer implementation to be more fair
  and ensure that it doesn't lose events
- [Issue #353](https://github.com/monix/monix/pull/353):
  Refactor `Coeval` / `Task` run-loop to introduce optimized
  `attempt` / `materialize` implementations and add 
  `transform` / `transformWith` methods making use of this
- [Issue #355](https://github.com/monix/monix/issues/355):
  Add `Coeval.run` method
- [Issue #356](https://github.com/monix/monix/issues/356):
  Add `Coeval#attempt` and `Task#attempt` methods
- [Issue #358](https://github.com/monix/monix/issues/358):
  Deprecate `materializeAttempt` and `dematerializeAttempt`
  on `Task` and `Coeval`
- [Issue #359](https://github.com/monix/monix/issues/359):
  Rename `Coeval.Attempt#isFailure` to `Coeval.Attempt#isError`
- [Issue #348](https://github.com/monix/monix/issues/348):
  Add `Consumer#transformInput` method
- [Issue #352](https://github.com/monix/monix/issues/352) / 
  [PR #361](https://github.com/monix/monix/pull/361):
  No back-pressure when converting from `org.reactivestreams.Publisher` 
  to `Observable`
- [Issue #362](https://github.com/monix/monix/pull/362):
  Replace `[T]` generic param to `[A]`, as a convention, 
  everywhere
- [PR #341](https://github.com/monix/monix/pull/341), 
  [PR #344](https://github.com/monix/monix/pull/344),
  [PR #346](https://github.com/monix/monix/pull/346),
  [Commit 9357ba](https://github.com/monix/monix/commit/9357ba4e5632c605623157343247054e338d42f0),
  etc:
  Update dependencies (Scalaz 7.2.11, Scala 2.11.11, 
  Scala 2.12.2, Scala.js 0.6.16)  
  
Administrative:

- [Issue #354](https://github.com/monix/monix/issues/354):
  Enable Mima and Unidoc error reporting in Travis build
- [PR #351](https://github.com/monix/monix/pull/351):
  Specify that Monix is now a Typelevel project with
  full membership
  
## Version 2.2.3 (Mar 10, 2017)

Critical bug fix release related to Scala 2.12:

- [Bug #330](https://github.com/monix/monix/issues/330):
  `Ack.Continue.transformWith` and `Ack.Stop.transformWith` 
  are not stack-safe
  
Most (probably all) functionality in Monix is not affected, because Monix 
rarely flatMaps `Continue` references and we have had extensive tests for it. 
However this bug can be dangerous for people that have implemented the 
communication protocol (as described in the docs) by themselves.

## Version 2.2.2 (Feb 22, 2017)

New Features:

- [Issue #306](https://github.com/monix/monix/issues/306):
  Circuit Breaker for `Task`
- [Issue #312](https://github.com/monix/monix/issues/312):
  Add `Task.memoizeOnSuccess` and `Coeval.memoizeOnSuccess`
- [Issue #313](https://github.com/monix/monix/issues/313):
  Add `Task.deferFutureAction` builder
- [Issue #325](https://github.com/monix/monix/issues/325):
  Add `SingleAssignmentCancelable.plusOne`
- [Issue #319](https://github.com/monix/monix/issues/319):
  Move and redesign exceptions in `monix.execution`
- [Issue #314](https://github.com/monix/monix/issues/314):
  `Task.sequence` should have lazy behavior in evaluating 
  the given sequence
  
Bug fixes:

- [Bug #268](https://github.com/monix/monix/issues/268):
  Optimise the unsubscribe logic in `PublishSubject`
- [Bug #308](https://github.com/monix/monix/issues/308):
  Fix NPE on `AsyncSubject.unsubscribe`
- [Bug #315](https://github.com/monix/monix/issues/315):
  The `MapTaskObservable` internal object is exposed (ScalaDoc)
- [Bug #321](https://github.com/monix/monix/issues/321):
  `Observable.concatMap` and `mapTask` cannot be canceled if
  the source has already completed
- Documentation fixes: 
  [#307](https://github.com/monix/monix/pull/307), 
  [#309](https://github.com/monix/monix/pull/309),
  [#311](https://github.com/monix/monix/issues/311),
  [#316](https://github.com/monix/monix/issues/316) and
  [#317](https://github.com/monix/monix/issues/317)
  
Build:

- enabled the Scala 
  [Migration Manager](https://github.com/typesafehub/migration-manager) 
  (MiMa) in `build.sbt` to check for backwards compatibility problems
- [Issue #322](https://github.com/monix/monix/issues/322):
  Switch projects which use `CrossVersion.full/"org.scala-lang"` 
  to `CrossVersion.patch/scalaOrganization.value`

## Version 2.2.1 (Jan 27, 2017)

Unfortunately we have to push an emergency fix:

- [Issue #305](https://github.com/monix/monix/pull/305):
  Fix stack-overflow error in `MVar`

## Version 2.2.0 (Jan 25, 2017)

In addition to the changes from the `2.2.0-M1` milestone:

- [Issue #298](https://github.com/monix/monix/pull/298):
  Don't publish empty jars in root projects
- [Issue #300](https://github.com/monix/monix/pull/300):
  Update to cats 0.9.0
- [Issue #301](https://github.com/monix/monix/pull/301):
  `MVar`, a non-blocking Task-based implementation
- [Issue #303](https://github.com/monix/monix/issues/303):
  Observable "doOn" operators refactoring

## Version 2.2.0-M1 (Jan 4, 2017)

Version `2.2.0-M1` is a milestone release, released for feedback 
and testing purposes.

- [Issue #281](https://github.com/monix/monix/issues/281):
  Fix performance regression in `Task.flatMap`
- [Issue #284](https://github.com/monix/monix/issues/284):
  Move `ExecutionModel` to `monix.execution`
- [Issue #285](https://github.com/monix/monix/issues/285):
  Rename `Task.runAsync(f: Try[A] => Unit)` to `Task.runOnComplete`
- [Issue #286](https://github.com/monix/monix/issues/286):
  Add `Task.runSyncMaybe`
- [Issue #287](https://github.com/monix/monix/issues/287):
  Add `Scheduler.forkJoin` builder
- [Issue #266](https://github.com/monix/monix/issues/266):
  Add `SchedulerService` interface in `monix.execution.schedulers`
- [Issue #288](https://github.com/monix/monix/issues/288):
  `Task.create` should not force async boundary
- [Issue #289](https://github.com/monix/monix/issues/289):
  `Observable.foreach` should not fork
- [Issue #291](https://github.com/monix/monix/issues/291):
  Add `Observable.takeEveryNth` operator
- [Issue #292](https://github.com/monix/monix/issues/292):
  Optimize `Observable.concatMap`
- [Issue #294](https://github.com/monix/monix/issues/294):
  Optimize `Observable.bufferSliding`
- [Issue #295](https://github.com/monix/monix/issues/295):
  Add `Observable.publishSelector`, `Observable.pipeThroughSelector`
- [Issue #296](https://github.com/monix/monix/issues/296):
  Add `Task.deferFuture` builder

## Version 2.1.2 (Dec 19, 2016)

Version `2.1.2` is a minor release, binary compatible with `2.1.x`,
upgrading Scala to `2.12.1` and fixing a bug in `Observable.bufferSliding`.

- [Bug #275](https://github.com/monix/monix/issues/275):
  `Observable.bufferSliding` is broken

## Version 2.1.1 (Nov 22, 2016)

Version `2.1.1` is a minor release, binary compatible with `2.1.0`,
fixing the compatibility with older Android versions.

The gist is that older Android versions are incompatible with our
usage of `sun.misc.Unsafe`. And this might also be true of other 
platforms as well, like the upcoming Java 9.

Therefore we are doing two things:

1. we introduce new `monix.execution.atomic.Atomic` implementations
   that make use of `AtomicFieldUpdater` classes, for those platforms
   that do not support `sun.misc.Unsafe`; hopefully this will perform
   well on top of Java 9, see this post by Aleksey Shipilёv:
   https://shipilev.net/blog/2015/faster-atomic-fu/\
2. in our usage of [JCTools](https://github.com/JCTools/JCTools/),
   since these rely heavily on `sun.misc.Unsafe`, we fallback to
   implementations from `org.jctools.queues.atomic`, as these are 
   safe to use
   
The issues being addressed:

- [Bug #269](https://github.com/monix/monix/issues/269): Observable
  throws NoSuchFieldException (via jctools) on Android
- [Issue #270](https://github.com/monix/monix/issues/270): Add support
  for platforms that do not have `sun.misc.Unsafe`
  (with the corresponding [PR #272](https://github.com/monix/monix/pull/272))

## Version 2.1.0 (Nov 9, 2016)

Version `2.1.0` is a major release that is not compatible with
the previous `2.0.x` series.

Issues addressed:

- [Issue #226](https://github.com/monix/monix/issues/226):
  Add `Task.Options` with an `autoCancelableRunLoops` property
- [Issue #227](https://github.com/monix/monix/issues/227): 
  Add `executeWithFork`, `executeWithModel` and `asyncBoundary` 
  operators on `Task`
- [Issue #232](https://github.com/monix/monix/issues/232):
  Async `Task` instances should execute with `TrampolinedRunnable` 
  everywhere we can 
- [Issue #236](https://github.com/monix/monix/issues/236): 
  `Task` and `Coeval` need `foreach` and `foreachL`
- [Issue #237](https://github.com/monix/monix/issues/237): 
  Introduce `monix.execution.misc.ThreadLocal`
- [Issue #238](https://github.com/monix/monix/issues/238):
  Add `Coeval.Attempt.get`
- [Issue #239](https://github.com/monix/monix/issues/239):  
  `Task.flatMap` loops should not be auto-cancelable by default
- [Issue #240](https://github.com/monix/monix/pull/240):
  Change type class encoding, provide optimal `Observable.tailRecM`,
  upgrade Cats to `0.8.x` 
- [Issue #251](https://github.com/monix/monix/issues/251):
  Provide instances for Scalaz `Catchable`
- [Issue #241](https://github.com/monix/monix/issues/241): `TestScheduler`'s 
  exposed `state` should return the `State` and not `Atomic[State]`
- [Issue #243](https://github.com/monix/monix/issues/243): 
  Add the `TrampolineScheduler` for the JVM, in addition to Javascript  
- [Issue #256](https://github.com/monix/monix/issues/256):
  Refine extension methods on `Scheduler`
- [Issue #264](https://github.com/monix/monix/issues/264):
  `AtomicNumber` classes need `getAndAdd` optimisation
- [Issue #262](https://github.com/monix/monix/issues/262):
  Add `TaskSemaphore` and `AsyncSemaphore`
- [Issue #263](https://github.com/monix/monix/issues/263): 
  Add `Observable.mapTask` and `Observable.mapFuture`
- [Issue #205](https://github.com/monix/monix/issues/205):
  Add `Observable.mapAsync` for parallel mapping over `Observable`
- [Issue #261](https://github.com/monix/monix/issues/261):
  Optimize the performance of the `Observable` buffers
- [Issue #254](https://github.com/monix/monix/issues/254)
  Rename `Observable.runWith` to `Observable.consumeWith`
- [Issue #242](https://github.com/monix/monix/issues/242):
  Add `Scheduler.cached` builder for the JVM

## Version 2.0.6 (Nov 2, 2016)

- Upgrade Scala to 2.12.0 final release
- Upgrade Scalaz to 7.2.7
- Upgrade Minitest to 0.27

## Version 2.0.5 (Oct 23, 2016)

- [Bug #247](https://github.com/monix/monix/issues/247):
  Avoid runtime reflection
- [Bug #248](https://github.com/monix/monix/pull/248): 
  Reset overflow counter on None onOverflow result
- Updates Scala.js to 0.6.13 and Scala to 2.12.0-RC2

## Version 2.0.4 (Oct 10, 2016)

- [Bug #244](https://github.com/monix/monix/issues/244):
  AsyncScheduler.scheduleAtFixedRate and scheduleWithFixedDelay 
  (on the JVM) have incorrect behavior

## Version 2.0.3 (Oct 3, 2016)

- [Bug #230](https://github.com/monix/monix/issues/230):
  Deadlock when blocking threads due to `LocalBatchingExecutor`
  (affects `Task` usage)
      
## Version 2.0.2 (Sat 25, 2016)

- [Issue #224](https://github.com/monix/monix/issues/224):
  `IllegalStateException` logged in parallel consumer, 
  when streaming gets canceled due to a subscriber triggering 
  an error          

## Version 2.0.1 (Sat 10, 2016)

- [Issue #218](https://github.com/monix/monix/issues/218):
  Upgrade Cats to `0.7.2`, Scalaz to `7.2.6` and support
  Scala `2.12.0-RC1`

## Version 2.0.0 (Aug 31, 2016)

- [Issue #216](https://github.com/monix/monix/pull/216):
  Change type class design in `monix.types` to an encoding
  inspired by the [Scato](https://github.com/aloiscochard/scato) and
  [Scalaz 8](https://github.com/scalaz/scalaz/tree/series/8.0.x),
  cleaning up the available types; also enable 2.12.0-M5 support, 
  although releases are not automatic, because Cats doesn't yet 
  support Scala 2.12

## Version 2.0-RC13 (Aug 19, 2016)

Emergency bug fix:

- [Issue #215](https://github.com/monix/monix/issues/215): 
  the instance created by `Task.gatherUnordered` keeps state and
  has problems running (with `runAsync`) a second time
  

## Version 2.0-RC12 (Aug 19, 2016)

Bug fixes:

- [Issue #211](https://github.com/monix/monix/issues/211):
  `CompositeCancelable.remove` wasn't working after the changes in
  RC10, fixed it in RC11 and now added some more tests
- [Issue #213](https://github.com/monix/monix/pull/213): Fixes
  `Task` / `Coeval` memoize operation
  
Enhancements:

- [Issue #212](https://github.com/monix/monix/issues/212): Upgraded
  Cats to version 0.7.0
- [PR #214](https://github.com/monix/monix/pull/214): optimize Task,
  refactorings, some deprecations (details below)

Details on [PR #214](https://github.com/monix/monix/pull/214):

- Upgraded the Cats dependency to version 0.7.0. Had some trouble with
  that (see
  [cats#1329](https://github.com/typelevel/cats/issues/1329)), but it
  is now functional
- Renamed `eval` to `evalAlways` across the board (in `Task`, `Coeval`
  and `Observable`), but kept `evalAlways` with the `@deprecated`
  sign, so upgrade should be smooth. The reason is that `evalAlways`
  is an often used operation and deserves a shorter name  
- For Scalaz converts introduced `Task.delay` as an alias of
  `Task.eval`, `Task.suspend` as an alias of `Task.defer` and
  `Task.async` as an alias of `Task.create`
- Renamed `Task.eval(Coeval)` to `Task.coeval(Coeval)` and
  `Observable.eval(Coeval)` to `Observable.coeval(Coeval)` in order to
  avoid a conflict
- Removed `Task.both` because it is just an alias for `Task.mapBoth`. Judging
  in retrospective, we probably should've added it a `@deprecated` warning
  instead; on the other hand `both` and `mapBoth` are so close that the IDE
  will probably suggest `mapBoth` (at least IntelliJ IDEA does)
- Refactor the `Task` internals again, for optimizations and simplifications:  
  - Simplified the internal states, e.g. instead of having `Now`,
    `Error`, `Always` and `Once`, we now have a single
    `Delay(coeval)`, thus reusing the `Coeval` type for computing
    asynchronous values  
  - Get rid of the `Task.Attempt` type, it never made any sense that
    one. People can use `Coeval.Attempt` if they need a `Try`
    alternative (and convert to `Task` if they end up needing a
    `Task`)
  - Introduced `Scheduler.executeAsync` and `Scheduler.executeLocal`
    as extension methods powered by macros, for zero-overhead, because
    building `Runnable` instances is too annoying
  - Used `Scheduler.executeLocal` and `LocalRunnable` in key points in
    the `Task` implementation to reduce forking    
  - Made `Task.gather` be based on `Task.gatherUnordered` and it is
    now way faster
- Moved everything from `monix.types.shims` to `monix.types`

## Version 2.0-RC11 (Aug 19, 2016)

Bug fixes:

- [Issue #207](https://github.com/monix/monix/issues/207): Task
  flatMap loop isn't cancelable  
- [Issue #210](https://github.com/monix/monix/pull/210): Fixed
  `CompositeCancelable.remove`, a bug introduced in the last release
  (RC10)

Enhancements:

- [Issue #208](https://github.com/monix/monix/pull/208): Uniquely
  name threads generated from ThreadFactory
- [Issue #210](https://github.com/monix/monix/pull/210): Refactorings
  for performance and coherence reasons, described below

Issue #210 changes for the `monix-execution` sub-project:

- introduced the `CallbackRunnable` interface for marking `Runnable`
  instances that could be executed on the current thread, on a local
  trampoline, as an optimization
- introduced `LocalBatchingExecutor`, a mixin for schedulers that can
  execute `CallbackRunnable` locally, using a trampoline
- made `AsyncScheduler` for the JVM be able to execute
  `CallbackRunnable` instances by inheriting from
  `LocalBatchingExecutor`; but not on top of Javascript
- fixed critical bug in `CompositeCancelable` that was introduced in
  the last release

Issue #210 changes for the `monix-eval` sub-project:

- optimized `Task.fromFuture` to the point that it has near zero
  overhead

- optimized `Task.gather`, `Task.gatherUnordered`, `Task.sequence`
- `Task.gather` now forces asynchronous execution for the given tasks
- `Task.gatherUnordered` also forces asynchronous execution
- optimized the `Task` trampoline in general
- introduced `Callback.async` wrapper and `asyncApply` extension
  method
- renamed `Task.zipWith` to `Task.zipMap` (rename across the board,
  also for `Observable`)
- added `Task.executeOn` for overriding the `Scheduler`
- added `Task.fork` overload with a scheduler override
- added `Task.async` as an alias of `Task.create`

Issue #210 changes for the `monix-types` sub-project:

- moved all shims to `monix.types.shims`, in order to differentiate
  them from type classes that are not shims  
- added the `Deferrable` type class, to express lazy evaluation
  concerns (e.g. `evalOnce`, `evalAlways`, `defer`, `memoize`)
- added the `Evaluable` type class, for computations that will
  eventually produce a value

Issue #210 changes for the `monix-reactive` project:

- for `foldLeft` methods, make the seed be just a normal
  by-name parameter instead of a `Coeval`, because otherwise
  it isn't compatible with other type classes / interfaces
- affected methods are `foldLeft`, `foldLeftAsync`
- rename `zipWith` to `zipMap`
- rename `combineLatestWith` to `combineLatestMap`
- add `Observable.fromAsyncStateAction`
- fix `Consumer.foldLeftAsync`

## Version 2.0-RC10 (Aug 10, 2016)

Enhancements:

- [Issue #200](https://github.com/monix/monix/issues/200): Add an
  `executeNow` extension method for `Scheduler`, taking a by-name callback,
  as initializing `Runnable` instances is too annoying
- [Issue #201](https://github.com/monix/monix/issues/201): Fixes and
  optimizes `Task.gatherUnordered` - as an edge-case, it wasn't stack
  safe and it has been optimized to be more efficient
- [Issue #202](https://github.com/monix/monix/issues/202): Added
  `asyncOnSuccess` and `asyncOnError` as extension methods for `Callback`
  and made `Task.create` safe by forcing an asynchronous boundary when
  calling the callback (onSuccess/onError); also optimizes
  `Task.chooseFirstOfList` and `TestScheduler`
- [Issue #203](https://github.com/monix/monix/issues/203): Changed
  the `onOverflow` function signature for `OverflowStrategy` types
  supporting it - it can now return an `Option` and if it returns
  `None`, then we don't signal any messages downstream, thus making
  it easier to just log that overflow happened without extra tricks

## Version 2.0-RC9 (Jul 31, 2016)

Bug fixes:

- [Issue #193](https://github.com/monix/monix/issues/193): Task Applicative
  instance doesn't run the tasks in parallel
- [Issue #194](https://github.com/monix/monix/issues/194): `Task.mapBoth` is
  not stack-safe

Enhancements:

- [Issue #196](https://github.com/monix/monix/issues/196): Add the
  `Consumer.create` builder
- [Issue #166](https://github.com/monix/monix/issues/166): Generalize
  `Task.sequence`, `Task.gather` and `Task.gatherUnordered` for arbitrary
  collections

Misc:

- updated Scala.js version to 0.6.11
- updated Cats version to 0.6.1
- changed PGP signing key
- increased timeout on the reactive streams publisher tests

## Version 2.0-RC8 (Jun 29, 2016)

**Critical bug fix:**

- [BUG #181](https://github.com/monix/monix/issues/181): the default operators
  on `CancelableFuture` are triggering `StackOverflow` exceptions

New Features:

- [Issue #184](https://github.com/monix/monix/pull/184): introducing the
  `Consumer` type, a factory of subscribers that makes it easier to specify
  reusable and composable consumers and for example, it makes it possible,
  out of the box, to load-balance the workload between multiple subscribers in
  parallel; see the description
- [Issue #186](https://github.com/monix/monix/pull/186) (related to
  [issue #168](https://github.com/monix/monix/issues/168)): adds the
  `Observable.interleave2` operator, similar with the one in `FS2`
  (former scalaz-streams)
- [Issue #180](https://github.com/monix/monix/issues/180): the `Observer.feed`
  function now has an overload that does not take a cancelable, because
  it's awkward coming up with one if it isn't needed; there's also a
  `onNextAll` extension for both `Observer` and `Subscriber` which can push
  a whole collection of events
- [Issue #187](https://github.com/monix/monix/issues/187): integrates
  the `MonadCombine` type class from [Cats](https://typelevel.org/cats/),
  being similar to the Scalaz `MonadPlus`, as somehow this was missed in
  the initial integration
- [Issue #177](https://github.com/monix/monix/issues/177) reviews exposed
  traits and abstract classes, making sure they inherit from `Serializable`
- [Issue #85](https://github.com/monix/monix/issues/85): small change,  
  clarifies the ScalaDoc on the `RefCountCancelable` type
- [Issue #162](https://github.com/monix/monix/issues/162): implements
  the `Observable.takeUntil(trigger: Observable[Any])` operator, an operator
  that takes from the source until another observable triggers an event
- [Issue #189](https://github.com/monix/monix/issues/189): for `Observable`
  operators that return a single item (e.g. `sumF`, `foldLeftF`, etc.) adds
  variants that do the same thing, but return `Task` results instead, so now
  we have `foldLeftL`, `sumL`, etc. that return tasks
- [Issue #190](https://github.com/monix/monix/issues/190): changes many
  observable operators, that were taking by-name parameters or initial state
  parameters (e.g. `headOrElseF`, `foldLeftF`, etc), to take `Coeval` params
  instead, in order to have fine control over the evaluation model
- [Issue #191](https://github.com/monix/monix/issues/191): introduces
  by default an implicit conversion from `Any` to `Coeval.Now`, to make it
  easier to use `Coeval` as function parameters - the default will thus
  simply be strict evaluation, strictness being usually the default in Scala

## Version 2.0-RC7 (Jun 21, 2016)

**Bug fixes:**

- [BUG #170](https://github.com/monix/monix/issues/170):
  `Task.materializeAttempt` doesn't work for `BindAsync`, leading to `onErrorHandleWith`
  not working with errors triggered in `flatMap` on async tasks

New Features:

- [Issue #171](https://github.com/monix/monix/issues/171):
   Add Scheduler builder on the JVM that allows specifying
   just the `ExecutionModel`, falling back `global` otherwise   
- [Issue #174](https://github.com/monix/monix/issues/174):
  [Scalaz](https://github.com/scalaz/scalaz) integration (in addition to the
  Cats integration) for FP goddess
- [Issue #175](https://github.com/monix/monix/issues/175):
  Reintroduce all of project `Sincron` into `monix-execution`, which means
  that now `monix-execution` exposes `Atomic` references. This project
  was split from Monix, but the decision didn't make sense and the exposed
  functionality is super useful and in the spirit of `monix-execution`
- [Issue #176](https://github.com/monix/monix/issues/176): now that we
  have `Task`, we introduce `TaskApp`, a safe `App` type that allows one
  to specify pure programs

## Version 2.0-RC6 (Jun 15, 2016)

**Breaking changes:**

- [Issue #157](https://github.com/monix/monix/issues/157): Renaming
  `Observable.doOnCancel` to `doOnSubscriptionCancel`
  because its meaning has changed since Monifu 1.x and it will cause pain
- [Issue #160](https://github.com/monix/monix/issues/160) (**breaking change**):
  Revamp the buffer operators on `Observable`
- [Issue #161](https://github.com/monix/monix/issues/161) (**breaking change**):
  Revamp the ConcurrentSubject and Pipe constructors

Bug fixes and enhancements:

- [Bug #153](https://github.com/monix/monix/issues/153): already fixed in
   `2.0-RC5` for `Task`, but now also fixed for `Coeval`
- [Issue #155](https://github.com/monix/monix/issues/155): Coeval enhancements
   and fixes
- [Issue #156](https://github.com/monix/monix/issues/156): Adding `Observable.doOnTerminate`
  for executing a piece of logic when `onComplete` or `onError` happens, or
  when the subscriber stops the streaming with `Stop`
- [Issue #158](https://github.com/monix/monix/issues/158): `Observable.fromIterator`
  takes now an optional `onFinish` callback for resource deallocation, because
  the `Iterator` interface doesn't provide a `close()` that could be used
- [Issue #163](https://github.com/monix/monix/issues/163): added
  `Observable.fromInputStream`, `fromCharsReader` and `fromLinesReader` for
  reading from `java.io.InputStream`, `java.io.Reader` and `java.io.BufferedReader`
  data sources
- [Issue #164](https://github.com/monix/monix/issues/164): Add
  `Observable.executeOn`, as alternative to `observeOn`
- [Issue #165](https://github.com/monix/monix/issues/165): Simplification
  of the Cats integration

## Version 2.0-RC5 (May 31, 2016)

Critical bug fix:

- [Bug 153](https://github.com/monix/monix/issues/153) - Task.sequence and Task.gather
  return a shared mutable.ListBuffer

## Version 2.0-RC4 (May 31, 2016)

- [Issue #89](https://github.com/monix/monix/issues/149) - reintroducing a minimal
  Cats integration, along with tests based on `cats-laws`. We are splitting `monix.type`
  into its own sub-project and `monix-cats` depends on it. This ensures that the pick what
  you use approach also works with `monix-cats`, as people wanting just `Task` should not
  get `Observable`, yet `Observable` needs integration as well.
- [Issue #149](https://github.com/monix/monix/issues/149) - documentation related fixes
  - Improved the description of `Task`
  - `Task.unit` is now a final val
  - `Task.never` now shares the reference instead of building a new instance every time
  - Exposing `Task.unsafeStartNow` and `Task.unsafeStartAsync`, as otherwise `Task.unsafeCreate`
    is useless. So we should expose all of them, or none at all.
  - `FutureUtils.Extensions.dematerialize` was named "materialize" (WTF!) and is renamed
  - Task should inherit just from `Serializable` and not from `Product`
- [Issue #150](https://github.com/monix/monix/issues/150) - add a new `Task.doOnFinish`
  operator that executes once a task is finished.
- [Issue #151](https://github.com/monix/monix/issues/151) - changing `Future.sequence` to be
  ordered in both execution and effects
- [Issue #152](https://github.com/monix/monix/issues/152) - introduce `Task.gather` which
  behaves like the previous `sequence` and `Task.gatherUnordered` which doesn't do ordering
  for results either.  

## Version 2.0-RC3

- [Issue #147](https://github.com/monix/monix/issues/147) - Make `BehaviorSubject` and `ReplaySubject`
  remove subscribers that triggered `Stop` while connecting, thus freeing
  the memory sooner, otherwise the GC cannot free the subscriber because
  its reference is kept captive until the next `Subject.onNext`
- Remove `tut` and `site` from `project/plugins`, as the documentation will
  be handled in the [monix.io](https://github.com/monix/monix.io) repository
- Re-enable code coverage in Travis and fix the build to actually test Scala 2.10.6

## Version 2.0-RC2

Minor release with a single bug fix:

- [Issue #144](https://github.com/monix/monix/issues/144) - fix Observable.zip

## Version 2.0-RC1

Feature freeze. There's lots of interesting stuff to work on, but I'd rather have
a stable 2.0 release first.

Bug fix:

- [Issue #143](https://github.com/monix/monix/issues/143) - on RefCountObservable cancel and stop should be idempotent


## Version 2.0-M2

The theme and the big issue of this release has been redesigning `Task`'s
implementation and introducing `Coeval`. See [Issue #141](https://github.com/monix/monix/issues/141)
for details.

List of changes:

- [Issue #88](https://github.com/monix/monix/issues/88): the `Task` implementation
  has been redesigned from scratch
- [Issue #89](https://github.com/monix/monix/issues/89): Cats integration
  has been tried and yielded very positive results, but is being delayed
- [Issue #96](https://github.com/monix/monix/issues/96)
  and [issue 99](https://github.com/monix/monix/issues/99): add `MulticastStrategy` for
  safer building of multicast Observables
- [Issue #127](https://github.com/monix/monix/issues/127):
  Introduce operators onErrorHandle and onErrorHandleWith
- [Issue #128](https://github.com/monix/monix/issues/128): operators
  materialize, dematerialize and memoize for `Task` (and `Coeval`)
- [Issue #113](https://github.com/monix/monix/issues/113): Introduce the `bufferIntrospective`
  operator
- [Issue #123](https://github.com/monix/monix/issues/123): underlying protocol
  changes, did some fixes for the work that already happened for `M1`
- [Issue #131](https://github.com/monix/monix/issues/131): renamed `Ack.Cancel`
  to `Ack.Stop` in order to differentiate it as a verb from `Cancelable.cancel`,
  because in version `2.0` they are two different actions
  (and it's more semantically correct this way)
- [Issue #132](https://github.com/monix/monix/issues/132): introduced the
   `Observable.onCancelTriggerError` operator
- [Issue #133](https://github.com/monix/monix/issues/133): introduced the
   `Observable.doOnDownstreamStop` and `doOnCancel`
   (which is one reason for #131)
- [Issue #134](https://github.com/monix/monix/issues/134):
   New operator `Observable.switchIfEmpty`
- [Issue #136](https://github.com/monix/monix/issues/136):
   Clarify reactive streams, initiated `monix.execution.rstreams` for
   reusable subscription types and added the `ReactivePublisher` type class
   for things that can be converted to `org.reactivestreams.Publisher`
   (both Observable and Task are instances)
- [Issue #140](https://github.com/monix/monix/issues/140):
   Add type class hierarchy, to be integrated with both Cats and Scalaz
- [Issue #141](https://github.com/monix/monix/issues/141): reimplement Task
  from scratch, introduce Coeval, introduce Scheduler.executionModel

## Version 2.0-M1

Milestone release for version 2.0, supplied as a preview.
The final 2.0 release should not have major changes compared
to this first milestone, but API breakage may still happen.

This version is not production ready. Use at your own risk.

List of changes:

- [Issue #60](https://github.com/monix/monix/issues/60) - initiated the `docs` sub-project with
  tutorials type-checked as part of the test process
- [Issue #88](https://github.com/monix/monix/issues/88) - add Monix's own `Task`, an alternative
  to `Future` and the Scalaz `Task`
- [Issue #89](https://github.com/monix/monix/issues/89) - initial integration
  with [Cats](https://github.com/typelevel/cats)
- [Issue #90](https://github.com/monix/monix/issues/90) - remove functionality that isn't
  relevant to the purpose of Monix and be supported
- [Issue #91](https://github.com/monix/monix/issues/91) - project rename and reorganization (epic API breakage)
- [Issue #93](https://github.com/monix/monix/issues/93) - renamed `Observable.onSubscribe` to
  `Observable.unsafeSubscribeFn`, because it shouldn't be used directly, unless you really, really
  know what you're doing
- [Issue #94](https://github.com/monix/monix/issues/94) - fixed comments style to ScalaDoc
- [Issue #96](https://github.com/monix/monix/issues/96) - removed `Observable.create` because
  it is unsafe
- [Issue #100](https://github.com/monix/monix/issues/100) - enhanced the `Scheduler` interface
- [Issue #102](https://github.com/monix/monix/issues/102) - brought back `sun.misc.Unsafe`
  (actually this was done in separate project, as part of [issue #104](https://github.com/monix/monix/issues/104))
- [Issue #104](https://github.com/monix/monix/issues/104) - separated the `Atomic` implementations
  in the [Sincron](https://github.com/monix/sincron)
- [Issue #106](https://github.com/monix/monix/issues/106) - enabled code coverage reports
- [Issue #111](https://github.com/monix/monix/issues/111) - improved test coverage
- [Issue #114](https://github.com/monix/monix/issues/114) - initiated the `benchmarks` sub-project to
  track performance issues
- [Issue #115](https://github.com/monix/monix/issues/115) - `Cancelable.cancel` now returns `Unit`
  and not `Boolean`
- [Issue #118](https://github.com/monix/monix/issues/118) - Fix `materialize`, add `dematerialize`
- [Issue #119](https://github.com/monix/monix/issues/119) - Introduced `Pipe`,
  renamed `Channel` in `ConcurrentSubject`, renamed `SynchronousObserver` in `SyncObserver`,
  renamed `SynchronousSubscriber` in `SyncSubscriber`
- [Issue #121](https://github.com/monix/monix/issues/121) - Clarified the contract for synchronous
  pipelines, got rid of the hacks from `internal`, replacing them with macro-driven extensions
  for `Future[Ack]`
- [Issue #123](https://github.com/monix/monix/issues/123) - Changed the protocol of `Observable.subscribe`,
  back-pressuring `onComplete` and `onError` is now optional and `Observable.unsafeSubscribeFn` needs
  to return a usable `Cancelable` instead of `Unit` (big internal change)
- [Issue #125](https://github.com/monix/monix/issues/125) - Modified contract of
  `AsyncScheduler`, added a new `ExecutorScheduler`

## Version 1.2

- [Issue #113](https://github.com/monix/monix/issues/113) - Introduce the `bufferIntrospective`
  operator

## Version 1.1

- [Issue #125](https://github.com/monix/monix/issues/125) - Modify contract of AsyncScheduler,
  and add a new Scheduler type based entirely on Java's ScheduledExecutor
- Update versions of SBT dependencies
- Documentation changes (fixed missing argument in Observable docs, add code of conduct mention)
