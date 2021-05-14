## Version 3.4.0 (May 14, 2021)

The release is binary and source compatible with 3.x series, and was cross-built for the following Scala and ScalaJS versions:

- Scala 2.13 and 3.0
- Scala.js 1.5.1

WARN: we're dropping compatibility with Scala 2.11 and ScalaJS 0.6.x. If you still need those you'll have to stay on version `3.3.0`.

Changes in this release:

- Dropped support for Scala 2.11 and Scala.js 0.6.x
- Dependency updates:
  - Cats 2.6.1
  - Cats-Effect 2.5.1
  - JCTools 3.3.0
- Adds support for Scala 3 (#1326, #1327, #1328, #1329, #1344, #1323)
- Adds `Observable.whileBusyAggregateEvents` (#1320)
- Fix tracing in `Coeval` and `Task` via a more accurate filter (#1353)
- Adds `Observable.throttleLatest` (#1396)
- Implement pagination for `Observable` (#1381)

This release was made possible by the work and feedback of:

- Alexandru Nedelcu (@alexandru)
- Dominik Wosiński (@Wosin)
- Lars Hupel (@larsrh)
- Luke Stephenson (@lukestephenson)
- Oleg Pyzhcov (@oleg-py)
- Pau Alarcón (@paualarco)
- Piotr Gawryś (@Avasil)

## Version 3.3.0 (Nov 7, 2020)

The release is binary and source compatible with 3.x.x line.
It is released for the following Scala and ScalaJS versions:
- Scala 2.11: ScalaJS 0.6.x
- Scala 2.12: ScalaJS 0.6.x and 1.3.x
- Scala 2.13: ScalaJS 0.6.x and 1.3.x

Note that most likely, this is going to be the last release on ScalaJS 0.6.x.
We can consider doing backports on-demand.

## Highlights

### Better Stack Traces

This release includes a highly requested feature of better stack traces for `Task` and `Coeval`!
Big thanks to @RaasAhsan and @djspiewak for providing the original implementation that we have ported.

They are enabled by default, but it is configurable. 
Refer to [Stack Traces section](https://monix.io/docs/current/eval/stacktraces.html) for more details.

We have measured about 10-30% performance hit in CACHED mode (the default) in microbenchmarks.
If you have any performance tests, we would greatly appreciate any reports!
If the hit is too big, you can disable the stack traces with `-Dmonix.eval.stackTracingMode=none`.

For the following code:

```scala 
package test.app

import monix.eval.Task
import monix.execution.Scheduler
import cats.implicits._
import scala.concurrent.duration._

object TestTracingApp extends App {
  implicit val s = Scheduler.global

  def customMethod: Task[Unit] =
    Task.now(()).guarantee(Task.sleep(10.millis))

  val tracingTestApp: Task[Unit] = for {
    _ <- Task.shift
    _ <- Task.unit.attempt
    _ <- (Task(println("Started the program")), Task.unit).parTupled
    _ <- customMethod
    _ <- if (true) Task.raiseError(new Exception("boom")) else Task.unit
  } yield ()

  tracingTestApp.onErrorHandleWith(ex => Task(ex.printStackTrace())).runSyncUnsafe
}
```

The default (cached) stack trace is going to be:

``` 
java.lang.Exception: boom
        at test.app.TestTracingApp$.$anonfun$tracingTestApp$5(TestTracingApp.scala:36)
        at guarantee @ test.app.TestTracingApp$.customMethod(TestTracingApp.scala:29)
        at flatMap @ test.app.TestTracingApp$.$anonfun$tracingTestApp$4(TestTracingApp.scala:35)
        at parTupled @ test.app.TestTracingApp$.$anonfun$tracingTestApp$2(TestTracingApp.scala:34)
        at parTupled @ test.app.TestTracingApp$.$anonfun$tracingTestApp$2(TestTracingApp.scala:34)
        at flatMap @ test.app.TestTracingApp$.$anonfun$tracingTestApp$2(TestTracingApp.scala:34)
        at flatMap @ test.app.TestTracingApp$.$anonfun$tracingTestApp$1(TestTracingApp.scala:33)
        at flatMap @ test.app.TestTracingApp$.delayedEndpoint$test$app$TestTracingApp$1(TestTracingApp.scala:32)
```

Before `3.3.0` and with stack traces disabled, stack traces are a mess:

```
java.lang.Exception: boom
        at test.app.TestTracingApp$.$anonfun$tracingTestApp$5(TestTracingApp.scala:36)
        at monix.eval.internal.TaskRunLoop$.startFull(TaskRunLoop.scala:188)
        at monix.eval.internal.TaskRestartCallback.syncOnSuccess(TaskRestartCallback.scala:101)
        at monix.eval.internal.TaskRestartCallback$$anon$1.run(TaskRestartCallback.scala:118)
        at monix.execution.internal.Trampoline.monix$execution$internal$Trampoline$$immediateLoop(Trampoline.scala:66)
        at monix.execution.internal.Trampoline.startLoop(Trampoline.scala:32)
        at monix.execution.schedulers.TrampolineExecutionContext$JVMNormalTrampoline.super$startLoop(TrampolineExecutionContext.scala:142)
        at monix.execution.schedulers.TrampolineExecutionContext$JVMNormalTrampoline.$anonfun$startLoop$1(TrampolineExecutionContext.scala:142)
        at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
        at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:94)
        at monix.execution.schedulers.TrampolineExecutionContext$JVMNormalTrampoline.startLoop(TrampolineExecutionContext.scala:142)
        at monix.execution.internal.Trampoline.execute(Trampoline.scala:40)
        at monix.execution.schedulers.TrampolineExecutionContext.execute(TrampolineExecutionContext.scala:57)
        at monix.execution.schedulers.BatchingScheduler.execute(BatchingScheduler.scala:50)
        at monix.execution.schedulers.BatchingScheduler.execute$(BatchingScheduler.scala:47)
        at monix.execution.schedulers.AsyncScheduler.execute(AsyncScheduler.scala:31)
        at monix.eval.internal.TaskRestartCallback.onSuccess(TaskRestartCallback.scala:72)
        at monix.eval.internal.TaskRunLoop$.startFull(TaskRunLoop.scala:183)
        at monix.eval.internal.TaskRestartCallback.syncOnSuccess(TaskRestartCallback.scala:101)
        at monix.eval.internal.TaskRestartCallback.onSuccess(TaskRestartCallback.scala:74)
        at monix.eval.internal.TaskSleep$SleepRunnable.run(TaskSleep.scala:71)
        at java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1402)
        at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
        at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
        at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
        at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)
```

### Better Task => Future interop when using Local

Running `Task` isolates `Local`, which was not available in the `Future`, resulting in `runToFuture`.
This release enables it and unblocks compelling use cases, such as reading proper request context in Akka HTTP Directive.
We've created an [AkkaHTTP Example](https://github.com/Avasil/akka-monix-local-example/blob/master/src/main/scala/AkkaHTTPExample.scala)
that demonstrates it.

Latest behavior is:

```scala
implicit val s: Scheduler = Scheduler.Implicits.traced

val local = Local(0)

for {
  _  <- Task(local.update(1)).runToFuture
  value <- Future(local.get)
} yield println(s"Local value in Future $value")

println(s"Local value on the current thread = $value")

// => Local value on the current thread = 0
// => Local value in Future = 1
```

`Task` still isolates the `Local`, but the `Future` continuation keeps the same reference and can read it.
Before the change, `Local` would be `0` in the `Future`.

More information about `Local` can be found in the new [Local documentation](https://monix.io/docs/current/execution/local.html).

### Relevant updates

- [#1205](https://github.com/monix/monix/pull/1205): Observable.mergePrioritizedList
- [#1209](https://github.com/monix/monix/pull/1209): Bring back Observable.transform and Transformer alias
- [#1198](https://github.com/monix/monix/pull/1198): Fix flatMapIterable calling recursively itself
- [#1213](https://github.com/monix/monix/pull/1213): Propagate Local isolation in runToFuture
- [#1217](https://github.com/monix/monix/pull/1217): Fix performance regression in bufferSliding
- [#1244](https://github.com/monix/monix/pull/1244): Add support for compression: Gzip and deflate
- [#1262](https://github.com/monix/monix/pull/1262): Fix bug in Task.runSyncUnsafe related to ContextSwitch
- [#1265](https://github.com/monix/monix/pull/1265): Implement Observable#bufferWhile and bufferWhileInclusive
- [#1267](https://github.com/monix/monix/pull/1267): Implement Asynchronous Stack Traces for Task
- [#1276](https://github.com/monix/monix/pull/1276): Add Task/Coeval convenience methods like .when 
- [#1282](https://github.com/monix/monix/pull/1282): Add 'as' in Task and Coeval
- [#1284](https://github.com/monix/monix/pull/1284): Add left/right builders for Task and Coeval
- [#1286](https://github.com/monix/monix/pull/1286): Add none/some builders for Task and Coeval
- [#1291](https://github.com/monix/monix/pull/1291): tapEval, tapError methods at Task and Coeval
- [#1293](https://github.com/monix/monix/pull/1293): removing no-op onComplete() in Observable.takeByTimespan
- [#1299](https://github.com/monix/monix/pull/1299): Fix a bug in Local.value
- [#1307](https://github.com/monix/monix/pull/1307): Observable.fromIteratorBuffered


### People who made this release possible

- Adrian (@adrian-salajan)
- Alexandru Nedelcu (@alexelcu)
- ctoomey (@ctoomey)
- Dmitro Pochapsky (@pchpsky)
- Georgy Khotyan (@GKhotyan)
- James Yoo (@jyoo980)
- Kasper Kondzielski (@ghostbuster91)
- Pau Alarcón (@paualarco)
- Piotr Gawryś (@Avasil)
- Sandeep Kota (@sandeepkota)
- tafit3 (@tafit3)
- Vladyslav (@VladPodilnyk)

## Version 3.2.2 (June 4, 2020)

The release is binary and source compatible with 3.x.x line.
It is released for the following Scala and ScalaJS versions:
- Scala 2.11: ScalaJS 0.6.x
- Scala 2.12: ScalaJS 0.6.x and 1.0.x
- Scala 2.13: ScalaJS 0.6.x and 1.0.x

Relevant updates:

- [#1197](https://github.com/monix/monix/pull/1197): Fixes a memory leak in bracket, introduced in 3.2.0
- [#1195](https://github.com/monix/monix/pull/1195): Fixes non-deterministic behavior in Observable.zip of sources different size
- [#1190](https://github.com/monix/monix/pull/1190): Now Observable.groupBy correctly signals error as a failed stream instead of normal completion
- [#1188](https://github.com/monix/monix/pull/1188): Fixes an issue in ForeachAsyncConsumer where it wasn't properly propagaing errors in some cases
- [#1187](https://github.com/monix/monix/pull/1187): Fixes an issue where Observable.doAfterSubscribe was not executing its finalizer
- [#1186](https://github.com/monix/monix/pull/1186): Observable.interval method now schedules the first tick asynchronously
- [#1184](https://github.com/monix/monix/pull/1184): Huge performance improvement in CharsReader, InputStream and LinesReaderObservable
- [#1154](https://github.com/monix/monix/pull/1161): Observable.fromInputStream and fromCharsReader now respects the chunkSize
- [#1181](https://github.com/monix/monix/pull/1181): Fix MatchError in Iterant.fromReactivePublisher

People who made this release possible:

- Akosh Farkash (@aakoshh)
- Alexander (@ppressives)
- fnqista (@fnqista)
- Kasper Kondzielski (@ghostbuster91)
- Pau Alarcón (@paualarco)
- Piotr Gawryś (@Avasil)
- Vasily Kirichenko (@vasily-kirichenko)
- Viktor Lövgren (@vlovgr)

## Version 3.2.1 (April 30, 2020)

Bug fix release, fixing a critical issue with ScalaJS 1.0.x [#1666](https://github.com/monix/monix/issues/1166) and a corner case with `Bracket` [#1175](https://github.com/monix/monix/pull/1175)

## Version 3.2.0 (April 26, 2020)

The release is binary and source compatible with 3.x.x line.
It is released for the following Scala and ScalaJS versions:
- Scala 2.11: ScalaJS 0.6.x
- Scala 2.12: ScalaJS 0.6.x and 1.0.x
- Scala 2.13: ScalaJS 0.6.x and 1.0.x

Relevant updates:

- [#1087](https://github.com/monix/monix/pull/1087): Bracket should not evaluate "use" if Task was canceled during "acquire"
- [#1098](https://github.com/monix/monix/pull/1098): Allow to pass Long in Observable.drop(n)
- [#1101](https://github.com/monix/monix/pull/1101): Canceled tasks in Half-Open state should return to Open state in CircuitBreaker
- [#1107](https://github.com/monix/monix/pull/1107): Add CommutativeApplicative[Task.Par] instance
- [#1106](https://github.com/monix/monix/pull/1106) and [#1125](https://github.com/monix/monix/pull/1125): Add Observable.concatMapIterable
- [#1117](https://github.com/monix/monix/pull/1117): SyncEffect instance for Coeval
- [#1120](https://github.com/monix/monix/pull/1120): Cancel should always wait for the finalizer
- [#1126](https://github.com/monix/monix/pull/1126): bufferIntrospective should signal Stop upstream when it is back-pressured
- [#1135](https://github.com/monix/monix/pull/1135): Observable.intervalAtFixedRate under-millisecond interval fix
- [#1132](https://github.com/monix/monix/pull/1132): Add Iterant.withFilter
- [#1129](https://github.com/monix/monix/pull/1129): Add Observable.withFilter
- [#1145](https://github.com/monix/monix/pull/1145): Deprecate gather, gatherN, gatherUnordered in favor of parSequence, parSequenceN, parSequenceUnordered
- [#1152](https://github.com/monix/monix/pull/1152): Add Consumer.toList

People that made this release possible:

- Allan Timothy Leong (@allantl)
- Eugene Platonov (@jozic)
- Fabien Chaillou (@fchaillou)
- Gabriel Claramunt (@gclaramunt)
- Mantas Indrašius (@mantasindrasius)
- TapanVaishnav (@TapanVaishnav)
- najder-k (@najder-k)
- Oleg Pyzhcov (@oleg-py)
- Pau Alarcón (@paualarco)
- Piotr Gawryś (@Avasil)
- Ross A. Baker (@rossabaker)
- Viet Yen Nguyen (@nguyenvietyen)
- Viktor Dychko (@dychko)

## Version 3.1.0 (November 8, 2019)

The release is binary and source compatible with 3.0.0.

Important updates:

- [#1008](https://github.com/monix/monix/pull/1008): Fixed stack safety issue of Observable.combineLatestList for big lists
- [#1010](https://github.com/monix/monix/pull/1010): flatMapLoop for Task and Coeval
- [#1012](https://github.com/monix/monix/pull/1012): ConcurrentQueue.isEmpty
- [#1014](https://github.com/monix/monix/pull/1014): Observable.timeoutOnSlowDownstreamTo
- [#1016](https://github.com/monix/monix/pull/1016): Observable.takeUntilEval
- [#1057](https://github.com/monix/monix/pull/1057): Fix default scheduleOnce implementation
- [#1062](https://github.com/monix/monix/pull/1062): Solves a memory leak which sometimes occurred when using Task with localContextPropagation and non-TracingScheduler
- [#1063](https://github.com/monix/monix/pull/1063): Ensure async boundary in TaskCreate if LCP is enabled
- [#1064](https://github.com/monix/monix/pull/1064) and [#1070](https://github.com/monix/monix/pull/1070): Earlier cancelation in Observable.mapParallel if any task fails
- [#1065](https://github.com/monix/monix/pull/1065): Add mapAccumulate to Observable 

People that made this release possible:

- Alexandru Nedelcu (@alexandru)
- Allan Timothy Leong (@allantl)
- fdilg (@fdilg)
- Jan Bracker (@jbracker)
- Moritz Bust (@busti)
- mudsam (@mudsam)
- Oleg Pyzhcov (@oleg-py)
- Paweł Kiersznowski (@pk044)
- Piotr Gawryś (@Avasil)
- tanaka takaya (@takayahilton)
- TapanVaishnav (@TapanVaishnav)

## Version 3.0.0 (September 11, 2019)

Important updates:

- [#875](https://github.com/monix/monix/pull/875) and [#1022](https://github.com/monix/monix/pull/1022): Update to Cats and Cats-Effect 2.0.0
- [#1018](https://github.com/monix/monix/pull/1018): Update Minitest to 2.7.0

Chores:

- [#996](https://github.com/monix/monix/pull/996): improve Observable.publishSelector scaladoc
- [#1017](https://github.com/monix/monix/pull/1017): disable Scalafmt checks in build
- [#1006](https://github.com/monix/monix/pull/1006): Update sbt to 1.3.0
- [#1013](https://github.com/monix/monix/pull/1013): Update sbt-sonatype to 3.6

People that made this release possible:

- Alexandru Nedelcu (@alexandru)
- Oleg Pyzhcov (@oleg-py)
- Piotr Gawryś (@Avasil)
- The Cats and Cats-Effect contributors that worked on and released 2.0.0

## Version 3.0.0-RC5 (August 29, 2019)

Bug fix release:

- [#991](https://github.com/monix/monix/issues/991) ([PR #993](https://github.com/monix/monix/pull/993)): `NullPointerException` in `Task` when used in combination with `Local`
- [#992](https://github.com/monix/monix/issues/992) ([PR #998](https://github.com/monix/monix/pull/998)): hide `InterceptableRunnable` exposed accidentally, refactor it for efficiency
- [#997](https://github.com/monix/monix/pull/997): bumped Scala version to 2.12.9 and Scala.js to 0.6.28

This release is binary compatible with `3.0.0-RC4`.

## Version 3.0.0-RC4 (August 25, 2019)

Last release before `3.0.0` which will be released as soon as Cats-Effect 2.0.0 is available. At the time of writing release notes,
it is only waiting on Cats 2.0.0 which is about to release next RC which will become stable version if no critical issues are discovered.

Any other development for this release is now frozen except if we discover critical bugs like memory leaks. All other changes will target `3.1.0`.

`3.0.0` will be binary and source compatible with `3.0.0-RC4` for Scala 2.12. Monix itself will be also binary compatible
for 2.11 but it will have a dependency on Cats-Effect which is not. [See Cats-Effect release notes](https://github.com/typelevel/cats-effect/releases/tag/v2.0.0-RC1) for more details.

We wish we could release `3.0.0` already but if we released now, we would have to release `4.0.0` for Cats-Effect 2.0 because of 2.11 incompatibility there so we decided to hold on.
It was a rough road but currently we have multiple active maintainers that can do releases going forward, instead of just Alex so we hope it can give you a confidence for the future! :)
Note that Cats-Effect 2.0 is very small release and mostly targeted at support for Scala 2.13 and bug fixes - the upgrade should be limited to bumping version without changing a single line of code.

This release depends on Cats-Effect 1.4.0 and Cats 1.6.1

This release is binary compatible with `3.0.0-RC3` on Scala 2.12.
On Scala 2.11 there is an incompatibility caused by introduction of `Scheduler.features`:

```
exclude[ReversedMissingMethodProblem]("monix.execution.Scheduler.features")
```

### Main changes

#### Local

There are several fixes related to `Local` usage.
- Using `TracingScheduler` will also automatically enable local context propagation in `Task` so
running it with `runToFutureOpt` or setting env variable is no longer a necessity.
- `Local.isolate` has a overload for isolating `Future` which is safer than regular `Local.isolate`.

The `Local` model since `3.0.0-RC3` shares by default. To isolate modifications of `Local` by
other concurrently running `Future`, you have to call `Local.isolate`.

In case of `Task`, there is a `TaskLocal.isolate` version. It is automatically called whenever you run `Task`
so if your use case is setting `correlationId` or similar, it probably won't require any explicit isolation because
your HTTP library will most likely run the `Task` per request.

#### Task Builders

There are two major improvements:
- Now all `Task` builders (`Task.create`, `Task.async`, `Task.fromFuture` etc.) will return a `Task` that continues on default `Scheduler` which is
the one supplied during execution unless overriden by `executeOn`.
- Callback in `Task.create` is now thread-safe against contract violation (calling it more than once) so does not require synchronization on the user side.


### Sub-project: monix-execution

- [PR #946](https://github.com/monix/monix/pull/946): Expose less implementation details in Local

- [PR #948](https://github.com/monix/monix/pull/948): Make Task.memoize play well with Local

- [PR #953](https://github.com/monix/monix/pull/953): Make default scheduler remove cancelled tasks

- [PR #960](https://github.com/monix/monix/pull/960): Scheduler.features

- [PR #971](https://github.com/monix/monix/pull/971): Callback tryOnSuccess/tryOnFailure

- [PR #973](https://github.com/monix/monix/pull/973): Fix Local.isolate corner case

- [PR #977](https://github.com/monix/monix/pull/977): Use type classes instead of overloads in Local.isolate/bind

### Sub-project: monix-eval

- [PR #913](https://github.com/monix/monix/pull/913): Optimize Task.guarantee

- [PR #921](https://github.com/monix/monix/pull/921) & [PR #917](https://github.com/monix/monix/pull/917): Callbacks in Task.create are now thread-safe and
always return to the main Scheduler.

- [PR #933](https://github.com/monix/monix/pull/933): Adds >> syntax to Task

- [PR #935](https://github.com/monix/monix/pull/935): Adds >> syntax to Coeval

- [PR #934](https://github.com/monix/monix/pull/934): Implement doOnFinish in terms of guaranteeCase

- [PR #951](https://github.com/monix/monix/pull/951): Add void to Task + Coeval

- [PR #952](https://github.com/monix/monix/pull/952): Implement ContextShift.evalOn in terms of Task.executeOn

- [PR #954](https://github.com/monix/monix/pull/954): Add gatherN + wanderN

- [PR #972](https://github.com/monix/monix/pull/972): Rename `Task.forkAndForget` to `Task.startAndForget`

### Sub-project: monix-reactive

- [PR #906](https://github.com/monix/monix/pull/906): Fix race in MapParallelOrderedObservable

- [PR #918](https://github.com/monix/monix/pull/918): switchMap should wait for last child to complete

- [PR #919](https://github.com/monix/monix/pull/919): propagate cancellation to tasks in async Consumers

- [PR #932](https://github.com/monix/monix/pull/932): Remove try-catch for EvalOnceObservable implementation

- [PR #941](https://github.com/monix/monix/pull/941): Added some polymorphic methods for Observable

- [PR #945](https://github.com/monix/monix/pull/945): Add collectWhile observable

- [PR #958](https://github.com/monix/monix/pull/958): Add Observable.throttle

- [PR #963](https://github.com/monix/monix/pull/963): Added fromAsyncStateActionF

- [PR #970](https://github.com/monix/monix/pull/970): Observable.unfold

- [PR #989](https://github.com/monix/monix/pull/989): Observable.unfoldEval and Observable.unfoldEvalF

### Sub-project: monix-tail

- [PR #965](https://github.com/monix/monix/pull/965): fixes resource handling in Iterant.repeat, adds Iterant.retryIfEmpty

### Chores

- [PR #936](https://github.com/monix/monix/pull/936): Add defaults values for benchmarking

- Tons of updates by https://github.com/fthomas/scala-steward

### Thanks

People that made this release possible, in alphabetical order:

- Alexandru Nedelcu (@alexandru)
- Allan Timothy Leong (@allantl)
- BambooTuna (@BambooTuna)
- Carlo (@entangled90)
- Oleg Pyzhcov (@oleg-py)
- Paul K (@pk044)
- Piotr Gawryś (@Avasil)
- Rahil Bohra (@rahilb)
- Richard Tarczaly (@arlequin-nyc)
- Ryo Fukumuro (@rfkm)
- TapanVaishnav (@TapanVaishnav)
- Valentin Willscher (@valenterry)

## Version 3.0.0-RC3 (June 16, 2019)

This release depends on Cats-Effect 1.3.1 and Cats 1.6.1.

The next planned release will target Cats-Effect 2.0.0 with Scala 2.13 support.

NOTE: this release IS NOT binary compatible with 3.0.0-RC2, as it contains some API changes, but it should be source compatible (with `@deprecated` symbols where the case).

### Sub-project: monix-execution

This sub-project no longer depends on cats-effect and there are various
improvement to `Local` aimed at fixing interop with `Future`. Note that
you might have to call `Local.isolate` to disable any sharing between tasks.

- [PR #775](https://github.com/monix/monix/pull/775): Simplified FutureUtils materialize & dematerialize

- [PR #790](https://github.com/monix/monix/pull/790): improve fixed rate scheduling on JS

- [PR #803](https://github.com/monix/monix/pull/803): Eagerly null out dequeued elements in ChunkedArrayQueue

- [PR #822](https://github.com/monix/monix/pull/822): remove dependency on cats-effect from monix-execution

- [PR #773](https://github.com/monix/monix/pull/773): change Cancelable.empty type to Cancelable

- [PR #887](https://github.com/monix/monix/pull/887): Shared locals with binds

- [PR #888](https://github.com/monix/monix/pull/888): Fix uncaught exception reporting for Scheduler

### Sub-project: monix-catnap

- [PR #778](https://github.com/monix/monix/pull/778): Adds ConcurrentChannel

- [PR #784](https://github.com/monix/monix/pull/784): More concurrent tests for MVar/Semaphore

- [PR #865](https://github.com/monix/monix/pull/865): Adding FunctionK values for Task, Coeval

### Sub-project: monix-eval

- [PR #802](https://github.com/monix/monix/pull/802): encapsulate local ctx on task execution

- [PR #807](https://github.com/monix/monix/pull/807): Improve encapsulation test, encapsulate locals on ContextShift

- [PR #838](https://github.com/monix/monix/pull/838): Add taskified variants of timeout combinators

- [PR #839](https://github.com/monix/monix/pull/839): TaskLocal should propagate when used with Bracket Methods

- [PR #849](https://github.com/monix/monix/pull/849): Specify exception on timeout

- [PR #887](https://github.com/monix/monix/pull/887): Shared locals with binds

- [PR #865](https://github.com/monix/monix/pull/865): Adding FunctionK values for Task, Coeval

### Sub-project: monix-reactive

- [PR #759](https://github.com/monix/monix/pull/759): Add Contravariant Observer and Subscriber

- [PR #760](https://github.com/monix/monix/pull/760): add Observable.filterEval

- [PR #774](https://github.com/monix/monix/pull/774): Add FunctorFilter instances for Iterant&Observable

- [PR #779](https://github.com/monix/monix/pull/779): fork blocking i/o observable ops

- [PR #794](https://github.com/monix/monix/pull/794): Acquire lock per subscription instead of observable-wide lock

- [PR #801](https://github.com/monix/monix/pull/801): Observable buffers refactoring

- [PR #819](https://github.com/monix/monix/pull/819): Extend ObservableLike with filterNot method

- [PR #831](https://github.com/monix/monix/pull/831): SerializableSuite to no longer test Future for serializability

- [PR #834](https://github.com/monix/monix/pull/834): Observable.reduce should emit for single item source

- [PR #846](https://github.com/monix/monix/pull/846): Ensure mapParallelOrdered runs in parallel

- [PR #872](https://github.com/monix/monix/pull/872): Add observable take while inclusive

- [PR #895](https://github.com/monix/monix/pull/895): Fix memory leak in MapParallelOrderedObservable

### Sub-project: monix-tail

- [PR #778](https://github.com/monix/monix/pull/778): Adds Iterant.channel, Iterant#consume

- [PR #826](https://github.com/monix/monix/pull/826): add Iterant.uncons operation

### Chores

- [PR #766](https://github.com/monix/monix/pull/766): Update sbt-unidoc to 0.4.2

- [PR #766](https://github.com/monix/monix/pull/766): Update sbt-pgp to 1.1.2

- [PR #768](https://github.com/monix/monix/pull/768): Update sbt-mima-plugin to 0.3.0

- [PR #769](https://github.com/monix/monix/pull/769): Update sbt-git to 1.0.0

- [PR #770](https://github.com/monix/monix/pull/770): Update jctools-core to 2.1.2

- [PR #771](https://github.com/monix/monix/pull/771): Update kind-projector to 0.9.8

- [PR #772](https://github.com/monix/monix/pull/772): Update sbt-jmh to 0.3.4

- [PR #771](https://github.com/monix/monix/pull/771): Update kind-projector to 0.9.9

- [PR #783](https://github.com/monix/monix/pull/783): Use globally accessible (rather than local) source paths in JS source maps (#781)

- [PR #785](https://github.com/monix/monix/pull/785): Update sbt-scalajs, scalajs-compiler, scalajs-library... to 0.6.26

- [PR #788](https://github.com/monix/monix/pull/788): Update cats-effect, cats-effect-laws to 1.1.0

- [PR #796](https://github.com/monix/monix/pull/796): fix scalacOptions

- [PR #797](https://github.com/monix/monix/pull/797): Scala 2.12.8

- [PR #798](https://github.com/monix/monix/pull/798): Update intervalWithFixedDelay scaladoc

- [PR #805](https://github.com/monix/monix/pull/805): Rename keysBuffer to os in groupBy's parameters

- [PR #808](https://github.com/monix/monix/pull/808): Update Copyright to 2019

- [PR #810](https://github.com/monix/monix/pull/810): sbt 1.2.8 (was 1.1.0)

- [PR #812](https://github.com/monix/monix/pull/812): Update Minitest to 2.3.2

- [PR #813](https://github.com/monix/monix/pull/813): Disable code coverage

- [PR #818](https://github.com/monix/monix/pull/818): Update Cats-Effect to 1.2.0

- [PR #820](https://github.com/monix/monix/pull/820): Update cats-laws to 1.5.0

- [PR #821](https://github.com/monix/monix/pull/821): Update cats-laws to 1.6.0

- [PR #823](https://github.com/monix/monix/pull/823): Scala 2.13 support

- [PR #821](https://github.com/monix/monix/pull/821): Update sbt-header to 5.1.0

- [PR #827](https://github.com/monix/monix/pull/827): Remove comments from .jvmopts

- [PR #833](https://github.com/monix/monix/pull/833): Fix build for 2.13.0-M5 by deactivating Mima for it

- [PR #840](https://github.com/monix/monix/pull/840): Add adopters list seed

- [PR #842](https://github.com/monix/monix/pull/842): Fixed deprecation docs for Task#coeval

- [PR #843](https://github.com/monix/monix/pull/843): Remove dead code from tests

- [PR #844](https://github.com/monix/monix/pull/844): Update sbt-header to 5.2.0

- [PR #847](https://github.com/monix/monix/pull/847): Update ExecutionModel.scala

- [PR #850](https://github.com/monix/monix/pull/850): Increase rate in AsyncSchedulerSuite

- [PR #854](https://github.com/monix/monix/pull/854): fix apparently erronous code involving Unit companion

- [PR #855](https://github.com/monix/monix/pull/855): Update sbt-jmh to 0.3.5

- [PR #857](https://github.com/monix/monix/pull/857): Make benchmarks compile

- [PR #859](https://github.com/monix/monix/pull/859): Update sbt-scalajs, scalajs-compiler to 0.6.27

- [PR #867](https://github.com/monix/monix/pull/867): Update kind-projector to 0.10.0

- [PR #869](https://github.com/monix/monix/pull/869): fix compile errors with latest Scala 2.13

- [PR #874](https://github.com/monix/monix/pull/874): Update cats-effect, cats-effect-laws to 1.3.0

- [PR #878](https://github.com/monix/monix/pull/878): Compile Benchmarks in CI

- [PR #879](https://github.com/monix/monix/pull/879): Do on subscription cancel scaladoc fix

- [PR #889](https://github.com/monix/monix/pull/889): Update cats-effect, cats-effect-laws to 1.3.1

- [PR #894](https://github.com/monix/monix/pull/894): Add UnsafeBecauseImpure Annotation to foreach.

- [PR #896](https://github.com/monix/monix/pull/896): Update cats-laws to 1.6.1

- [PR #898](https://github.com/monix/monix/pull/898): Reformating via Scalafmt

- [PR #899](https://github.com/monix/monix/pull/899): Fix autoCancelableRunLoops comment.

- [PR #901](https://github.com/monix/monix/pull/901): avoid deprecated unicode arrow chars

- [PR #902](https://github.com/monix/monix/pull/902): reformat build files

### Thanks

People that made this release possible, in alphabetical order:

- Alexandru Nedelcu (@alexandru)
- Allan Timothy Leong (@allantl)
- Daniel Neades (@djneades)
- Dawid Dworak (@ddworak)
- Eugene Platonov (@jozic)
- Itamar Ravid (@iravid)
- Jorge (@jvican)
- Jorge Branco (@jorgedfbranco)
- Kenji Yoshida (@xuwei-k)
- Luke Stephenson (@lukestephenson)
- Matthew de Detrich (@mdedetrich)
- Mikhail Chugunkov (@poslegm)
- Oleg Pyzhcov (@oleg-py)
- Ota Hauptmann (@Otah)
- Piotr Gawryś (@Avasil)
- Roman Tkalenko (@tkroman)
- Ryo Fukumuro (@rfkm)
- Sam Guymer (@guymers)
- Seth Tisue (@SethTisue)
- Tanaka Takaya (@takayahilton)
- Yann Simon (@yanns)

And special thanks to our top contributor in this release:
https://github.com/fthomas/scala-steward :)

## Version 3.0.0-RC2 (Nov 6, 2018)

### Cats-Effect Updates

Supporting Cats-Effect `1.0.0` has been a massive amount of work:

- [PR #659](https://github.com/monix/monix/pull/659)
  Cats Effect `1.0.0-RC` update
- [PR #681](https://github.com/monix/monix/pull/681):
  Cats-Effect `1.0.0-RC2` update
- [PR #686](https://github.com/monix/monix/pull/686):
  Cats-Effect `1.0.0-RC3` update, `Task` conversions
- [PR #716](https://github.com/monix/monix/pull/716):
  Updates to Cats-Effect `1.0.0`

Also related, but mentioned below:

- `Iterant`'s encoding had to change due to the new contract of
  Cats-Effect's type classes, in a massive change of internals that
  also improved its performance and safety
  ([#683](https://github.com/monix/monix/pull/683))

### Sub-project: monix-execution

Several features, deprecations and refactorings happened in
`monix-execution`, mentioned under the changes for `monix-catnap`
below:

- `monix.execution.misc.AsyncVar` was refactored, to have an interface
  in line with the `MVar` in Cats-Effect and moved to `monix.execution.AsyncVar`
  ([#753](https://github.com/monix/monix/pull/753))
- `monix.execution.misc.AsyncSemaphore` was also refactored and
  enhanced, with an interface resembling that of Cats-Effect's
  `Semaphore` and moved to `monix.execution.Semaphore`
  ([#754](https://github.com/monix/monix/pull/754))
- `monix.execution.AsyncQueue` was added
  ([#757](https://github.com/monix/monix/pull/757))
- `monix.execution.Callback` was added
  ([#740](https://github.com/monix/monix/pull/740))
- `monix.execution.FutureUtils` and `CancelableFuture` can now take
  care of the conversions of Scala's `Future` to and from Java's
  `CompletableFuture`
  ([#761](https://github.com/monix/monix/pull/761))

Other features:

- [PR #675](https://github.com/monix/monix/pull/675):
  Switches to stdlib `NonFatal`
- [PR #738](https://github.com/monix/monix/pull/738):
  Adds `CancelablePromise`
- [PR #765](https://github.com/monix/monix/pull/765):
  Changes `TrampolineScheduler`'s internal stack back to a queue

### Sub-project: monix-catnap

This is a new project introduced that currently depends on only
`monix-execution` and Cats/Cats-Effect and whose purpose is to provide
abstractions built on top of Cats-Effect's type classes.

- [PR #744](https://github.com/monix/monix/pull/744):
  Adds `monix.catnap.CircuitBreaker` and `LiftFuture`
- [PR #753](https://github.com/monix/monix/pull/753):
  Adds generic `monix.catnap.MVar` and `monix.execution.AsyncVar`
  refactoring
- [PR #756](https://github.com/monix/monix/pull/756):
  Makes `MVar` fork on async `take` for fairness
- [PR #754](https://github.com/monix/monix/pull/754):
  Adds generic `monix.catnap.Semaphore` and
  `monix.execution.AsyncSemaphore` refactoring
- [PR #757](https://github.com/monix/monix/pull/757):
  Adds `monix.execution.AsyncQueue` and `monix.catnap.ConcurrentQueue`
- [PR #762](https://github.com/monix/monix/pull/762):
  Fixes issue [typelevel/cats-effect#403](https://github.com/typelevel/cats-effect/pull/403),
  also added `monix.catnap.cancelables.SingleAssignCancelableF`

Also mentioned below, as part of other features:

- Added `monix.catnap.CancelableF` and
  `monix.catnap.cancelables.BooleanCancelableF`
  ([#726](https://github.com/monix/monix/pull/726))

Note: the new `FutureLift` type class provides some of the
functionality of the now deprecated `monix-java8`.

### Sub-project: monix-eval

Major removals (with deprecation symbols kept around):

- `monix.eval.TaskSemaphore`, replaced by the generic `monix.catnap.Semaphore`
- `monix.eval.MVar`, replaced by the generic `monix.catnap.MVar`
- `monix.eval.TaskCircuitBreaker`, replaced by the generic `monix.catnap.CircuitBreaker`

This was done because having these implementations specialized for
`Task` doesn't make sense and the new implementations are better and
have more features.

Features:

- [PR #626](https://github.com/monix/monix/pull/626):
  Adds `forever` for `Task`
- [PR #636](https://github.com/monix/monix/pull/636):
  Adds `join` to the `fork` documentation
- [PR #639](https://github.com/monix/monix/pull/639):
  Makes `Coeval.value` empty-parens to indicate side effects
- [PR #638](https://github.com/monix/monix/pull/638):
  Fixes `Task.foreach` waiting / error reporting
- [PR #634](https://github.com/monix/monix/pull/634):
  Adds ability to specify custom options on `Effect[Task]`
- [PR #655](https://github.com/monix/monix/pull/655):
  Handles `InterruptedException` in `NonFatal`
- [PR #660](https://github.com/monix/monix/pull/660):
  Makes `TaskApp` scheduler and options defs
- [PR #664](https://github.com/monix/monix/pull/664):
  Fixes `Task.map2` not executing things in sequence
- [PR #661](https://github.com/monix/monix/pull/661):
  Makes `mapBoth` always execute tasks in parallel
- [PR #669](https://github.com/monix/monix/pull/669)
  Adds `uncancelable` to example
- [PR #647](https://github.com/monix/monix/pull/647):
  Changes internal encoding for `Task.Async` (_major!_)
- [PR #670](https://github.com/monix/monix/pull/670):
  `Task` gets smarter about forking and async boundaries
- [PR #652](https://github.com/monix/monix/pull/652):
  Uses `TaskLocal#local` in `TaskLocal#bindL` and `TaskLocal#bindClear`
- [PR #679](https://github.com/monix/monix/pull/679):
  Fixes `Task.bracket`, `onCancelRaiseError`; introduce `Task.ContextSwitch`
- [PR #706](https://github.com/monix/monix/pull/706):
  Adds `SemigroupK[Task]` instance
- [PR #715](https://github.com/monix/monix/pull/715):
  Implements Task `timed` method
- [PR #724](https://github.com/monix/monix/pull/724):
  Makes `Task` auto-cancelable by defaul (_major!_)
- [PR #725](https://github.com/monix/monix/pull/725):
  Adds runtime check to `TaskLocal` to make it safer
- [PR #726](https://github.com/monix/monix/pull/726):
  Changes `Task` to sequence (back-pressure) async finalizers (_major!_)
- [PR #732](https://github.com/monix/monix/pull/732):
  Adds `guarantee` and `guaranteeCase` methods on `Task` and `Coeval`
- [PR #740](https://github.com/monix/monix/pull/740):
  Moves `Callback` to `monix.execution`, Task `runAsync` refactor,
  rename to `runToFuture` (_major!_)
- [PR #761](https://github.com/monix/monix/pull/761):
  Expands `FutureLift` to take care of `CompletableFuture`

### Sub-project: monix-reactive

`Observable` suffered an API refactoring, changing the convention for
operators that take `Task` or `F[_] : Effect` values as arguments:

- operators using `Task` now use the `Eval` suffix instead of `Task`,
  or no special suffix at all
- operators using `F[_] : Sync` parameters use an `F` suffix
- the `F` suffixed operators previously signalled operators that kept
  the `Observable` context (e.g. `findF`), however all of them have
  been renamed

See [PR #729](https://github.com/monix/monix/pull/729) for details.

Features:

- [PR #610](https://github.com/monix/monix/pull/610):
  Adds `scan0`, `flatScan0`, `flatScan0DelayErrors`,
  `scanEval0`, `scanMap0` on `Observable`
- [PR #641](https://github.com/monix/monix/pull/641):
  Reference `bufferTumbling` instead of `buffer` in scaladoc
- [PR #646](https://github.com/monix/monix/pull/646):
  Fixes `ack.future` called when `Ack` is `null`
- [PR #657](https://github.com/monix/monix/pull/657):
  Adds a few missing tests to `Observable` methods
- [PR #684](https://github.com/monix/monix/pull/684):
  Simplifies logic in `TakeLastOperator.onNext`
- [PR #704](https://github.com/monix/monix/pull/704):
  Introduces `Observable.doOnStartTask` and `Observable.doOnStartEval`
- [PR #723](https://github.com/monix/monix/pull/723):
  Adds `Alternative` instance for `Observable`
- [PR #654](https://github.com/monix/monix/pull/654):
  Makes `Observable#++`'s argument lazy
- [PR #729](https://github.com/monix/monix/pull/729):
  Adds `Observable.bracket`, Iterant/Task API refactorings, fixes (major!)
- [PR #741](https://github.com/monix/monix/pull/741):
  Adds cats `Profunctor` instance for `Subject`
- [PR #739](https://github.com/monix/monix/pull/739):
  Adds operator `bufferTimedWithPressure` with `sizeOf` on `Observable`
- [PR #743](https://github.com/monix/monix/pull/743):
  Improvs `Observable.collect` to avoid double evaluation
- [PR #749](https://github.com/monix/monix/pull/749):
  Adds `Profunctor` and `Contravariant` instance for `Consumer`
- [PR #750](https://github.com/monix/monix/pull/750):
  Fixes handling start/end of `Long` range in `RangeObservable`
- [PR #558](https://github.com/monix/monix/pull/558):
  Adds `Observable.mapParallelOrdered`

### Sub-project: monix-tail

The `Iterant` encoding suffered a major change, with all operators
described for `Iterant` being changed for it. This was done because:

1. the old encoding couldn't be supported under the auto-cancelable
   model promoted by Cats-Effect 1.0.0
2. the new encoding is safer and has better performance too

Features:

- [PR #683](https://github.com/monix/monix/pull/683):
  Iterant, version 2 (_major_)
- [PR #614](https://github.com/monix/monix/pull/614):
  Adds `scan0`, `scanEval0`, `scanMap0` on `Iterant`
- [PR #622](https://github.com/monix/monix/pull/622):
  Adds `mapBatch` for `Iterant`
- [PR #629](https://github.com/monix/monix/pull/629):
  Fixes `IterantBuildersSync` methods to not require
  `implicit F: Sync[F]`
- [PR #631](https://github.com/monix/monix/pull/631):
  Renames `toGenerator` to `toBatch` in `Cursor`
- [PR #633](https://github.com/monix/monix/pull/633):
  Fixes eagerness of `.bracket` on `Last` nodes
- [PR #621](https://github.com/monix/monix/pull/621):
  Changes behavior of `Batch#fromArray`, `fromAnyArray`
- [PR #656](https://github.com/monix/monix/pull/656):
  Makes Iterant's `++` take a lazy (by-name) parameter
- [PR #662](https://github.com/monix/monix/pull/662):
  Adds `Iterant.fromReactivePublisher`
- [PR #709](https://github.com/monix/monix/pull/709):
  Removes unused function from `EvalOnNextAck`
- [PR #707](https://github.com/monix/monix/pull/707):
  Add `Iterant.lastOptionL`
- [PR #746](https://github.com/monix/monix/pull/746):
  Fix `Iterant.fromReactivePublisher`
- [PR #755](https://github.com/monix/monix/pull/755):
  Remove the `Sync[Iterant]` instance

### Sub-project deprecation: monix-java8

The functionality in `monix-java8` was implemented directly in:

1. `monix.execution.FutureUtils`
2. `monix.execution.CancelableFuture`
3. `monix.catnap.FutureLift`

The `monix-java8` sub-project is still provided, but is deprecated and
will soon be removed.

### Chores

- [PR #653](https://github.com/monix/monix/pull/653):
  Update Monix's rootdoc, the landing page for the ScalaDoc
- [PR #671](https://github.com/monix/monix/pull/671):
  Optionally allow forcing a build on Java 9+
- [PR #677](https://github.com/monix/monix/pull/677):
  Add Starting Point section to CONTRIBUTING.md
- [PR #693](https://github.com/monix/monix/pull/693):
  Fix micro doc typo in `monix.execution.misc.InlineMacros`
- [PR #699](https://github.com/monix/monix/pull/699):
  Add `Concat` and `Scope` to `Iterant`'s description
- [PR #640](https://github.com/monix/monix/pull/640):
  Add sbt-doctest, to verify code examples in ScalaDoc
- [PR #705](https://github.com/monix/monix/pull/705):
  Fix all ScalaDocs (except Task's) in `monix.eval`
- [PR #717](https://github.com/monix/monix/pull/717):
  Change to Scala's Code of Conduct
- [PR #720](https://github.com/monix/monix/pull/720):
  Add @Avasil to the Code of Conduct
- [PR #718](https://github.com/monix/monix/pull/718):
  Fix `Task` ScalaDocs
- [PR #736](https://github.com/monix/monix/pull/736):
  Update doctest plugin version
- [PR #763](https://github.com/monix/monix/pull/763):
  Fix Observable doc mentioning cats.Eq

### Thanks

People that made this release possible, in alphabetical order:

- Alexandru Nedelcu (@alexandru)
- Eduardo Barrientos (@kdoomsday)
- Eugene Platonov (@jozic)
- Jakub Kozłowski (@kubukoz)
- Jamie Wilson (@jfwilson)
- Joe Ferris (@jferris)
- Jules Ivanic (@guizmaii)
- Kacper Gunia (@cakper)
- Kamil Kloch (@kamilkloch)
- Loránd Szakács (@lorandszakacs)
- Oleg Pyzhcov (@oleg-py)
- Piotr Gawryś (@Avasil)
- Raas A (@RaasAhsan)
- Seth Tisue (@SethTisue)
- Yohann B (@ybr)
- jendakol (@jendakol)
- volth (@volth)

## Version 3.0.0-RC1 (May 18, 2018)

[https://typelevel.org/cats-effect/ Cats Effect] integration:

- [PR #598](https://github.com/monix/monix/pull/598):
  Integrates `cats.effect.Timer` and `IO.cancelable`
- [PR #600](https://github.com/monix/monix/pull/600):
  Add `Sync` & `Async` instances for `Iterant`
- [PR #607](https://github.com/monix/monix/pull/607):
  implement `ConcurrentEffect[Task]` (upgrade to cats-effect 0.10)
- [PR #609](https://github.com/monix/monix/pull/609):
  update Cats to 1.1.0 & Cats Effect to 0.10

Features for `monix-execution`:

- [PR #527](https://github.com/monix/monix/pull/527)
  ([#517](https://github.com/monix/monix/issues/517),
  [#525](https://github.com/monix/monix/issues/525) and
  [#526](https://github.com/monix/monix/issues/526)):
  removes macros, replacing them with plain extension methods,
  removes `SerialCancelable#orderedUpdate`
- [PR #556](https://github.com/monix/monix/pull/556):
  `SchedulerService.awaitTermination` and fix concurrency tests
- [PR #584](https://github.com/monix/monix/pull/584)
  ([#221](https://github.com/monix/monix/issues/221)):
  add conversions module for the Java 8 `CompletableFuture`

Features for `monix-eval`:

- [PR #507](https://github.com/monix/monix/pull/507): add
  `onErrorRestartLoop` for `Task` and `Coeval`
- [PR #533](https://github.com/monix/monix/pull/533)
  ([#523](https://github.com/monix/monix/issues/532)):
  add `Task.fork` operator, deprecate old `Task.fork` and
  `executeWithFork`, renamed to `executeAsync`
- [PR #530](https://github.com/monix/monix/pull/530)
  ([#520](https://github.com/monix/monix/issues/520)):
  add `Task.forkAndForget`
- [PR #537](https://github.com/monix/monix/pull/537)
  ([#535](https://github.com/monix/monix/issues/535)):
  Make `MVar` constructors return `Task`
- [PR #540](https://github.com/monix/monix/pull/540)
  ([#539](https://github.com/monix/monix/issues/539)):
  make all `Task` abstractions referentially transparent
- [PR #545](https://github.com/monix/monix/pull/545)
  ([#538](https://github.com/monix/monix/issues/538)):
  add `newtype` for `Task.Par`, using same encoding used in `cats-effect`
- [PR #547](https://github.com/monix/monix/pull/547)
  ([#542](https://github.com/monix/monix/issues/542)):
  add `Task.runSyncUnsafe`
- [PR #550](https://github.com/monix/monix/pull/550):
  add `Task.sleep`, refactor the implementation of `delayExecution` and
  `delayResult` and deprecate `delayExecutionWith` and
  `delayResultBySelector`
- [PR #482](https://github.com/monix/monix/pull/482)
  ([#477](https://github.com/monix/monix/issues/477)):
  add the `.cancelable` operator on `Task`
- [PR #561](https://github.com/monix/monix/pull/561):
  Bracket for `Task` / `Coeval`, `Task.cancelable` and `Task.onCancelRaiseError`
- [PR #596](https://github.com/monix/monix/pull/596):
  add `Fiber` interface, refactor `memoize` for `Task` and `Coeval`
- [PR #602](https://github.com/monix/monix/pull/602):
  `TaskLocal` should expose `Local`
- [PR #603](https://github.com/monix/monix/pull/603):
  Changed implementation `TaskLocal#bind` in terms of `Task#bracket`

Features for `monix-reactive`:

- [PR #511](https://github.com/monix/monix/pull/511)
  ([#269](https://github.com/monix/monix/issues/279)):
  add `monix.reactive.subjects.Var` type
- [PR #528](https://github.com/monix/monix/pull/528):
  add `Observable.scanMap` operator
- [PR #536](https://github.com/monix/monix/pull/536)
  ([#459](https://github.com/monix/monix/issues/459)):
  add a `NonEmptyParallel` for `Observable` using `combineLatest`

Features for `monix-tail`:

- [PR #503](https://github.com/monix/monix/pull/503)
  ([#487](https://github.com/monix/monix/issues/487)):
  add `Iterant.liftF` builder for lifting monadic values
- [PR #510](https://github.com/monix/monix/pull/510)
  ([#500](https://github.com/monix/monix/issues/500)):
  add `Iterant.takeEveryNth` operator
- [PR #504](https://github.com/monix/monix/pull/504)
  ([#499](https://github.com/monix/monix/issues/499)):
  add `Iterant.switchIfEmpty` operator
- [PR #509](https://github.com/monix/monix/pull/509)
  ([#495](https://github.com/monix/monix/issues/495)):
  add `Iterant.dropLast` operator
- [PR #508](https://github.com/monix/monix/pull/508)
  ([#501](https://github.com/monix/monix/issues/501)):
  add `Iterant.intersperse` operator
- [PR #512](https://github.com/monix/monix/pull/512)
  ([#496](https://github.com/monix/monix/issues/496)):
  add `Iterant.dropWhileWithIndex` operator
- [PR #514](https://github.com/monix/monix/pull/514)
  ([#497](https://github.com/monix/monix/issues/497)):
  add `Iterant.takeWhileWithIndex` operator
- [PR #523](https://github.com/monix/monix/pull/523)
  ([#519](https://github.com/monix/monix/issues/519)):
  add `Iterant.scanMap` operator
- [PR #518](https://github.com/monix/monix/pull/518)
  ([#516](https://github.com/monix/monix/issues/516)):
  add `Iterant[Task].intervalAtFixedRate`
- [PR #524](https://github.com/monix/monix/pull/524)
  ([#498](https://github.com/monix/monix/issues/498)):
  add `Iterant.interleave`
- [PR #549](https://github.com/monix/monix/pull/549)
  ([#548](https://github.com/monix/monix/issues/548)):
  add `Iterant.fromStateAction` and `fromStateActionL`
- [PR #567](https://github.com/monix/monix/pull/567)
  (related to [#563](https://github.com/monix/monix/issues/563)):
  `completedL` should handle `F[_]` errors, `mapEval` should not
- [PR #569](https://github.com/monix/monix/pull/569)
  (related to [#563](https://github.com/monix/monix/issues/563)):
  `Iterant` fold left operators (yielding `F[_]`) need to handle errors
  thrown in `F[_]`
- [PR #566](https://github.com/monix/monix/pull/566)
  ([#562](https://github.com/monix/monix/issues/562)):
  improve safety of `attempt` & `onErrorHandleWith` on `Iterant`
- [PR #578](https://github.com/monix/monix/pull/578)
  ([#570](https://github.com/monix/monix/issues/570)):
  add `Iterant#sumL` method
- [PR #579](https://github.com/monix/monix/pull/579)
  ([#577](https://github.com/monix/monix/issues/577)):
  make `Iterant#reduceL` and `headOptionL` left folds handle errors
  from `F[_]` context
- [PR #575](https://github.com/monix/monix/pull/575)
  ([##571](https://github.com/monix/monix/issues/571) and
  [#572](https://github.com/monix/monix/issues/572)):
  add `Iterant.repeat` (method and builder)
- [PR #583](https://github.com/monix/monix/pull/583)
  ([#549](https://github.com/monix/monix/pull/549)):
  add `Iterant.fromStateAction` builder
- [PR #582](https://github.com/monix/monix/pull/582)
  ([#573](https://github.com/monix/monix/issues/573) and
  [#574](https://github.com/monix/monix/issues/574)):
  add `repeatEval`/`repeatEvalF` for `Iterant` & `repeatEvalF`
  for `Observable`
- [PR #554](https://github.com/monix/monix/pull/554)
  ([#479](https://github.com/monix/monix/issues/479)):
  add `Iterant#bracket` operator
- [PR #581](https://github.com/monix/monix/pull/581)
  ([#559](https://github.com/monix/monix/issues/559)):
  handle broken nodes in `Iterant.skipSuspendL`
- [PR #589](https://github.com/monix/monix/pull/589):
  improve handling of broken batches/cursors in `Iterant.attempt`
- [PR #591](https://github.com/monix/monix/pull/591)
  ([#580](https://github.com/monix/monix/issues/580)):
  improve strictness of `Eq` of `Iterant`, fix `doOnFinish` on `Last`

Bug fixes:

- [PR #552](https://github.com/monix/monix/pull/552)
  ([#483](https://github.com/monix/monix/issues/483)):
  `MVar` is stack unsafe, triggering stack overflow on `put`
- [PR #543](https://github.com/monix/monix/pull/543)
  ([#541](https://github.com/monix/monix/issues/541)):
  `Observable.take(0)` shouldn't subscribe to the source at all
- [PR #496](https://github.com/monix/monix/pull/469)
  ([#468](https://github.com/monix/monix/issues/468)):
  race condition for `Observable` in concatenation operators
- [PR #568](https://github.com/monix/monix/pull/568):
  in `Iterant.toReactivePublisher` the `cancel` should be made
  by `request()`
- [PR #592](https://github.com/monix/monix/pull/592)
  ([#590](https://github.com/monix/monix/issues/590)):
  potential nontermination in `Observable.zip[Map]`

Chores:

- [PR #502](https://github.com/monix/monix/pull/502): update sbt to 1.1
- [PR #488](https://github.com/monix/monix/pull/488):
  add note about execution model for `Observable.fromInputStream`
- [PR #531](https://github.com/monix/monix/pull/531)
  (related to [#513](https://github.com/monix/monix/issues/513)):
  add automatic header creation on compilation
- [PR #557](https://github.com/monix/monix/pull/557):
  disable automatic publishing, too dangerous
- [PR #546](https://github.com/monix/monix/pull/546)
  (related to [#513](https://github.com/monix/monix/issues/513)):
  add `scalafmt.conf` configuration
- [PR #565](https://github.com/monix/monix/pull/565):
  correct small typo in doc of `Task#executeOn`
- [PR #576](https://github.com/monix/monix/pull/576):
  fix comment mentioning Akka instead of Monix
- [PR #588](https://github.com/monix/monix/pull/588):
  update copyright headers for Scala 2.11 source files
- [PR #605](https://github.com/monix/monix/pull/605):
  Make concurrent Atomic tests more resilient to timeouts

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
  [Automatic Releases to Maven Central with Travis and sbt](https://alexn.org/blog/2017/08/16/automatic-releases-sbt-travis.html)
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
- Update versions of sbt dependencies
- Documentation changes (fixed missing argument in Observable docs, add code of conduct mention)
