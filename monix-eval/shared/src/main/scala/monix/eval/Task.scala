/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.eval

import cats.effect.{Effect, IO}
import cats.{Monoid, Semigroup}
import monix.eval.instances._
import monix.eval.internal._
import monix.execution.ExecutionModel.{AlwaysAsyncExecution, BatchedExecution, SynchronousExecution}
import monix.execution._
import monix.execution.atomic.Atomic
import monix.execution.cancelables.StackedCancelable
import monix.execution.internal.Platform.fusionMaxStackDepth
import monix.execution.internal.{Newtype1, Platform}
import monix.execution.misc.ThreadLocal
import monix.execution.schedulers.{CanBlock, TrampolinedRunnable}

import scala.annotation.unchecked.{uncheckedVariance => uV}
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success, Try}

/** `Task` represents a specification for a possibly lazy or
  * asynchronous computation, which when executed will produce an `A`
  * as a result, along with possible side-effects.
  *
  * Compared with `Future` from Scala's standard library, `Task` does
  * not represent a running computation or a value detached from time,
  * as `Task` does not execute anything when working with its builders
  * or operators and it does not submit any work into any thread-pool,
  * the execution eventually taking place only after `runAsync` is
  * called and not before that.
  *
  * Note that `Task` is conservative in how it spawns logical threads.
  * Transformations like `map` and `flatMap` for example will default
  * to being executed on the logical thread on which the asynchronous
  * computation was started. But one shouldn't make assumptions about
  * how things will end up executed, as ultimately it is the
  * implementation's job to decide on the best execution model. All
  * you are guaranteed is asynchronous execution after executing
  * `runAsync`.
  *
  * =Getting Started=
  *
  * To build a `Task` from a by-name parameters (thunks), we can use
  * [[monix.eval.Task.eval Task.eval]] or
  * [[monix.eval.Task.apply Task.apply]]:
  *
  * {{{
  *   val hello = Task.eval("Hello ")
  *   val world = Task("World!")
  * }}}
  *
  * Nothing gets executed yet, as `Task` is lazy, nothing executes
  * until you trigger [[Task!.runAsync(implicit* .runAsync]] on it.
  *
  * To combine `Task` values we can use [[Task!.map .map]] and
  * [[Task!.flatMap .flatMap]], which describe sequencing and this time
  * it's in a very real sense because of the laziness involved:
  *
  * {{{
  *   val sayHello = hello
  *     .flatMap(h => world.map(w => h + w))
  *     .map(println)
  * }}}
  *
  * This `Task` reference will trigger a side effect on evaluation, but
  * not yet. To make the above print its message:
  *
  * {{{
  *   import monix.execution.CancelableFuture
  *
  *   val f: CancelableFuture[Unit] = sayHello.run()
  *   //=> Hello World!
  * }}}
  *
  * The returned type is a
  * [[monix.execution.CancelableFuture CancelableFuture]] which
  * inherits from Scala's standard [[scala.concurrent.Future Future]],
  * a value that can be completed already or might be completed at
  * some point in the future, once the running asynchronous process
  * finishes. Such a future value can also be canceled, see below.
  *
  * =Laziness=
  *
  * The fact that `Task` is lazy whereas `Future` is not
  * has real consequences. For example with `Task` you can do this:
  *
  * {{{
  *   def retryOnFailure[A](times: Int, source: Task[A]): Task[A] =
  *     source.recoverWith { err =>
  *       // No more retries left? Re-throw error:
  *       if (times <= 0) Task.raise(err) else {
  *         // Recursive call, yes we can!
  *         retryOnFailure(times - 1, source)
  *           // Adding 500 ms delay for good measure
  *           .delayExecution(500)
  *       }
  *     }
  * }}}
  *
  * `Future` being a strict value-wannabe means that the actual value
  * gets "memoized" (means cached), however `Task` is basically a function
  * that can be repeated for as many times as you want. `Task` can also
  * do memoization of course:
  *
  * {{{
  * task.memoize
  * }}}
  *
  * The difference between this and just calling `runAsync()` is that
  * `memoize()` still returns a `Task` and the actual memoization
  * happens on the first `runAsync()` (with idempotency guarantees of
  * course).
  *
  * But here's something else that the `Future` data type cannot do:
  *
  * {{{
  * task.memoizeOnSuccess
  * }}}
  *
  * This keeps repeating the computation for as long as the result is a
  * failure and caches it only on success. Yes we can!
  *
  * ==Parallelism==
  *
  * Because of laziness, invoking
  * [[monix.eval.Task.sequence Task.sequence]] will not work like
  * it does for `Future.sequence`, the given `Task` values being
  * evaluated one after another, in ''sequence'', not in ''parallel''.
  * If you want parallelism, then you need to use
  * [[monix.eval.Task.gather Task.gather]] and
  * thus be explicit about it.
  *
  * This is great because it gives you the possibility of fine tuning the
  * execution. For example, say you want to execute things in parallel,
  * but with a maximum limit of 30 tasks being executed in parallel.
  * One way of doing that is to process your list in batches:
  *
  * {{{
  *   // Some array of tasks, you come up with something good :-)
  *   val list: Seq[Task[Int]] = ???
  *
  *   // Split our list in chunks of 30 items per chunk,
  *   // this being the maximum parallelism allowed
  *   val chunks = list.sliding(30, 30)
  *
  *   // Specify that each batch should process stuff in parallel
  *   val batchedTasks = chunks.map(chunk => Task.gather(chunk))
  *   // Sequence the batches
  *   val allBatches = Task.sequence(batchedTasks)
  *
  *   // Flatten the result, within the context of Task
  *   val all: Task[Seq[Int]] = allBatches.map(_.flatten)
  * }}}
  *
  * Note that the built `Task` reference is just a specification at
  * this point, or you can view it as a function, as nothing has
  * executed yet, you need to call
  * [[Task!.runAsync(implicit* .runAsync]] explicitly.
  *
  * =Cancellation=
  *
  * The logic described by an `Task` task could be cancelable,
  * depending on how the `Task` gets built.
  *
  * [[monix.execution.CancelableFuture CancelableFuture]] references
  * can also be canceled, in case the described computation can
  * be canceled. When describing `Task` tasks with `Task.eval` nothing
  * can be cancelled, since there's nothing about a plain function
  * that you can cancel, but we can build cancelable tasks with
  * [[monix.eval.Task.async Task.async]] (alias
  * [[monix.eval.Task.create Task.create]]):
  *
  * {{{
  *   import scala.concurrent.duration._
  *
  *   val delayedHello = Task.async { (scheduler, callback) =>
  *     val task = scheduler.scheduleOnce(1.second) {
  *       println("Delayed Hello!")
  *       // Signaling successful completion
  *       callback(Success(()))
  *     }
  *
  *     Cancelable { () => {
  *       println("Cancelling!")
  *       task.cancel()
  *     }
  *   }
  * }}}
  *
  * The sample above prints a message with a delay, where the delay
  * itself is scheduled with the injected `Scheduler`. The `Scheduler`
  * is in fact an implicit parameter to `runAsync()`.
  *
  * This action can be cancelled, because it specifies cancellation
  * logic. In case we have no cancelable logic to express, then it's
  * OK if we returned a
  * [[monix.execution.Cancelable.empty Cancelable.empty]] reference,
  * in which case the resulting `Task` would not be cancelable.
  *
  * But the `Task` we just described is cancelable, for one at the
  * edge, due to `runAsync` returning [[monix.execution.Cancelable Cancelable]]
  * and [[monix.execution.CancelableFuture CancelableFuture]] references:
  *
  * {{{
  *   // Triggering execution
  *   val f: CancelableFuture[Unit] = delayedHello.runAsync
  *
  *   // If we change our mind before the timespan has passed:
  *   f.cancel()
  * }}}
  *
  * But also cancellation is described on `Task` as a pure action,
  * which can be used for example in [[monix.eval.Task.race race]] conditions:
  *
  * {{{
  *   import scala.concurrent.duration._
  *
  *   val ta = Task(1)
  *     .delayExecution(4.seconds)
  *   val tb = Task.raiseError(new TimeoutException)
  *     .delayExecution(4.seconds)
  *
  *   Task.racePair(ta, tb).flatMap {
  *     case Left((a, taskB)) =>
  *       taskB.cancel.map(_ => a)
  *     case Right((taskA, b)) =>
  *       taskA.cancel.map(_ => b)
  *   }
  * }}}
  *
  * Also, given a task, we can specify actions that need to be
  * triggered in case of cancellation, see
  * [[monix.eval.Task!.doOnCancel doOnCancel]]:
  *
  * {{{
  *   val task = Task.eval(println("Hello!")).executeAsync
  *
  *   task.doOnCancel(Task.eval {
  *     println("A cancellation attempt was made!")
  *   })
  * }}}
  *
  * Controlling cancellation can be achieved with
  * [[monix.eval.Task!.cancelable cancelable]] and
  * [[monix.eval.Task!.uncancelable uncancelable]].
  *
  * The former activates
  * [[monix.eval.Task.Options.autoCancelableRunLoops auto-cancelable flatMap chains]],
  * whereas the later ensures that a task becomes uncancelable such that
  * it gets executed as an atomic unit (either all or nothing).
  *
  * =Note on the ExecutionModel=
  *
  * `Task` is conservative in how it introduces async boundaries.
  * Transformations like `map` and `flatMap` for example will default
  * to being executed on the current call stack on which the
  * asynchronous computation was started. But one shouldn't make
  * assumptions about how things will end up executed, as ultimately
  * it is the implementation's job to decide on the best execution
  * model. All you are guaranteed (and can assume) is asynchronous
  * execution after executing `runAsync`.
  *
  * Currently the default
  * [[monix.execution.ExecutionModel ExecutionModel]] specifies
  * batched execution by default and `Task` in its evaluation respects
  * the injected `ExecutionModel`. If you want a different behavior,
  * you need to execute the `Task` reference with a different scheduler.
  *
  * @define runAsyncOptDesc Triggers the asynchronous execution,
  *         much like normal `runAsync`, but includes the ability
  *         to specify [[monix.eval.Task.Options Options]] that
  *         can modify the behavior of the run-loop.
  *
  * @define runAsyncDesc Triggers the asynchronous execution.
  *
  *         Without invoking `runAsync` on a `Task`, nothing
  *         gets evaluated, as a `Task` has lazy behavior.
  *
  * @define schedulerDesc is an injected
  *         [[monix.execution.Scheduler Scheduler]] that gets used
  *         whenever asynchronous boundaries are needed when
  *         evaluating the task
  *
  * @define callbackDesc is a callback that will be invoked upon
  *         completion
  *
  * @define cancelableDesc a [[monix.execution.Cancelable Cancelable]]
  *         that can be used to cancel a running task
  *
  * @define optionsDesc a set of [[monix.eval.Task.Options Options]]
  *         that determine the behavior of Task's run-loop.
  *         
  * @define startInspiration Inspired by
  *         [[https://github.com/functional-streams-for-scala/fs2 FS2]],
  *         with the difference that this method does not fork
  *         automatically, being consistent with Monix's default
  *         behavior.
  *
  * @define runSyncUnsafeDesc Evaluates the source task synchronously and
  *         returns the result immediately or blocks the underlying thread
  *         until the result is ready.
  *
  *         '''WARNING:''' blocking operations are unsafe and incredibly error
  *         prone on top of the JVM. It's a good practice to not block any threads
  *         and use the asynchronous `runAsync` methods instead.
  *
  *         In general prefer to use the asynchronous
  *         [[monix.eval.Task!.runAsync(implicit* .runAsync]] and to
  *         structure your logic around asynchronous actions in a
  *         non-blocking way. But in case you're blocking only once,
  *         in `main`, at the "edge of the world" so to speak, then
  *         it's OK.
  *
  *         Sample:
  *         {{{
  *           import scala.concurrent.duration._
  *
  *           task.runSyncUnsafe(3.seconds)
  *         }}}
  *
  *         This is equivalent with:
  *         {{{
  *           import scala.concurrent.Await
  *
  *           Await.result(task.runAsync, 3.seconds)
  *         }}}
  *
  *         Some implementation details:
  *
  *          - blocking the underlying thread is done by triggering Scala's
  *            `BlockingContext` (`scala.concurrent.blocking`), just like
  *            Scala's `Await.result`
  *          - the `timeout` is mandatory, just like when using Scala's
  *            `Await.result`, in order to make the caller aware that the
  *            operation is dangerous and that setting a `timeout` is good
  *            practice
  *          - the loop starts in an execution mode that ignores
  *            [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]] or
  *            [[monix.execution.ExecutionModel.AlwaysAsyncExecution AlwaysAsyncExecution]],
  *            until the first asynchronous boundary. This is because we want to block
  *            the underlying thread for the result, in which case preserving
  *            fairness by forcing (batched) async boundaries doesn't do us any good,
  *            quite the contrary, the underlying thread being stuck until the result
  *            is available or until the timeout exception gets triggered.
  *
  *         Not supported on top of JavaScript engines and trying to use it
  *         with Scala.js will trigger a compile time error.
  *
  *         For optimizations on top of JavaScript you can use [[runSyncMaybe]]
  *         instead.
  *
  * @define runSyncUnsafeTimeout is a duration that specifies the
  *         maximum amount of time that this operation is allowed to block the
  *         underlying thread. If the timeout expires before the result is ready,
  *         a `TimeoutException` gets thrown. Note that you're allowed to
  *         pass an infinite duration (with `Duration.Inf`), but unless
  *         it's `main` that you're blocking and unless you're doing it only
  *         once, then this is definitely not recommended — provide a finite
  *         timeout in order to avoid deadlocks.
  *
  * @define runSyncUnsafePermit is an implicit value that's only available for
  *         the JVM and not for JavaScript, its purpose being to stop usage of
  *         this operation on top of engines that do not support blocking threads.
  *
  * @define runSyncMaybeDesc Tries to execute the source synchronously.
  *
  *         As an alternative to `runAsync`, this method tries to execute
  *         the source task immediately on the current thread and call-stack.
  *
  *         WARNING: This method is a partial function, throwing exceptions
  *         in case errors happen immediately (synchronously).
  *
  *         Usage sample:
  *         {{{
  *           try task.runSyncMaybe match {
  *             case Right(a) => println("Success: " + a)
  *             case Left(future) =>
  *               future.onComplete {
  *                 case Success(a) => println("Async success: " + a)
  *                 case Failure(e) => println("Async error: " + e)
  *               }
  *           } catch {
  *             case NonFatal(e) =>
  *               println("Error: " + e)
  *           }
  *         }}}
  *
  *         Obviously the purpose of this method is to be used for
  *         optimizations.
  *
  *         Also see [[runSyncUnsafe]], the blocking execution mode that can
  *         only work on top of the JVM.
  *
  * @define runSyncMaybeReturn `Right(result)` in case a result was processed,
  *         or `Left(future)` in case an asynchronous boundary
  *         was hit and further async execution is needed
  *
  * @define bracketErrorNote '''NOTE on error handling''': one big
  *         difference versus `try {} finally {}` is that, in case
  *         both the `release` function and the `use` function throws,
  *         the error raised by `use` gets signaled and the error
  *         raised by `release` gets reported with `System.err` for
  *         [[Coeval]] or with
  *         [[monix.execution.Scheduler.reportFailure Scheduler.reportFailure]]
  *         for [[Task]].
  *
  *         For example:
  *
  *         {{{
  *           Task("resource").bracket { _ =>
  *             // use
  *             Task.raiseError(new RuntimeException("Foo"))
  *           } { _ =>
  *             // release
  *             Task.raiseError(new RuntimeException("Bar"))
  *           }
  *         }}}
  *
  *         In this case the error signaled downstream is `"Foo"`,
  *         while the `"Bar"` error gets reported. This is consistent
  *         with the behavior of Haskell's `bracket` operation and NOT
  *         with `try {} finally {}` from Scala, Java or JavaScript.
  */
sealed abstract class Task[+A] extends Serializable {
  import monix.eval.Task._

  /** $runAsyncDesc
    *
    * @param s is an injected [[monix.execution.Scheduler Scheduler]]
    *        that gets used whenever asynchronous boundaries are needed
    *        when evaluating the task
    *
    * @return a [[monix.execution.CancelableFuture CancelableFuture]]
    *         that can be used to extract the result or to cancel
    *         a running task.
    */
  def runAsync(implicit s: Scheduler): CancelableFuture[A] =
    TaskRunLoop.startFuture(this, s, defaultOptions)

  /** $runAsyncDesc
    *
    * @param cb $callbackDesc
    * @param s $schedulerDesc
    * @return $cancelableDesc
    */
  def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable =
    TaskRunLoop.startLight(this, s, defaultOptions, cb)

  /** $runAsyncOptDesc
    *
    * @param s $schedulerDesc
    * @param opts $optionsDesc
    * @return $cancelableDesc
    */
  def runAsyncOpt(implicit s: Scheduler, opts: Options): CancelableFuture[A] =
    TaskRunLoop.startFuture(this, s, opts)

  /** $runAsyncOptDesc
    *
    * @param cb $callbackDesc
    * @param s $schedulerDesc
    * @param opts $optionsDesc
    * @return $cancelableDesc
    */
  def runAsyncOpt(cb: Callback[A])(implicit s: Scheduler, opts: Options): Cancelable =
    TaskRunLoop.startLight(this, s, opts, cb)

  /** $runSyncMaybeDesc
    *
    * @param s $schedulerDesc
    * @return $runSyncMaybeReturn
    */
  final def runSyncMaybe(implicit s: Scheduler): Either[CancelableFuture[A], A] =
    runSyncMaybeOpt(s, defaultOptions)

  /** $runSyncMaybeDesc
    *
    * @param s $schedulerDesc
    * @param opts $optionsDesc
    * @return $runSyncMaybeReturn
    */
  final def runSyncMaybeOpt(implicit s: Scheduler, opts: Options): Either[CancelableFuture[A], A] = {
    val future = runAsyncOpt(s, opts)
    future.value match {
      case Some(value) =>
        value match {
          case Success(a) => Right(a)
          case Failure(e) => throw e
        }
      case None =>
        Left(future)
    }
  }

  /** $runSyncUnsafeDesc
    *
    * @param timeout $runSyncUnsafeTimeout
    * @param s $schedulerDesc
    * @param permit $runSyncUnsafePermit
    */
  final def runSyncUnsafe(timeout: Duration)
    (implicit s: Scheduler, permit: CanBlock): A =
    TaskRunSyncUnsafe(this, timeout, s, defaultOptions)

  /** $runSyncUnsafeDesc
    *
    * @param timeout $runSyncUnsafeTimeout
    * @param s $schedulerDesc
    * @param opts $optionsDesc
    * @param permit $runSyncUnsafePermit
    */
  final def runSyncUnsafeOpt(timeout: Duration)
    (implicit s: Scheduler, opts: Options, permit: CanBlock): A =
    TaskRunSyncUnsafe(this, timeout, s, opts)

  /** Similar to Scala's `Future#onComplete`, this method triggers
    * the evaluation of a `Task` and invokes the given callback whenever
    * the result is available.
    *
    * @param f $callbackDesc
    * @param s $schedulerDesc
    * @return $cancelableDesc
    */
  final def runOnComplete(f: Try[A] => Unit)(implicit s: Scheduler): Cancelable =
    runAsync(new Callback[A] {
      def onSuccess(value: A): Unit = f(Success(value))
      def onError(ex: Throwable): Unit = f(Failure(ex))
    })(s)

  /** Creates a new [[Task]] that will expose any triggered error
    * from the source.
    */
  final def attempt: Task[Either[Throwable, A]] =
    FlatMap(this, AttemptTask.asInstanceOf[A => Task[Either[Throwable, A]]])

  /** Introduces an asynchronous boundary at the current stage in the
    * asynchronous processing pipeline.
    *
    * Consider the following example:
    *
    * {{{
    *   import monix.execution.Scheduler
    *   val io = Scheduler.io()
    *
    *   val source = Task(1).executeOn(io).map(_ + 1)
    * }}}
    *
    * That task is being forced to execute on the `io` scheduler,
    * including the `map` transformation that follows after
    * `executeOn`. But what if we want to jump with the execution
    * run-loop on the default scheduler for the following
    * transformations?
    *
    * Then we can do:
    *
    * {{{
    *   source.asyncBoundary.map(_ + 2)
    * }}}
    *
    * In this sample, whatever gets evaluated by the `source` will
    * happen on the `io` scheduler, however the `asyncBoundary` call
    * will make all subsequent operations to happen on the default
    * scheduler.
    */
  final def asyncBoundary: Task[A] =
    this.flatMap(r => Task.shift.map(_ => r))

  /** Introduces an asynchronous boundary at the current stage in the
    * asynchronous processing pipeline, making processing to jump on
    * the given [[monix.execution.Scheduler Scheduler]] (until the
    * next async boundary).
    *
    * Consider the following example:
    * {{{
    *   import monix.execution.Scheduler
    *   val io = Scheduler.io()
    *
    *   val source = Task(1).executeOn(io).map(_ + 1)
    * }}}
    *
    * That task is being forced to execute on the `io` scheduler,
    * including the `map` transformation that follows after
    * `executeOn`. But what if we want to jump with the execution
    * run-loop on another scheduler for the following transformations?
    *
    * Then we can do:
    * {{{
    *   import monix.execution.Scheduler.global
    *
    *   source.asyncBoundary(global).map(_ + 2)
    * }}}
    *
    * In this sample, whatever gets evaluated by the `source` will
    * happen on the `io` scheduler, however the `asyncBoundary` call
    * will make all subsequent operations to happen on the specified
    * `global` scheduler.
    *
    * @param s is the scheduler triggering the asynchronous boundary
    */
  final def asyncBoundary(s: Scheduler): Task[A] =
    this.flatMap(a => Task.shift(s).map(_ => a))

  /** Returns a task that treats the source task as the acquisition of a resource,
    * which is then exploited by the `use` function and then `released`.
    *
    * The `bracket` operation is the equivalent of the
    * `try {} catch {} finally {}` statements from mainstream languages.
    *
    * The `bracket` operation installs the necessary exception handler to release
    * the resource in the event of an exception being raised during the computation,
    * or in case of cancellation.
    *
    * If an exception is raised, then `bracket` will re-raise the exception
    * ''after'' performing the `release`. If the resulting task gets cancelled,
    * then `bracket` will still perform the `release`, but the yielded task
    * will be non-terminating (equivalent with [[Task.never]]).
    *
    * Example:
    *
    * {{{
    *   import java.io._
    *
    *   def readFile(file: File): Task[String] = {
    *     // Opening a file handle for reading text
    *     val acquire = Task.eval(new BufferedReader(
    *       new InputStreamReader(new FileInputStream(file), "utf-8")
    *     ))
    *
    *     acquire.bracket { in =>
    *       // Usage part
    *       Task.eval {
    *         // Yes, ugly Java, non-FP loop;
    *         // side-effects are suspended though
    *         var line: String = null
    *         val buff = new StringBuilder()
    *         do {
    *           line = in.readLine()
    *           if (line != null) buff.append(line)
    *         } while (line != null)
    *         buff.toString()
    *       }
    *     } { in =>
    *       // The release part
    *       Task.eval(in.close())
    *     }
    *   }
    * }}}
    *
    * Note that in case of cancellation the underlying implementation cannot
    * guarantee that the computation described by `use` doesn't end up
    * executed concurrently with the computation from `release`. In the example
    * above that ugly Java loop might end up reading from a `BufferedReader`
    * that is already closed due to the task being cancelled, thus triggering
    * an error in the background with nowhere to go but in
    * [[monix.execution.Scheduler.reportFailure Scheduler.reportFailure]].
    *
    * In this particular example, given that we are just reading from a file,
    * it doesn't matter. But in other cases it might matter, as concurrency
    * on top of the JVM when dealing with I/O might lead to corrupted data.
    *
    * For those cases you might want to do synchronization (e.g. usage of
    * locks and semaphores) and you might want to use [[bracketE]], the
    * version that allows you to differentiate between normal termination
    * and cancellation.
    *
    * $bracketErrorNote
    *
    * @see [[bracketE]]
    *
    * @param use is a function that evaluates the resource yielded by the source,
    *        yielding a result that will get generated by the task returned
    *        by this `bracket` function
    *
    * @param release is a function that gets called after `use` terminates,
    *        either normally or in error, or if it gets cancelled, receiving
    *        as input the resource that needs to be released
    */
  final def bracket[B](use: A => Task[B])(release: A => Task[Unit]): Task[B] =
    bracketE(use)((a, _) => release(a))

  /** Returns a task that treats the source task as the acquisition of a resource,
    * which is then exploited by the `use` function and then `released`, with
    * the possibility of distinguishing between normal termination and cancellation,
    * such that an appropriate release of resources can be executed.
    *
    * The `bracketE` operation is the equivalent of `try {} catch {} finally {}`
    * statements from mainstream languages.
    *
    * The `bracketE` operation installs the necessary exception handler to release
    * the resource in the event of an exception being raised during the computation,
    * or in case of cancellation.
    *
    * In comparison with the simpler [[bracket]] version, this one allows the
    * caller to differentiate between normal termination and cancellation.
    *
    * The `release` function receives as input:
    *
    *  - `Left(None)` in case of cancellation
    *  - `Left(Some(error))` in case `use` terminated with an error
    *  - `Right(b)` in case of success
    *
    * $bracketErrorNote
    *
    * @see [[bracket]]
    *
    * @param use is a function that evaluates the resource yielded by the source,
    *        yielding a result that will get generated by this function on
    *        evaluation
    *
    * @param release is a function that gets called after `use` terminates,
    *        either normally or in error, or if it gets cancelled, receiving
    *        as input the resource that needs that needs release, along with
    *        the result of `use` (cancellation, error or successful result)
    */
  final def bracketE[B](use: A => Task[B])(release: (A, Either[Option[Throwable], B]) => Task[Unit]): Task[B] =
    TaskBracket(this, use, release)

  /** Transforms a [[Task]] into a [[Coeval]] that tries to execute the
    * source synchronously, returning either `Right(value)` in case a
    * value is available immediately, or `Left(future)` in case we
    * have an asynchronous boundary or an error.
    */
  final def coeval(implicit s: Scheduler): Coeval[Either[CancelableFuture[A], A]] =
    Coeval.eval(runSyncMaybe(s))

  /** Signals cancellation of the source.
    *
    * Returns a new task that will complete when the cancellation is
    * sent (but not when it is observed).
    *
    * Compared with triggering
    * [[monix.execution.Cancelable.cancel Cancelable.cancel]] or
    * [[monix.execution.CancelableFuture.cancel CancelableFuture.cancel]]
    * after [[Task.runAsync(implicit* runAsync]], this action is pure.
    *
    * Example:
    * {{{
    *   Task.racePair(ta, tb).flatMap {
    *     case Left((a, tb)) => tb.cancel.map(_ => a)
    *     case Right((ta, b)) => ta.cancel.map(_ => b)
    *   }
    * }}}
    */
  final def cancel: Task[Unit] =
    TaskCancellation.signal(this)

  /** Returns a task that waits for the specified `timespan` before
    * executing and mirroring the result of the source.
    *
    * In this example we're printing to standard output, but before
    * doing that we're introducing a 3 seconds delay:
    *
    * {{{
    *   Task(println("Hello!"))
    *     .delayExecution(3.seconds)
    * }}}
    *
    * This operation is also equivalent with:
    *
    * {{{
    *   Task.sleep(timespan).flatMap(_ => task)
    * }}}
    *
    * See [[Task.sleep]] for the operation that describes the effect
    * and [[Task.delayResult]] for the version that evaluates the
    * task on time, but delays the signaling of the result.
    *
    * @param timespan is the time span to wait before triggering
    *        the evaluation of the task
    */
  final def delayExecution(timespan: FiniteDuration): Task[A] =
    Task.sleep(timespan).flatMap(_ => this)

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    *
    * See [[delayExecution]] for delaying the evaluation of the
    * task with the specified duration. The [[delayResult]] operation
    * is effectively equivalent with:
    *
    * {{{
    *   task.flatMap(a => Task.now(a).delayExecution(timespan))
    * }}}
    *
    * Or if we are to use the [[Task.sleep]] describing just the
    * effect, this operation is equivalent with:
    *
    * {{{
    *   task.flatMap(a => Task.sleep(timespan).map(_ => a))
    * }}}
    *
    * Thus in this example 3 seconds will pass before the result
    * is being generated by the source, plus another 5 seconds
    * before it is finally emitted:
    *
    * {{{
    *   Task.eval(1 + 1)
    *     .delayExecution(3.seconds)
    *     .delayResult(5.seconds)
    * }}}
    *
    * @param timespan is the time span to sleep before signaling
    *        the result, but after the evaluation of the source
    */
  final def delayResult(timespan: FiniteDuration): Task[A] =
    flatMap(a => Task.sleep(timespan).map(_ => a))

  /** Overrides the default [[monix.execution.Scheduler Scheduler]],
    * possibly forcing an asynchronous boundary before execution
    * (if `forceAsync` is set to `true`, the default).
    *
    * When a `Task` is executed with [[Task.runAsync(implicit* .runAsync]],
    * it needs a `Scheduler`, which is going to be injected in all
    * asynchronous tasks processed within the `flatMap` chain,
    * a `Scheduler` that is used to manage asynchronous boundaries
    * and delayed execution.
    *
    * This scheduler passed in `runAsync` is said to be the "default"
    * and `executeOn` overrides that default.
    *
    * {{{
    *   import monix.execution.Scheduler
    *   import java.io.{BufferedReader, FileInputStream, InputStreamReader}
    *
    *   /** Reads the contents of a file using blocking I/O. */
    *   def readFile(path: String): Task[String] = Task.eval {
    *     val in = new BufferedReader(
    *       new InputStreamReader(new FileInputStream(path), "utf-8"))
    *
    *     val buffer = new StringBuffer()
    *     var line: String = null
    *     do {
    *       line = in.readLine()
    *       if (line != null) buffer.append(line)
    *     } while (line != null)
    *
    *     buffer.toString
    *   }
    *
    *   // Building a Scheduler meant for blocking I/O
    *   val io = Scheduler.io()
    *
    *   // Building the Task reference, specifying that `io` should be
    *   // injected as the Scheduler for managing async boundaries
    *   readFile("path/to/file").executeOn(io, forceAsync = true)
    * }}}
    *
    * In this example we are using [[Task.eval]], which executes the
    * given `thunk` immediately (on the current thread and call stack).
    *
    * By calling `executeOn(io)`, we are ensuring that the used
    * `Scheduler` (injected in [[Task.unsafeCreate async tasks]] by
    * means of [[Task.Context]]) will be `io`, a `Scheduler` that we
    * intend to use for blocking I/O actions. And we are also forcing
    * an asynchronous boundary right before execution, by passing
    * the `forceAsync` parameter as `true` (which happens to be
    * the default value).
    *
    * Thus, for our described function that reads files using Java's
    * blocking I/O APIs, we are ensuring that execution is entirely
    * managed by an `io` scheduler, executing that logic on a thread
    * pool meant for blocking I/O actions.
    *
    * Note that in case `forceAsync = false`, then the invocation will
    * not introduce any async boundaries of its own and will not
    * ensure that execution will actually happen on the given
    * `Scheduler`, that depending of the implementation of the `Task`.
    * For example:
    *
    * {{{
    *   Task.eval("Hello, " + "World!")
    *     .executeOn(io, forceAsync = false)
    * }}}
    *
    * The evaluation of this task will probably happen immediately
    * (depending on the configured
    * [[monix.execution.ExecutionModel ExecutionModel]]) and the
    * given scheduler will probably not be used at all.
    *
    * However in case we would use [[Task.apply]], which ensures
    * that execution of the provided thunk will be async, then
    * by using `executeOn` we'll indeed get a logical fork on
    * the `io` scheduler:
    *
    * {{{
    *   Task("Hello, " + "World!")
    *     .executeOn(io, forceAsync = false)
    * }}}
    *
    * Also note that overriding the "default" scheduler can only
    * happen once, because it's only the "default" that can be
    * overridden.
    *
    * Something like this won't have the desired effect:
    *
    * {{{
    *   val io1 = Scheduler.io()
    *   val io2 = Scheduler.io()
    *
    *   task.executeOn(io1).executeOn(io2)
    * }}}
    *
    * In this example the implementation of `task` will receive
    * the reference to `io1` and will use it on evaluation, while
    * the second invocation of `executeOn` will create an unnecessary
    * async boundary (if `forceAsync = true`) or be basically a
    * costly no-op. This might be confusing but consider the
    * equivalence to these functions:
    *
    * {{{
    *   import scala.concurrent.ExecutionContext
    *
    *   val io1 = Scheduler.io()
    *   val io2 = Scheduler.io()
    *
    *   def sayHello(ec: ExecutionContext): Unit =
    *     ec.execute(new Runnable {
    *       def run() = println("Hello!")
    *     })
    *
    *   def sayHello2(ec: ExecutionContext): Unit =
    *     // Overriding the default `ec`!
    *     sayHello(io1)
    *
    *   def sayHello3(ec: ExecutionContext): Unit =
    *     // Overriding the default no longer has the desired effect
    *     // because sayHello2 is ignoring it!
    *     sayHello2(io2)
    * }}}
    *
    * @param s is the [[monix.execution.Scheduler Scheduler]] to use
    *        for overriding the default scheduler and for forcing
    *        an asynchronous boundary if `forceAsync` is `true`
    *
    * @param forceAsync indicates whether an asynchronous boundary
    *        should be forced right before the evaluation of the
    *        `Task`, managed by the provided `Scheduler`
    *
    * @return a new `Task` that mirrors the source on evaluation,
    *         but that uses the provided scheduler for overriding
    *         the default and possibly force an extra asynchronous
    *         boundary on execution
    */
  final def executeOn(s: Scheduler, forceAsync: Boolean = true): Task[A] =
    TaskExecuteOn(this, s, forceAsync)

  /** Mirrors the given source `Task`, but upon execution ensure
    * that evaluation forks into a separate (logical) thread.
    *
    * The [[monix.execution.Scheduler Scheduler]] used will be
    * the one that is used to start the run-loop in
    * [[Task.runAsync(implicit* .runAsync]].
    *
    * This operation is equivalent with:
    *
    * {{{
    *   Task.shift.flatMap(_ => task)
    *
    *   // ... or ...
    *
    *   import cats.syntax.all._
    *
    *   Task.shift.followedBy(task)
    * }}}
    *
    * The [[monix.execution.Scheduler Scheduler]] used for scheduling
    * the async boundary will be the default, meaning the one used to
    * start the run-loop in `runAsync`.
    */
  final def executeAsync: Task[A] =
    Task.shift.flatMap(_ => this)

  /** Returns a new task that will execute the source with a different
    * [[monix.execution.ExecutionModel ExecutionModel]].
    *
    * This allows fine-tuning the options injected by the scheduler
    * locally. Example:
    *
    * {{{
    *   import monix.execution.ExecutionModel.AlwaysAsyncExecution
    *   task.executeWithModel(AlwaysAsyncExecution)
    * }}}
    *
    * @param em is the
    *        [[monix.execution.ExecutionModel ExecutionModel]]
    *        with which the source will get evaluated on `runAsync`
    */
  final def executeWithModel(em: ExecutionModel): Task[A] =
    TaskExecuteWithModel(this, em)

  /** Returns a new task that will execute the source with a different
    * set of [[Task.Options Options]].
    *
    * This allows fine-tuning the default options. Example:
    *
    * {{{
    *   task.executeWithOptions(_.enableAutoCancelableRunLoops)
    * }}}
    *
    * @param f is a function that takes the source's current set of
    *        [[Task.Options options]] and returns a modified set of
    *        options that will be used to execute the source
    *        upon `runAsync`
    */
  final def executeWithOptions(f: Options => Options): Task[A] =
    TaskExecuteWithOptions(this, f)

  /** Returns a new task that is cancelable.
    *
    * Normally Monix Tasks have these characteristics:
    *
    *  - `flatMap` chains are not cancelable by default
    *  - when creating [[Task.create async tasks]] the user has to specify explicit
    *    cancellation logic
    *
    * This operation returns a task that has [[Task.Options.autoCancelableRunLoops]]
    * enabled upon evaluation, thus being equivalent with:
    * {{{
    *   task.executeWithOptions(_.enableAutoCancelableRunLoops)
    * }}}
    *
    * What this does is two-fold:
    *
    *  - `flatMap` chains become cancelable on async boundaries, which works in
    *    combination with [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]]
    *    that's enabled by default (injected by [[monix.execution.Scheduler Scheduler]],
    *    but can also be changed with [[executeWithModel]])
    *  - even if the source task cannot be cancelled, upon completion the result
    *    is not allowed to be streamed and the continuation is not allowed to execute
    *
    * For example this is a function that calculates the n-th Fibonacci element:
    * {{{
    *   def fib(n: Int): Task[Long] = {
    *     def loop(n: Int, a: Long, b: Long): Task[Long] =
    *       Task.suspend {
    *         if (n > 0)
    *           loop(n - 1, b, a + b)
    *         else
    *           Task.now(a)
    *       }
    *
    *     loop(n, 0, 1).cancelable
    *   }
    * }}}
    * 
    * Normally this isn't cancelable and it might take a long time, but
    * by calling `cancelable` on the result, we ensure that when cancellation
    * is observed, at async boundaries, the loop will stop with the task
    * becoming a non-terminating one.
    * 
    * This operation represents the opposite of [[uncancelable]]. And note
    * that it works even for tasks that are uncancelable / atomic, because
    * it blocks the rest of the `flatMap` loop from executing, functioning
    * like a sort of cancellation boundary:
    *
    * {{{
    *   Task(println("Hello ..."))
    *     .cancelable
    *     .flatMap(_ => Task.eval(println("World!")))
    * }}}
    *
    * Normally [[Task.apply]] does not yield a cancelable task, but by applying
    * the `cancelable` transformation to it, the `println` will execute,
    * but not the subsequent `flatMap` operation.
    */
  def cancelable: Task[A] =
    executeWithOptions(_.enableAutoCancelableRunLoops)

  /** Returns a failed projection of this task.
    *
    * The failed projection is a `Task` holding a value of type `Throwable`,
    * emitting the error yielded by the source, in case the source fails,
    * otherwise if the source succeeds the result will fail with a
    * `NoSuchElementException`.
    */
  final def failed: Task[Throwable] =
    transformWith(_ => Error(new NoSuchElementException("failed")), e => Now(e))

  /** Creates a new Task by applying a function to the successful result
    * of the source Task, and returns a task equivalent to the result
    * of the function.
    */
  final def flatMap[B](f: A => Task[B]): Task[B] =
    FlatMap(this, f)

  /** Given a source Task that emits another Task, this function
    * flattens the result, returning a Task equivalent to the emitted
    * Task by the source.
    */
  final def flatten[B](implicit ev: A <:< Task[B]): Task[B] =
    flatMap(a => a)

  /** Returns a new task that upon evaluation will execute the given
    * function for the generated element, transforming the source into
    * a `Task[Unit]`.
    *
    * Similar in spirit with normal [[foreach]], but lazy, as
    * obviously nothing gets executed at this point.
    */
  final def foreachL(f: A => Unit): Task[Unit] =
    this.map { a => f(a); () }

  /** Triggers the evaluation of the source, executing the given
    * function for the generated element.
    *
    * The application of this function has strict behavior, as the
    * task is immediately executed.
    */
  final def foreach(f: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] =
    foreachL(f).runAsync(s)

  /** Start asynchronous execution of the source suspended in the `Task` context.
    *
    * This can be used for non-deterministic / concurrent execution.
    * The following code is more or less equivalent with
    * [[Task.parMap2]] (minus the behavior on error handling and
    * cancellation, plus forced async execution):
    *
    * {{{
    *   def par2[A, B](ta: Task[A], tb: Task[B]): Task[(A, B)] =
    *     for {
    *       fa <- ta.fork
    *       fb <- tb.fork
    *        a <- fa
    *        b <- fb
    *     } yield (a, b)
    * }}}
    *
    * Note in such a case usage of [[Task.parMap2 parMap2]]
    * (and [[Task.parMap3 parMap3]], etc.) is still recommended
    * because of behavior on error and cancellation — consider that
    * in the example above, if the first task finishes in error,
    * the second task doesn't get cancelled.
    *
    * IMPORTANT — this operation forces an asynchronous boundary before
    * execution, as in general this law holds:
    * {{{
    *   fa.fork <-> fa.executeAsync.start
    * }}}
    *
    * See [[start]] for the equivalent that does not start the task with
    * a forced async boundary.
    */
  final def fork: Task[Task[A]] =
    executeAsync.start

  /** Start asynchronous execution of the source suspended in the `Task` context,
    * running it in the background and discarding the result.
    *
    * Similar to [[fork]] after mapping result to Unit. Below law holds:
    *
    * {{{
    *   task.forkAndForget <-> task.fork.map(_ => ())
    * }}}
    *
    */
  final def forkAndForget: Task[Unit] =
    TaskForkAndForget(this)

  /** Returns a new `Task` in which `f` is scheduled to be run on
    * completion. This would typically be used to release any
    * resources acquired by this `Task`.
    *
    * The returned `Task` completes when both the source and the task
    * returned by `f` complete.
    *
    * NOTE: The given function is only called when the task is
    * complete.  However the function does not get called if the task
    * gets canceled.  Cancellation is a process that's concurrent with
    * the execution of a task and hence needs special handling.
    *
    * See [[doOnCancel]] for specifying a callback to call on
    * canceling a task.
    */
  final def doOnFinish(f: Option[Throwable] => Task[Unit]): Task[A] =
    transformWith(
      a => f(None).map(_ => a),
      e => f(Some(e)).flatMap(_ => Error(e))
    )

  /** Returns a new `Task` that will mirror the source, but that will
    * execute the given `callback` if the task gets canceled before
    * completion.
    *
    * This only works for premature cancellation. See [[doOnFinish]]
    * for triggering callbacks when the source finishes.
    *
    * @param callback is the callback to execute if the task gets
    *        canceled prematurely
    */
  final def doOnCancel(callback: Task[Unit]): Task[A] =
    TaskDoOnCancel(this, callback)

  /** Creates a new [[Task]] that will expose any triggered error from
    * the source.
    */
  final def materialize: Task[Try[A]] =
    FlatMap(this, MaterializeTask.asInstanceOf[A => Task[Try[A]]])

  /** Dematerializes the source's result from a `Try`. */
  final def dematerialize[B](implicit ev: A <:< Try[B]): Task[B] =
    this.asInstanceOf[Task[Try[B]]].flatMap(fromTry)

  /** Returns a new task that mirrors the source task for normal termination,
    * but that triggers the given error on cancellation.
    *
    * Normally tasks that are cancelled become non-terminating.
    * Here's an example of a cancelable task:
    *
    * {{{
    *   val tenSecs = Task.sleep(10)
    *   val task = tenSecs.fork.flatMap { fa =>
    *     // Triggering pure cancellation, then trying to get its result
    *     fa.cancel.flatMap(_ => fa)
    *   }
    *
    *   task.timeout(10.seconds).runAsync
    *   //=> throws TimeoutException
    * }}}
    *
    * In general you can expect cancelable tasks to become non-terminating on
    * cancellation.
    *
    * This `onCancelRaiseError` operator transforms a task that would yield
    * [[Task.never]] on cancellation into one that yields [[Task.raiseError]].
    *
    * Example:
    * {{{
    *   import java.util.concurrent.CancellationException
    *
    *   val tenSecs = Task.sleep(10.seconds)
    *     .onCancelRaiseError(new CancellationException)
    *
    *   val task = tenSecs.fork.flatMap { fa =>
    *     // Triggering pure cancellation, then trying to get its result
    *     fa.cancel.flatMap(_ => fa)
    *   }
    *
    *   task.runAsync
    *   // => CancellationException
    * }}}
    */
  final def onCancelRaiseError(e: Throwable): Task[A] =
    TaskCancellation.raiseError(this, e)

  /** Creates a new task that will try recovering from an error by
    * matching it with another task using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  final def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Task[B]]): Task[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, raiseConstructor))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit by executing another task.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  final def onErrorHandleWith[B >: A](f: Throwable => Task[B]): Task[B] =
    FlatMap(this, StackFrame.errorHandler(nowConstructor, f))

  /** Creates a new task that in case of error will fallback to the
    * given backup task.
    */
  final def onErrorFallbackTo[B >: A](that: Task[B]): Task[B] =
    onErrorHandleWith(_ => that)

  /** Given a predicate function, keep retrying the
    * task until the function returns true.
    */
  final def restartUntil(p: (A) => Boolean): Task[A] =
    this.flatMap(a => if (p(a)) now(a) else this.restartUntil(p))

  /** Returns a new `Task` that applies the mapping function to
    * the element emitted by the source.
    *
    * Can be used for specifying a (lazy) transformation to the result
    * of the source.
    *
    * This equivalence with [[flatMap]] always holds:
    *
    * ```scala
    * fa.map(f) <-> fa.flatMap(x => Task.pure(f(x)))
    * ```
    */
  final def map[B](f: A => B): Task[B] =
    this match {
      case Map(source, g, index) =>
        // Allowed to do a fixed number of map operations fused before
        // resetting the counter in order to avoid stack overflows;
        // See `monix.execution.internal.Platform` for details.
        if (index != fusionMaxStackDepth) Map(source, g.andThen(f), index + 1)
        else Map(this, f, 0)
      case _ =>
        Map(this, f, 0)
    }

  /** Memoizes (caches) the result of the source task and reuses it on
    * subsequent invocations of `runAsync`.
    *
    * The resulting task will be idempotent, meaning that
    * evaluating the resulting task multiple times will have the
    * same effect as evaluating it once.
    *
    * @see [[memoizeOnSuccess]] for a version that only caches
    *     successful results
    */
  final def memoize: Task[A] =
    this match {
      case Now(_) | Error(_) =>
        this
      case Eval(f) =>
        f match {
          case _:Coeval.Once[_] => this
          case _ =>
            val coeval = Coeval.Once(f)
            Eval(coeval)
        }
      case ref: MemoizeSuspend[_] if ref.isCachingAll =>
        this
      case other =>
        new MemoizeSuspend[A](() => other, cacheErrors = true)
    }

  /** Memoizes (cache) the successful result of the source task
    * and reuses it on subsequent invocations of `runAsync`.
    * Thrown exceptions are not cached.
    *
    * The resulting task will be idempotent, but only if the
    * result is successful.
    *
    * @see [[memoize]] for a version that caches both successful
    *     results and failures
    */
  final def memoizeOnSuccess: Task[A] =
    this match {
      case Now(_) | Error(_) =>
        this
      case Eval(f) =>
        val lf = LazyOnSuccess(f)
        if (lf eq f) this else Eval(lf)
      case _: MemoizeSuspend[_] =>
        this
      case other =>
        new MemoizeSuspend[A](() => other, cacheErrors = false)
    }

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  final def onErrorRestart(maxRetries: Long): Task[A] =
    this.onErrorHandleWith(ex =>
      if (maxRetries > 0) this.onErrorRestart(maxRetries-1)
      else raiseError(ex))

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds, or until the given
    * predicate returns `false`.
    *
    * In this sample we retry for as long as the exception is a `TimeoutException`:
    * {{{
    *   task.onErrorRestartIf {
    *     case _: TimeoutException => true
    *     case _ => false
    *   }
    * }}}
    *
    * @param p is the predicate that is executed if an error is thrown and
    *        that keeps restarting the source for as long as it returns `true`
    */
  final def onErrorRestartIf(p: Throwable => Boolean): Task[A] =
    this.onErrorHandleWith(ex => if (p(ex)) this.onErrorRestartIf(p) else raiseError(ex))

  /** On error restarts the source with a customizable restart loop.
    *
    * This operation keeps an internal `state`, with a start value, an internal
    * state that gets evolved and based on which the next step gets decided,
    * e.g. should it restart, maybe with a delay, or should it give up and
    * re-throw the current error.
    *
    * Example that implements a simple retry policy that retries for a maximum
    * of 10 times before giving up; also introduce a 1 second delay before
    * each retry is executed:
    *
    * {{{
    *   import scala.concurrent.duration._
    *
    *   task.onErrorRestartLoop(10) { (err, maxRetries, retry) =>
    *     if (maxRetries > 0)
    *       // Next retry please; but do a 1 second delay
    *       retry(maxRetries - 1).delayExecution(1.second)
    *     else
    *       // No retries left, rethrow the error
    *       Task.raiseError(err)
    *   }
    * }}}
    *
    * A more complex exponential back-off sample:
    *
    * {{{
    *   import scala.concurrent.duration._
    *
    *   // Keeps the current state, indicating the restart delay and the
    *   // maximum number of retries left
    *   final case class Backoff(maxRetries: Int, delay: FiniteDuration)
    *
    *   // Restarts for a maximum of 10 times, with an initial delay of 1 second,
    *   // a delay that keeps being multiplied by 2
    *   task.onErrorRestartLoop(Backoff(10, 1.second)) { (err, state, retry) =>
    *     val Backoff(maxRetries, delay) = state
    *     if (maxRetries > 0)
    *       retry(Backoff(maxRetries - 1, delay * 2)).delayExecution(delay)
    *     else
    *       // No retries left, rethrow the error
    *       Task.raiseError(err)
    *   }
    * }}}
    *
    * The given function injects the following parameters:
    *
    *  1. `error` reference that was thrown
    *  2. the current `state`, based on which a decision for the retry is made
    *  3. `retry: S => Task[B]` function that schedules the next retry
    *
    * @param initial is the initial state used to determine the next on error
    *        retry cycle
    * @param f is a function that injects the current error, state, a
    *        function that can signal a retry is to be made and returns
    *        the next task
    */
  final def onErrorRestartLoop[S, B >: A](initial: S)(f: (Throwable, S, S => Task[B]) => Task[B]): Task[B] =
    onErrorHandleWith(err => f(err, initial, state => (this : Task[B]).onErrorRestartLoop(state)(f)))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  final def onErrorHandle[U >: A](f: Throwable => U): Task[U] =
    onErrorHandleWith(f.andThen(nowConstructor))

  /** Creates a new task that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  final def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Task[U] =
    onErrorRecoverWith(pf.andThen(nowConstructor))

  /** Start execution of the source suspended in the `Task` context.
    *
    * This can be used for non-deterministic / concurrent execution.
    * The following code is more or less equivalent with
    * [[Task.parMap2]] (minus the behavior on error handling and
    * cancellation):
    *
    * {{{
    *   def par2[A, B](ta: Task[A], tb: Task[B]): Task[(A, B)] =
    *     for {
    *       fa <- ta.start
    *       fb <- tb.start
    *        a <- fa
    *        b <- fb
    *     } yield (a, b)
    * }}}
    *
    * Note in such a case usage of [[Task.parMap2 parMap2]]
    * (and [[Task.parMap3 parMap3]], etc.) is still recommended
    * because of behavior on error and cancellation — consider that
    * in the example above, if the first task finishes in error,
    * the second task doesn't get cancelled.
    *
    * IMPORTANT — this operation does start with an asynchronous boundary.
    * You can either use [[fork]] as an alternative, or use [[executeAsync]]
    * just before calling `start`, as in general this law holds:
    *
    * {{{
    *   fa.fork <-> fa.executeAsync.start
    * }}}
    *
    * See [[fork]] for the equivalent that does starts the task with
    * a forced async boundary.
    */
  final def start: Task[Task[A]] =
    TaskStart(this)

  /** Converts the source `Task` to a `cats.effect.IO` value. */
  final def toIO(implicit s: Scheduler): IO[A] =
    TaskConversions.toIO(this)(s)

  /** Converts a [[Task]] to an `org.reactivestreams.Publisher` that
    * emits a single item on success, or just the error on failure.
    *
    * See [[http://www.reactive-streams.org/ reactive-streams.org]] for the
    * Reactive Streams specification.
    */
  final def toReactivePublisher(implicit s: Scheduler): org.reactivestreams.Publisher[A @uV] =
    TaskToReactivePublisher[A](this)(s)

  /** Returns a Task that mirrors the source Task but that triggers a
    * `TimeoutException` in case the given duration passes without the
    * task emitting any item.
    */
  final def timeout(after: FiniteDuration): Task[A] =
    timeoutTo(after, raiseError(new TimeoutException(s"Task timed-out after $after of inactivity")))

  /** Returns a Task that mirrors the source Task but switches to the
    * given backup Task in case the given duration passes without the
    * source emitting any item.
    */
  final def timeoutTo[B >: A](after: FiniteDuration, backup: Task[B]): Task[B] =
    Task.race(this, Task.unit.delayExecution(after)).flatMap {
      case Left(a) =>
        Task.now(a)
      case Right(_) =>
        backup
    }

  /** Returns a string representation of this task meant for
    * debugging purposes only.
    */
  override def toString: String = this match {
    case Now(a) => s"Task.Now($a)"
    case Error(e) => s"Task.Error($e)"
    case _ =>
      val n = this.getClass.getName.replaceFirst("^monix\\.eval\\.Task[$.]", "")
      s"Task.$n$$${System.identityHashCode(this)}"
  }

  /** Creates a new `Task` by applying the 'fa' function to the successful result of
    * this future, or the 'fe' function to the potential errors that might happen.
    *
    * This function is similar with [[map]], except that it can also transform
    * errors and not just successful results.
    *
    * @param fa function that transforms a successful result of the receiver
    * @param fe function that transforms an error of the receiver
    */
  final def transform[R](fa: A => R, fe: Throwable => R): Task[R] =
    transformWith(fa.andThen(nowConstructor), fe.andThen(nowConstructor))

  /** Creates a new `Task` by applying the 'fa' function to the successful result of
    * this future, or the 'fe' function to the potential errors that might happen.
    *
    * This function is similar with [[flatMap]], except that it can also transform
    * errors and not just successful results.
    *
    * @param fa function that transforms a successful result of the receiver
    * @param fe function that transforms an error of the receiver
    */
  final def transformWith[R](fa: A => Task[R], fe: Throwable => Task[R]): Task[R] =
    FlatMap(this, StackFrame.fold(fa, fe))

  /** Makes the source `Task` uninterruptible such that a [[cancel]]
    * signal has no effect.
    *
    * {{{
    *   val cancelable = Task
    *     .eval(println("Hello!"))
    *     .delayExecution(10.seconds)
    *     .runAsync
    *
    *   // No longer works
    *   cancelable.cancel()
    *
    *   // After 10 seconds
    *   //=> Hello!
    * }}}
    */
  final def uncancelable: Task[A] =
    TaskCancellation.uncancelable(this)

  /** Zips the values of `this` and `that` task, and creates a new task
    * that will emit the tuple of their results.
    */
  final def zip[B](that: Task[B]): Task[(A, B)] =
    Task.mapBoth(this, that)((a,b) => (a,b))

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  final def zipMap[B,C](that: Task[B])(f: (A,B) => C): Task[C] =
    Task.mapBoth(this, that)(f)
}

/** Builders for [[Task]].
  *
  * @define createAsyncDesc Create a `Task` from an
  *         asynchronous computation, which takes the form of a
  *         function with which we can register a callback.
  *
  *         This can be used to translate from a callback-based API to
  *         a straightforward monadic version.
  *
  * @define registerParamDesc is a function that will be called when
  *         this `Task` is executed, receiving a callback as a
  *         parameter, a callback that the user is supposed to call in
  *         order to signal the desired outcome of this `Task`.
  *
  * @define shiftDesc For example we can introduce an
  *         asynchronous boundary in the `flatMap` chain before a
  *         certain task, this being literally the implementation of
  *         [[Task.executeAsync executeAsync]]:
  *
  *         {{{
  *           Task.shift.flatMap(_ => task)
  *         }}}
  *
  *         And this can also be described with `followedBy` from Cats:
  *
  *         {{{
  *           import cats.syntax.all._
  *
  *           Task.shift.followedBy(task)
  *         }}}
  *
  *         Or we can specify an asynchronous boundary ''after''
  *         the evaluation of a certain task, this being literally
  *         the implementation of
  *         [[Task!.asyncBoundary:monix\.eval\.Task[A]* .asyncBoundary]]:
  *
  *         {{{
  *           task.flatMap(a => Task.shift.map(_ => a))
  *         }}}
  *
  *         And again we can also describe this with `forEffect`
  *         from Cats:
  *
  *         {{{
  *           task.forEffect(Task.shift)
  *         }}}
  */
object Task extends TaskInstancesLevel1 {
  /** Returns a new task that, when executed, will emit the result of
    * the given function, executed asynchronously.
    *
    * This operation is the equivalent of:
    * {{{
    *   Task.eval(f).executeAsync
    * }}}
    *
    * @param f is the callback to execute asynchronously
    */
  def apply[A](f: => A): Task[A] =
    eval(f).executeAsync

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Task[A] =
    Task.Now(a)

  /** Lifts a value into the task context. Alias for [[now]]. */
  def pure[A](a: A): Task[A] = now(a)

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def raiseError[A](ex: Throwable): Task[A] =
    Error(ex)

  /** Promote a non-strict value representing a Task to a Task of the
    * same type.
    */
  def defer[A](fa: => Task[A]): Task[A] =
    Suspend(fa _)

  /** Defers the creation of a `Task` by using the provided
    * function, which has the ability to inject a needed
    * [[monix.execution.Scheduler Scheduler]].
    *
    * Example:
    * {{{
    *   def measureLatency[A](source: Task[A]): Task[(A, Long)] =
    *     Task.deferAction { implicit s =>
    *       // We have our Scheduler, which can inject time, we
    *       // can use it for side-effectful operations
    *       val start = s.currentTimeMillis()
    *
    *       source.map { a =>
    *         val finish = s.currentTimeMillis()
    *         (a, finish - start)
    *       }
    *     }
    * }}}
    *
    * @param f is the function that's going to be called when the
    *        resulting `Task` gets evaluated
    */
  def deferAction[A](f: Scheduler => Task[A]): Task[A] =
    TaskDeferAction(f)

  /** Promote a non-strict Scala `Future` to a `Task` of the same type.
    *
    * The equivalent of doing:
    * {{{
    *   Task.defer(Task.fromFuture(fa))
    * }}}
    */
  def deferFuture[A](fa: => Future[A]): Task[A] =
    defer(fromFuture(fa))

  /** Wraps calls that generate `Future` results into [[Task]], provided
    * a callback with an injected [[monix.execution.Scheduler Scheduler]]
    * to act as the necessary `ExecutionContext`.
    *
    * This builder helps with wrapping `Future`-enabled APIs that need
    * an implicit `ExecutionContext` to work. Consider this example:
    *
    * {{{
    *   import scala.concurrent.{ExecutionContext, Future}
    *
    *   def sumFuture(list: Seq[Int])(implicit ec: ExecutionContext): Future[Int] =
    *     Future(list.sum)
    * }}}
    *
    * We'd like to wrap this function into one that returns a lazy
    * `Task` that evaluates this sum every time it is called, because
    * that's how tasks work best. However in order to invoke this
    * function an `ExecutionContext` is needed:
    *
    * {{{
    *   def sumTask(list: Seq[Int])(implicit ec: ExecutionContext): Task[Int] =
    *     Task.deferFuture(sumFuture(list))
    * }}}
    *
    * But this is not only superfluous, but against the best practices
    * of using `Task`. The difference is that `Task` takes a
    * [[monix.execution.Scheduler Scheduler]] (inheriting from
    * `ExecutionContext`) only when [[monix.eval.Task!.runAsync(cb* runAsync]]
    * happens. But with `deferFutureAction` we get to have an injected
    * `Scheduler` in the passed callback:
    *
    * {{{
    *   def sumTask(list: Seq[Int]): Task[Int] =
    *     Task.deferFutureAction { implicit scheduler =>
    *       sumFuture(list)
    *     }
    * }}}
    *
    * @param f is the function that's going to be executed when the task
    *        gets evaluated, generating the wrapped `Future`
    */
  def deferFutureAction[A](f: Scheduler => Future[A]): Task[A] =
    TaskFromFuture.deferAction(f)

  /** Alias for [[defer]]. */
  def suspend[A](fa: => Task[A]): Task[A] =
    Suspend(fa _)

  /** Promote a non-strict value to a Task that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](a: => A): Task[A] = {
    val coeval = Coeval.Once(a _)
    Eval(coeval)
  }

  /** Promote a non-strict value to a Task, catching exceptions in the
    * process.
    *
    * Note that since `Task` is not memoized, this will recompute the
    * value each time the `Task` is executed.
    */
  def eval[A](a: => A): Task[A] =
    Eval(a _)

  /** Alias for [[eval]]. */
  def delay[A](a: => A): Task[A] = eval(a)

  /** A [[Task]] instance that upon evaluation will never complete. */
  def never[A]: Task[A] = neverRef

  /** Builds a [[Task]] instance out of a `cats.Eval`. */
  def fromEval[A](a: cats.Eval[A]): Task[A] =
    Coeval.fromEval(a).task

  /** Builds a [[Task]] instance out of a `cats.effect.IO`. */
  def fromIO[A](a: IO[A]): Task[A] =
    TaskConversions.fromIO(a)

  /** Builds a [[Task]] instance out of any `F[_]` data type
    * that implements the `cats.effect.Effect` type class.
    *
    * Example that works out of the box:
    * {{{
    *   import cats.effect.IO
    *
    *   Task.fromEffect(IO("Hello!"))
    * }}}
    */
  def fromEffect[F[_], A](fa: F[A])(implicit F: Effect[F]): Task[A] =
    TaskConversions.fromEffect(fa)(F)

  /** Builds a [[Task]] instance out of a Scala `Try`. */
  def fromTry[A](a: Try[A]): Task[A] =
    a match {
      case Success(v) => Now(v)
      case Failure(ex) => Error(ex)
    }

  /** Keeps calling `f` until it returns a `Right` result.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[A,B](a: A)(f: A => Task[Either[A,B]]): Task[B] =
    Task.defer(f(a)).flatMap {
      case Left(continueA) => tailRecM(continueA)(f)
      case Right(b) => Task.now(b)
    }

  /** A `Task[Unit]` provided for convenience. */
  final val unit: Task[Unit] = Now(())

  /** Transforms a [[Coeval]] into a [[Task]]. */
  def coeval[A](a: Coeval[A]): Task[A] = Eval(a)

  /** $createAsyncDesc
    *
    * Alias for [[Task.create]].
    *
    * @param register $registerParamDesc
    */
  def async[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] =
    create(register)

  /** $createAsyncDesc
    *
    * @param register $registerParamDesc
    */
  def create[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] =
    TaskCreate(register)

  /** Constructs a lazy [[Task]] instance whose result
    * will be computed asynchronously.
    *
    * **WARNING:** Unsafe to use directly, only use if you know
    * what you're doing. For building `Task` instances safely
    * see [[create Task.create]].
    *
    * Rules of usage:
    *
    *  - the received `StackedCancelable` can be used to store
    *    cancelable references that will be executed upon cancel;
    *    every `push` must happen at the beginning, before any
    *    execution happens and `pop` must happen afterwards
    *    when the processing is finished, before signaling the
    *    result
    *  - the received `FrameRef` indicates the current frame
    *    index and must be reset on real asynchronous boundaries
    *    (which avoids doing extra async boundaries in batched
    *    execution mode)
    *  - before execution, an asynchronous boundary is recommended,
    *    to avoid stack overflow errors, but can happen using the
    *    scheduler's facilities for trampolined execution
    *  - on signaling the result (`onSuccess`, `onError`),
    *    another async boundary is necessary, but can also
    *    happen with the scheduler's facilities for trampolined
    *    execution (e.g. `asyncOnSuccess` and `asyncOnError`)
    *
    * **WARNING:** note that not only is this builder unsafe, but also
    * unstable, as the callback type is exposing volatile internal
    * implementation details. This builder is meant to create
    * optimized asynchronous tasks, but for normal usage prefer
    * [[Task.create]].
    */
  def unsafeCreate[A](register: (Context, Callback[A]) => Unit): Task[A] =
    Async(register)

  /** Converts the given Scala `Future` into a `Task`.
    *
    * NOTE: if you want to defer the creation of the future, use
    * in combination with [[defer]].
    */
  def fromFuture[A](f: Future[A]): Task[A] =
    TaskFromFuture.strict(f)

  /** Run two `Task` actions concurrently, and return the first to
    * finish, either in success or error. The loser of the race is
    * cancelled.
    *
    * The two tasks are executed in parallel, the winner being the
    * first that signals a result.
    *
    * As an example, this would be equivalent with [[Task.timeout]]:
    * {{{
    *   import scala.concurrent.duration._
    *
    *   val timeoutError = Task
    *     .raiseError(new TimeoutException)
    *     .delayExecution(5.seconds)
    *
    *   Task.race(myTask, timeoutError)
    * }}}
    *
    * Similarly [[Task.timeoutTo]] is expressed in terms of `race`.
    *
    * Also see [[racePair]] for a version that does not cancel
    * the loser automatically on successful results. And [[raceMany]]
    * for a version that races a whole list of tasks.
    */
  def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    TaskRace(fa, fb)

  /** Runs multiple `Task` actions concurrently, returning the
    * first to finish, either in success or error. All losers of the
    * race get cancelled.
    *
    * The tasks get executed in parallel, the winner being the first
    * that signals a result.
    *
    * {{{
    *   val list: List[Task[Int]] = List(t1, t2, t3, ???)
    *
    *   val winner: Task[Int] = Task.raceMany(list)
    * }}}
    *
    * See [[race]] or [[racePair]] for racing two tasks, for more
    * control.
    */
  def raceMany[A](tasks: TraversableOnce[Task[A]]): Task[A] =
    TaskRaceList(tasks)

  /** Run two `Task` actions concurrently, and returns a pair
    * containing both the winner's successful value and the loser
    * represented as a still-unfinished task.
    *
    * If the first task completes in error, then the result will
    * complete in error, the other task being cancelled.
    *
    * On usage the user has the option of cancelling the losing task,
    * this being equivalent with plain [[race]]:
    *
    * {{{
    *   val ta: Task[A] = ???
    *   val tb: Task[B] = ???
    *
    *   Task.racePair(ta, tb).flatMap {
    *     case Left((a, taskB)) =>
    *       taskB.cancel.map(_ => a)
    *     case Right((taskA, b)) =>
    *       taskA.cancel.map(_ => b)
    *   }
    * }}}
    *
    * See [[race]] for a simpler version that cancels the loser
    * immediately or [[raceMany]] that races collections of tasks.
    */
  def racePair[A,B](fa: Task[A], fb: Task[B]): Task[Either[(A, Task[B]), (Task[A], B)]] =
    TaskRacePair(fa, fb)

  /** Asynchronous boundary described as an effectful `Task` that
    * can be used in `flatMap` chains to "shift" the continuation
    * of the run-loop to another thread or call stack, managed by
    * the default [[monix.execution.Scheduler Scheduler]].
    *
    * This is the equivalent of `IO.shift`, except that Monix's `Task`
    * gets executed with an injected `Scheduler` in
    * [[Task.runAsync(implicit* .runAsync]] and that's going to be
    * the `Scheduler` responsible for the "shift".
    *
    * $shiftDesc
    */
  final val shift: Task[Unit] =
    shift(null)

  /** Asynchronous boundary described as an effectful `Task` that
    * can be used in `flatMap` chains to "shift" the continuation
    * of the run-loop to another call stack or thread, managed by
    * the given execution context.
    *
    * This is the equivalent of `IO.shift`.
    *
    * $shiftDesc
    */
  def shift(ec: ExecutionContext): Task[Unit] =
    Async[Unit] { (context, cb) =>
      val ec2 = if (ec eq null) context.scheduler else ec
      ec2.execute(new Runnable {
        def run(): Unit = {
          context.frameRef.reset()
          cb.onSuccess(())
        }
      })
    }

  /** Creates a new `Task` that will sleep for the given duration,
    * emitting a tick when that time span is over.
    *
    * As an example on evaluation this will print "Hello!" after
    * 3 seconds:
    *
    * {{{
    *   import scala.concurrent.duration._
    *
    *   Task.sleep(3.seconds).flatMap { _ =>
    *     Task.eval(println("Hello!"))
    *   }
    * }}}
    *
    * See [[Task.delayExecution]] for this operation described as
    * a method on `Task` references or [[Task.delayResult]] for the
    * helper that triggers the evaluation of the source on time, but
    * then delays the result.
    */
  def sleep(timespan: FiniteDuration): Task[Unit] =
    TaskSleep.apply(timespan)

  /** Given a `TraversableOnce` of tasks, transforms it to a task signaling
    * the collection, executing the tasks one by one and gathering their
    * results in the same collection.
    *
    * This operation will execute the tasks one by one, in order, which means that
    * both effects and results will be ordered. See [[gather]] and [[gatherUnordered]]
    * for unordered results or effects, and thus potential of running in parallel.
    *
    *  It's a simple version of [[traverse]].
    */
  def sequence[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    TaskSequence.list(in)(cbf)

  /** Given a `TraversableOnce[A]` and a function `A => Task[B]`, sequentially
    * apply the function to each element of the collection and gather their
    * results in the same collection.
    *
    *  It's a generalized version of [[sequence]].
    */
  def traverse[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Task[M[B]] =
    TaskSequence.traverse(in, f)(cbf)

  /** Nondeterministically gather results from the given collection of tasks,
    * returning a task that will signal the same type of collection of results
    * once all tasks are finished.
    *
    * This function is the nondeterministic analogue of `sequence` and should
    * behave identically to `sequence` so long as there is no interaction between
    * the effects being gathered. However, unlike `sequence`, which decides on
    * a total order of effects, the effects in a `gather` are unordered with
    * respect to each other.
    *
    * Although the effects are unordered, we ensure the order of results
    * matches the order of the input sequence. Also see [[gatherUnordered]]
    * for the more efficient alternative.
    */
  def gather[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    TaskGather[A, M](in, () => cbf(in))

   /** Given a `TraversableOnce[A]` and a function `A => Task[B]`,
   * nondeterministically apply the function to each element of the collection
   * and return a task that will signal a collection of the results once all
   * tasks are finished.
   *
   * This function is the nondeterministic analogue of `traverse` and should
   * behave identically to `traverse` so long as there is no interaction between
   * the effects being gathered. However, unlike `traverse`, which decides on
   * a total order of effects, the effects in a `wander` are unordered with
   * respect to each other.
   *
   * Although the effects are unordered, we ensure the order of results
   * matches the order of the input sequence. Also see [[wanderUnordered]]
   * for the more efficient alternative.
   *
   * It's a generalized version of [[gather]].
   */
  def wander[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Task[M[B]] =
    Task.eval(in.map(f)).flatMap(col => TaskGather[B, M](col, () => cbf(in)))

  /** Nondeterministically gather results from the given collection of tasks,
    * without keeping the original ordering of results.
    *
    * If the tasks in the list are set to execute asynchronously, forking
    * logical threads, then the tasks will execute in parallel.
    *
    * This function is similar to [[gather]], but neither the effects nor the
    * results will be ordered. Useful when you don't need ordering because:
    *
    *  - it has non-blocking behavior (but not wait-free)
    *  - it can be more efficient (compared with [[gather]]), but not
    *    necessarily (if you care about performance, then test)
    *
    * @param in is a list of tasks to execute
    */
  def gatherUnordered[A](in: TraversableOnce[Task[A]]): Task[List[A]] =
    TaskGatherUnordered(in)

  /** Given a `TraversableOnce[A]` and a function `A => Task[B]`,
    * nondeterministically apply the function to each element of the collection
    * without keeping the original ordering of the results.
    *
    * This function is similar to [[wander]], but neither the effects nor the
    * results will be ordered. Useful when you don't need ordering because:
    *
    *  - it has non-blocking behavior (but not wait-free)
    *  - it can be more efficient (compared with [[wander]]), but not
    *    necessarily (if you care about performance, then test)
    *
    * It's a generalized version of [[gatherUnordered]].
    */
  def wanderUnordered[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B]): Task[List[B]] =
    Task.eval(in.map(f)).flatMap(gatherUnordered)

  /** Apply a mapping functions to the results of two tasks, nondeterministically
    * ordering their effects.
    *
    * If the two tasks are synchronous, they'll get executed one
    * after the other, with the result being available asynchronously.
    * If the two tasks are asynchronous, they'll get scheduled for execution
    * at the same time and in a multi-threading environment they'll execute
    * in parallel and have their results synchronized.
    */
  def mapBoth[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] =
    TaskMapBoth(fa1, fa2)(f)

  /** Pairs 2 `Task` values, applying the given mapping function.
    *
    * Returns a new `Task` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * This is a specialized [[Task.sequence]] operation and as such
    * the tasks are evaluated in order, one after another, the
    * operation being described in terms of [[Task.flatMap .flatMap]].
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *
    *   // Yields Success(3)
    *   Task.map2(fa1, fa2) { (a, b) =>
    *     a + b
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.map2(fa1, Task.raiseError(e)) { (a, b) =>
    *     a + b
    *   }
    * }}}
    *
    * See [[Task.parMap2]] for parallel processing.
    */
  def map2[A1, A2, R](fa1: Task[A1], fa2: Task[A2])(f: (A1, A2) => R): Task[R] =
    fa1.zipMap(fa2)(f)

  /** Pairs 3 `Task` values, applying the given mapping function.
    *
    * Returns a new `Task` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * This is a specialized [[Task.sequence]] operation and as such
    * the tasks are evaluated in order, one after another, the
    * operation being described in terms of [[Task.flatMap .flatMap]].
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *
    *   // Yields Success(6)
    *   Task.map3(fa1, fa2, fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.map3(fa1, Task.raiseError(e), fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    * }}}
    *
    * See [[Task.parMap3]] for parallel processing.
    */
  def map3[A1, A2, A3, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])
    (f: (A1, A2, A3) => R): Task[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3)
      yield f(a1, a2, a3)
  }

  /** Pairs 4 `Task` values, applying the given mapping function.
    *
    * Returns a new `Task` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * This is a specialized [[Task.sequence]] operation and as such
    * the tasks are evaluated in order, one after another, the
    * operation being described in terms of [[Task.flatMap .flatMap]].
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *
    *   // Yields Success(10)
    *   Task.map4(fa1, fa2, fa3, fa4) { (a, b, c, d) =>
    *     a + b + c + d
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.map4(fa1, Task.raiseError(e), fa3, fa4) {
    *     (a, b, c, d) => a + b + c + d
    *   }
    * }}}
    *
    * See [[Task.parMap4]] for parallel processing.
    */
  def map4[A1, A2, A3, A4, R]
    (fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])
    (f: (A1, A2, A3, A4) => R): Task[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4)
      yield f(a1, a2, a3, a4)
  }

  /** Pairs 5 `Task` values, applying the given mapping function.
    *
    * Returns a new `Task` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * This is a specialized [[Task.sequence]] operation and as such
    * the tasks are evaluated in order, one after another, the
    * operation being described in terms of [[Task.flatMap .flatMap]].
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *   val fa5 = Task(5)
    *
    *   // Yields Success(15)
    *   Task.map5(fa1, fa2, fa3, fa4, fa5) { (a, b, c, d, e) =>
    *     a + b + c + d + e
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.map5(fa1, Task.raiseError(e), fa3, fa4, fa5) {
    *     (a, b, c, d, e) => a + b + c + d + e
    *   }
    * }}}
    *
    * See [[Task.parMap5]] for parallel processing.
    */
  def map5[A1, A2, A3, A4, A5, R]
    (fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])
    (f: (A1, A2, A3, A4, A5) => R): Task[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4; a5 <- fa5)
      yield f(a1, a2, a3, a4, a5)
  }

  /** Pairs 6 `Task` values, applying the given mapping function.
    *
    * Returns a new `Task` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * This is a specialized [[Task.sequence]] operation and as such
    * the tasks are evaluated in order, one after another, the
    * operation being described in terms of [[Task.flatMap .flatMap]].
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *   val fa5 = Task(5)
    *   val fa6 = Task(6)
    *
    *   // Yields Success(21)
    *   Task.map6(fa1, fa2, fa3, fa4, fa5, fa6) { (a, b, c, d, e, f) =>
    *     a + b + c + d + e + f
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.map6(fa1, Task.raiseError(e), fa3, fa4, fa5, fa6) {
    *     (a, b, c, d, e, f) => a + b + c + d + e + f
    *   }
    * }}}
    *
    * See [[Task.parMap6]] for parallel processing.
    */
  def map6[A1, A2, A3, A4, A5, A6, R]
    (fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6])
    (f: (A1, A2, A3, A4, A5, A6) => R): Task[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4; a5 <- fa5; a6 <- fa6)
      yield f(a1, a2, a3, a4, a5, a6)
  }

  /** Pairs 2 `Task` values, applying the given mapping function,
    * ordering the results, but not the side effects, the evaluation
    * being done in parallel if the tasks are async.
    *
    * This is a specialized [[Task.gather]] operation and as such
    * the tasks are evaluated in parallel, ordering the results.
    * In case one of the tasks fails, then all other tasks get
    * cancelled and the final result will be a failure.
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *
    *   // Yields Success(3)
    *   Task.parMap2(fa1, fa2) { (a, b) =>
    *     a + b
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.parMap2(fa1, Task.raiseError(e)) { (a, b) =>
    *     a + b
    *   }
    * }}}
    *
    * See [[Task.map2]] for sequential processing.
    */
  def parMap2[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] =
    Task.mapBoth(fa1, fa2)(f)

  /** Pairs 3 `Task` values, applying the given mapping function,
    * ordering the results, but not the side effects, the evaluation
    * being done in parallel if the tasks are async.
    *
    * This is a specialized [[Task.gather]] operation and as such
    * the tasks are evaluated in parallel, ordering the results.
    * In case one of the tasks fails, then all other tasks get
    * cancelled and the final result will be a failure.
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *
    *   // Yields Success(6)
    *   Task.parMap3(fa1, fa2, fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.parMap3(fa1, Task.raiseError(e), fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    * }}}
    *
    * See [[Task.map3]] for sequential processing.
    */
  def parMap3[A1,A2,A3,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])(f: (A1,A2,A3) => R): Task[R] = {
    val fa12 = zip2(fa1, fa2)
    parMap2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  /** Pairs 4 `Task` values, applying the given mapping function,
    * ordering the results, but not the side effects, the evaluation
    * being done in parallel if the tasks are async.
    *
    * This is a specialized [[Task.gather]] operation and as such
    * the tasks are evaluated in parallel, ordering the results.
    * In case one of the tasks fails, then all other tasks get
    * cancelled and the final result will be a failure.
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *
    *   // Yields Success(10)
    *   Task.parMap4(fa1, fa2, fa3, fa4) { (a, b, c, d) =>
    *     a + b + c + d
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.parMap4(fa1, Task.raiseError(e), fa3, fa4) {
    *     (a, b, c, d) => a + b + c + d
    *   }
    * }}}
    *
    * See [[Task.map4]] for sequential processing.
    */
  def parMap4[A1,A2,A3,A4,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])(f: (A1,A2,A3,A4) => R): Task[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    parMap2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  /** Pairs 5 `Task` values, applying the given mapping function,
    * ordering the results, but not the side effects, the evaluation
    * being done in parallel if the tasks are async.
    *
    * This is a specialized [[Task.gather]] operation and as such
    * the tasks are evaluated in parallel, ordering the results.
    * In case one of the tasks fails, then all other tasks get
    * cancelled and the final result will be a failure.
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *   val fa5 = Task(5)
    *
    *   // Yields Success(15)
    *   Task.parMap5(fa1, fa2, fa3, fa4, fa5) { (a, b, c, d, e) =>
    *     a + b + c + d + e
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.parMap5(fa1, Task.raiseError(e), fa3, fa4, fa5) {
    *     (a, b, c, d, e) => a + b + c + d + e
    *   }
    * }}}
    *
    * See [[Task.map5]] for sequential processing.
    */
  def parMap5[A1,A2,A3,A4,A5,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])(f: (A1,A2,A3,A4,A5) => R): Task[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    parMap2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  /** Pairs 6 `Task` values, applying the given mapping function,
    * ordering the results, but not the side effects, the evaluation
    * being done in parallel if the tasks are async.
    *
    * This is a specialized [[Task.gather]] operation and as such
    * the tasks are evaluated in parallel, ordering the results.
    * In case one of the tasks fails, then all other tasks get
    * cancelled and the final result will be a failure.
    *
    * {{{
    *   val fa1 = Task(1)
    *   val fa2 = Task(2)
    *   val fa3 = Task(3)
    *   val fa4 = Task(4)
    *   val fa5 = Task(5)
    *   val fa6 = Task(6)
    *
    *   // Yields Success(21)
    *   Task.parMap6(fa1, fa2, fa3, fa4, fa5, fa6) { (a, b, c, d, e, f) =>
    *     a + b + c + d + e + f
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Task.parMap6(fa1, Task.raiseError(e), fa3, fa4, fa5, fa6) {
    *     (a, b, c, d, e, f) => a + b + c + d + e + f
    *   }
    * }}}
    *
    * See [[Task.map6]] for sequential processing.
    */
  def parMap6[A1,A2,A3,A4,A5,A6,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6])(f: (A1,A2,A3,A4,A5,A6) => R): Task[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    parMap2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }

  /** Pairs two [[Task]] instances using [[parMap2]]. */
  def zip2[A1,A2,R](fa1: Task[A1], fa2: Task[A2]): Task[(A1,A2)] =
    Task.mapBoth(fa1, fa2)((_,_))

  /** Pairs three [[Task]] instances using [[parMap3]]. */
  def zip3[A1,A2,A3](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3]): Task[(A1,A2,A3)] =
    parMap3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))

  /** Pairs four [[Task]] instances using [[parMap4]]. */
  def zip4[A1,A2,A3,A4](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4]): Task[(A1,A2,A3,A4)] =
    parMap4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))

  /** Pairs five [[Task]] instances using [[parMap5]]. */
  def zip5[A1,A2,A3,A4,A5](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5]): Task[(A1,A2,A3,A4,A5)] =
    parMap5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))

  /** Pairs six [[Task]] instances using [[parMap6]]. */
  def zip6[A1,A2,A3,A4,A5,A6](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6]): Task[(A1,A2,A3,A4,A5,A6)] =
    parMap6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** A run-loop frame index is a number representing the current run-loop
    * cycle, being incremented whenever a `flatMap` evaluation happens.
    *
    * It gets used for automatically forcing asynchronous boundaries, according to the
    * [[monix.execution.ExecutionModel ExecutionModel]]
    * injected by the [[monix.execution.Scheduler Scheduler]] when
    * the task gets evaluated with `runAsync`.
    *
    * @see [[FrameIndexRef]]
    */
  type FrameIndex = Int

  /** Set of options for customizing the task's behavior.
    *
    * See [[Task.defaultOptions]] for the default `Options` instance
    * used by [[Task!.runAsync(implicit* .runAsync]].
    *
    * @param autoCancelableRunLoops should be set to `true` in
    *        case you want `flatMap` driven loops to be
    *        auto-cancelable. Defaults to `false`.
    *
    * @param localContextPropagation should be set to `true` in
    *        case you want the [[monix.execution.misc.Local Local]]
    *        variables to be propagated on async boundaries.
    *        Defaults to `false`.
    */
  final case class Options(
    autoCancelableRunLoops: Boolean,
    localContextPropagation: Boolean) {

    /** Creates a new set of options from the source, but with
      * the [[autoCancelableRunLoops]] value set to `true`.
      */
    def enableAutoCancelableRunLoops: Options =
      copy(autoCancelableRunLoops = true)

    /** Creates a new set of options from the source, but with
      * the [[autoCancelableRunLoops]] value set to `false`.
      */
    def disableAutoCancelableRunLoops: Options =
      copy(autoCancelableRunLoops = false)

    /** Creates a new set of options from the source, but with
      * the [[localContextPropagation]] value set to `true`.
      */
    def enableLocalContextPropagation: Options =
      copy(localContextPropagation = true)

    /** Creates a new set of options from the source, but with
      * the [[localContextPropagation]] value set to `false`.
      */
    def disableLocalContextPropagation: Options =
      copy(localContextPropagation = false)
  }

  /** Default [[Options]] to use for [[Task]] evaluation,
    * thus:
    *
    *  - `autoCancelableRunLoops` is `false` by default
    *  - `localContextPropagation` is `false` by default
    *
    * On top of the JVM the default can be overridden by
    * setting the following system properties:
    *
    *  - `monix.environment.autoCancelableRunLoops`
    *    (`true`, `yes` or `1` for enabling)
    *
    *  - `monix.environment.localContextPropagation`
    *    (`true`, `yes` or `1` for enabling)
    *
    * @see [[Task.Options]]
    */
  val defaultOptions: Options =
    Options(
      autoCancelableRunLoops = Platform.autoCancelableRunLoops,
      localContextPropagation = Platform.localContextPropagation
    )

  /** A reference that boxes a [[FrameIndex]] possibly using a thread-local.
    *
    * This definition is of interest only when creating
    * tasks with [[Task.unsafeCreate]], which exposes internals and
    * is considered unsafe to use.
    *
    * In case the [[Task]] is executed with
    * [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]],
    * this class boxes a [[FrameIndex]] in order to transport it over
    * light async boundaries, possibly using a
    * [[monix.execution.misc.ThreadLocal ThreadLocal]], since this
    * index is not supposed to survive when threads get forked.
    *
    * The [[FrameIndex]] is a counter that increments whenever a
    * `flatMap` operation is evaluated. And with `BatchedExecution`,
    * whenever that counter exceeds the specified threshold, an
    * asynchronous boundary is automatically inserted. However this
    * capability doesn't blend well with light asynchronous
    * boundaries, for example `Async` tasks that never fork logical threads or
    * [[monix.execution.schedulers.TrampolinedRunnable TrampolinedRunnable]]
    * instances executed by capable schedulers. This is why
    * [[FrameIndexRef]] is part of the [[Context]] of execution for
    * [[Task]], available for asynchronous tasks that get created with
    * [[Task.unsafeCreate]].
    *
    * Note that in case the execution model is not
    * [[monix.execution.ExecutionModel.BatchedExecution BatchedExecution]]
    * then this reference is just a dummy, since there's no point in
    * keeping a counter around, plus setting and fetching from a
    * `ThreadLocal` can be quite expensive.
    */
  sealed abstract class FrameIndexRef {
    /** Returns the current [[FrameIndex]]. */
    def apply(): FrameIndex

    /** Stores a new [[FrameIndex]]. */
    def `:=`(update: FrameIndex): Unit

    /** Resets the stored [[FrameIndex]] to 1, which is the
      * default value that should be used after an asynchronous
      * boundary happened.
      */
    def reset(): Unit
  }

  object FrameIndexRef {
    /** Builds a [[FrameIndexRef]]. */
    def apply(em: ExecutionModel): FrameIndexRef =
      em match {
        case AlwaysAsyncExecution | SynchronousExecution => Dummy
        case BatchedExecution(_) => new Local
      }

    // Keeps our frame index in a thread-local
    private final class Local extends FrameIndexRef {
      private[this] val local = ThreadLocal(1)
      def apply(): FrameIndex = local.get()
      def `:=`(update: FrameIndex): Unit = local.set(update)
      def reset(): Unit = local.reset()
    }

    // Dummy implementation that doesn't do anything
    private object Dummy extends FrameIndexRef {
      def apply(): FrameIndex = 1
      def `:=`(update: FrameIndex): Unit = ()
      def reset(): Unit = ()
    }
  }

  /** The `Context` under which [[Task]] is supposed to be executed.
    *
    * This definition is of interest only when creating
    * tasks with [[Task.unsafeCreate]], which exposes internals and
    * is considered unsafe to use.
    *
    * @param scheduler is the [[monix.execution.Scheduler Scheduler]]
    *        in charge of evaluation on `runAsync`.
    *
    * @param connection is the
    *        [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    *        that handles the cancellation on `runAsync`
    *
    * @param frameRef is a thread-local counter that keeps track
    *        of the current frame index of the run-loop. The run-loop
    *        is supposed to force an asynchronous boundary upon
    *        reaching a certain threshold, when the task is evaluated
    *        with
    *        [[monix.execution.ExecutionModel.BatchedExecution]].
    *        And this `frameIndexRef` should be reset whenever a real
    *        asynchronous boundary happens.
    *
    *        See the description of [[FrameIndexRef]].
    *
    * @param options is a set of options for customizing the task's
    *        behavior upon evaluation.
    */
  final case class Context(
    scheduler: Scheduler,
    connection: StackedCancelable,
    frameRef: FrameIndexRef,
    options: Options) {

    /** Helper that returns the
      * [[monix.execution.ExecutionModel ExecutionModel]]
      * specified by the [[scheduler]].
      */
    def executionModel: ExecutionModel =
      scheduler.executionModel

    /** Helper that returns `true` if the current `Task` run-loop
      * should be canceled or `false` otherwise.
      */
    def shouldCancel: Boolean =
      options.autoCancelableRunLoops &&
      connection.isCanceled
  }

  object Context {
    /** Initialize fresh [[Context]] reference. */
    def apply(s: Scheduler, opts: Options): Context = {
      val conn = StackedCancelable()
      val em = s.executionModel
      val frameRef = FrameIndexRef(em)
      Context(s, conn, frameRef, opts)
    }
  }

  // -- DEPRECATIONS

  /** DEPRECATED — please use [[Task!.executeAsync .executeAsync]].
    *
    * The reason for the deprecation is the repurposing of the word "fork".
    */
  @deprecated("Please use Task!.executeAsync", "3.0.0")
  def fork[A](fa: Task[A]): Task[A] = {
    // $COVERAGE-OFF$
    fa.executeAsync
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please use [[Task.executeOn .executeOn]].
    *
    * The reason for the deprecation is the repurposing of the word "fork".
    */
  @deprecated("Please use Task!.executeOn", "3.0.0")
  def fork[A](fa: Task[A], s: Scheduler): Task[A] = {
  // $COVERAGE-OFF$
    fa.executeOn(s)
    // $COVERAGE-ON$
  }

  implicit final class DeprecatedExtensions[A](val self: Task[A]) extends AnyVal {
    /** DEPRECATED - renamed to [[Task.executeAsync executeAsync]].
      *
      * The reason for the deprecation is the repurposing of the word "fork".
      */
    @deprecated("Renamed to Task!.executeAsync", "3.0.0")
    def executeWithFork: Task[A] = {
      // $COVERAGE-OFF$
      self.executeAsync
      // $COVERAGE-ON$
    }

    /** DEPRECATED - please use [[Task.flatMap flatMap]].
      *
      * The reason for the deprecation is that this operation is
      * redundant, as it can be expressed with `flatMap`, with the
      * same effect:
      * {{{
      *   trigger.flatMap(_ => task)
      * }}}
      *
      * The syntax provided by Cats can also help:
      * {{{
      *   import cats.syntax.all._
      *
      *   trigger *> task
      * }}}
      */
    @deprecated("Please use flatMap", "3.0.0")
    def delayExecutionWith(trigger: Task[Any]): Task[A] = {
      // $COVERAGE-OFF$
      trigger.flatMap(_ => self)
      // $COVERAGE-ON$
    }

    /** DEPRECATED - please use [[Task.flatMap flatMap]].
      *
      * The reason for the deprecation is that this operation is
      * redundant, as it can be expressed with `flatMap` and `map`,
      * with the same effect:
      *
      * {{{
      *   task.flatMap(a => selector(a).map(_ => a))
      * }}}
      */
    @deprecated("Please rewrite in terms of flatMap", "3.0.0")
    def delayResultBySelector[B](selector: A => Task[B]): Task[A] = {
      // $COVERAGE-OFF$
      self.flatMap(a => selector(a).map(_ => a))
      // $COVERAGE-OFF$
    }
  }

  // -- INTERNALS

  /** [[Task]] state describing an immediate synchronous value. */
  private[eval] final case class Now[A](value: A) extends Task[A] {
    // Optimizations to avoid the run-loop
    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
      if (s.executionModel != AlwaysAsyncExecution) cb.onSuccess(value)
      else s.executeAsync(() => cb.onSuccess(value))
      Cancelable.empty
    }
    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      CancelableFuture.successful(value)
    override def runAsyncOpt(implicit s: Scheduler, opts: Options): CancelableFuture[A] =
      runAsync(s)
    override def runAsyncOpt(cb: Callback[A])(implicit s: Scheduler, opts: Options): Cancelable =
      runAsync(cb)(s)
  }

  /** [[Task]] state describing an immediate exception. */
  private[eval] final case class Error[A](ex: Throwable) extends Task[A] {
    // Optimizations to avoid the run-loop
    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
      if (s.executionModel != AlwaysAsyncExecution) cb.onError(ex)
      else s.executeAsync(() => cb.onError(ex))
      Cancelable.empty
    }
    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      CancelableFuture.failed(ex)
    override def runAsyncOpt(implicit s: Scheduler, opts: Options): CancelableFuture[A] =
      runAsync(s)
    override def runAsyncOpt(cb: Callback[A])(implicit s: Scheduler, opts: Options): Cancelable =
      runAsync(cb)(s)
  }

  /** [[Task]] state describing an immediate synchronous value. */
  private[eval] final case class Eval[A](thunk: () => A)
    extends Task[A]

  /** Internal state, the result of [[Task.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Task[A])
    extends Task[A]

  /** Internal [[Task]] state that is the result of applying `flatMap`. */
  private[eval] final case class FlatMap[A, B](source: Task[A], f: A => Task[B])
    extends Task[B]

  /** Internal [[Coeval]] state that is the result of applying `map`. */
  private[eval] final case class Map[S, +A](source: Task[S], f: S => A, index: Int)
    extends Task[A] with (S => Task[A]) {

    def apply(value: S): Task[A] =
      new Now(f(value))
    override def toString: String =
      super[Task].toString
  }

  /** Constructs a lazy [[Task]] instance whose result will
    * be computed asynchronously.
    *
    * Unsafe to build directly, only use if you know what you're doing.
    * For building `Async` instances safely, see [[create]].
    */
  private[eval] final case class Async[+A](register: (Context, Callback[A]) => Unit)
    extends Task[A]

  /** Internal [[Task]] state that defers the evaluation of the
    * given [[Task]] and upon execution memoize its result to
    * be available for later evaluations.
    */
  private[eval] final class MemoizeSuspend[A](
    f: () => Task[A],
    private[eval] val cacheErrors: Boolean)
    extends Task[A] {

    private[eval] var thunk: () => Task[A] = f
    private[eval] val state = Atomic(null : AnyRef)

    def isCachingAll: Boolean =
      cacheErrors

    def value: Option[Try[A]] =
      state.get match {
        case null => None
        case (p: Promise[_], _) =>
          p.asInstanceOf[Promise[A]].future.value
        case result: Try[_] =>
          Some(result.asInstanceOf[Try[A]])
      }

    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable =
      state.get match {
        case null =>
          super.runAsync(cb)(s)
        case (p: Promise[_], conn: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          f.onComplete(cb)
          conn
        case result: Try[_] =>
          cb(result.asInstanceOf[Try[A]])
          Cancelable.empty
      }

    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      state.get match {
        case null =>
          super.runAsync(s)
        case (p: Promise[_], conn: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          CancelableFuture(f, conn)
        case result: Try[_] =>
          CancelableFuture.fromTry(result.asInstanceOf[Try[A]])
      }
  }

  /** Unsafe utility - starts the execution of a Task with a guaranteed
    * asynchronous boundary, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]]
    * and [[Task.executeAsync .executeAsync]].
    */
  def unsafeStartAsync[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    TaskRunLoop.restartAsync(source, context, cb, null, null, null)

  /** Unsafe utility - starts the execution of a Task with a guaranteed
    * [[monix.execution.schedulers.TrampolinedRunnable trampolined asynchronous boundary]],
    * by providing the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]]
    * and [[Task.executeAsync .executeAsync]].
    */
  def unsafeStartTrampolined[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    context.scheduler.execute(new TrampolinedRunnable {
      def run(): Unit =
        TaskRunLoop.startFull(source, context, cb, null, null, null, context.frameRef())
    })

  /** Unsafe utility - starts the execution of a Task, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]].
    */
  def unsafeStartNow[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    TaskRunLoop.startFull(source, context, cb, null, null, null, context.frameRef())

  private[this] final val neverRef: Async[Nothing] =
    Async((_,_) => ())

  /** Internal, reusable reference. */
  private final val nowConstructor: (Any => Task[Nothing]) =
    ((a: Any) => new Now(a)).asInstanceOf[Any => Task[Nothing]]
  /** Internal, reusable reference. */
  private final val raiseConstructor: (Throwable => Task[Nothing]) =
    e => new Error(e)

  /** Used as optimization by [[Task.attempt]]. */
  private object AttemptTask extends StackFrame[Any, Task[Either[Throwable, Any]]] {
    override def apply(a: Any): Task[Either[Throwable, Any]] =
      new Now(new Right(a))
    override def recover(e: Throwable, r: UncaughtExceptionReporter): Task[Either[Throwable, Any]] =
      new Now(new Left(e))
  }

  /** Used as optimization by [[Task.materialize]]. */
  private object MaterializeTask extends StackFrame[Any, Task[Try[Any]]] {
    override def apply(a: Any): Task[Try[Any]] =
      new Now(new Success(a))
    override def recover(e: Throwable, r: UncaughtExceptionReporter): Task[Try[Any]] =
      new Now(new Failure(e))
  }
}

private[eval] abstract class TaskInstancesLevel1 extends TaskInstancesLevel0 {
  /** Global instance for `cats.effect.Async`.
    *
    * Implied are `cats.CoflatMap`, `cats.Applicative`, `cats.Monad`,
    * `cats.MonadError` and `cats.effect.Sync`.
    *
    * Seek more info about Cats, the standard library for FP, at:
    *
    *  - [[https://typelevel.org/cats/ typelevel/cats]]
    *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
    */
  implicit def catsAsync: CatsAsyncForTask =
    CatsAsyncForTask

  /** Global instance for `cats.Parallel`.
    *
    * The `Parallel` type class is useful for processing
    * things in parallel in a generic way, usable with
    * Cats' utils and syntax:
    *
    * {{{
    *   import cats.syntax.all._
    *
    *   (taskA, taskB, taskC).parMap { (a, b, c) =>
    *     a + b + c
    *   }
    * }}}
    *
    * Seek more info about Cats, the standard library for FP, at:
    *
    *  - [[https://typelevel.org/cats/ typelevel/cats]]
    *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
    */
  implicit def catsParallel: CatsParallelForTask =
    CatsParallelForTask

  /** Given an `A` type that has a `cats.Monoid[A]` implementation,
    * then this provides the evidence that `Task[A]` also has
    * a `Monoid[Task[A]]` implementation.
    */
  implicit def catsMonoid[A](implicit A: Monoid[A]): Monoid[Task[A]] =
    new CatsMonadToMonoid[Task, A]()(CatsAsyncForTask, A)
}

private[eval] abstract class TaskInstancesLevel0 extends TaskParallelNewtype {
  /** Global instance for `cats.effect.Effect`.
    *
    * Implied are `cats.CoflatMap`, `cats.Applicative`, `cats.Monad`,
    * `cats.MonadError`, `cats.effect.Sync` and `cats.effect.Async`.
    *
    * Note this is different from
    * [[monix.eval.Task.catsAsync Task.catsAsync]] because we need an
    * implicit [[monix.execution.Scheduler Scheduler]] in scope in
    * order to trigger the execution of a `Task`. It's also lower
    * priority in order to not trigger conflicts, because
    * `Effect <: Async`
    *
    * Seek more info about Cats, the standard library for FP, at:
    *
    *  - [[https://typelevel.org/cats/ typelevel/cats]]
    *  - [[https://github.com/typelevel/cats-effect typelevel/cats-effect]]
    *
    * @param ec is a [[monix.execution.Scheduler Scheduler]] that needs
    *        to be available in scope
    */
  implicit def catsEffect(implicit ec: Scheduler): CatsEffectForTask =
    new CatsEffectForTask

  /** Given an `A` type that has a `cats.Semigroup[A]` implementation,
    * then this provides the evidence that `Task[A]` also has
    * a `Semigroup[Task[A]]` implementation.
    *
    * This has a lower-level priority than [[Task.catsMonoid]]
    * in order to avoid conflicts.
    */
  implicit def catsSemigroup[A](implicit A: Semigroup[A]): Semigroup[Task[A]] =
    new CatsMonadToSemigroup[Task, A]()(CatsAsyncForTask, A)
}

private[eval] abstract class TaskParallelNewtype {
  /** Newtype encoding for an `Task` datatype that has a [[cats.Applicative]]
    * capable of doing parallel processing in `ap` and `map2`, needed
    * for implementing [[cats.Parallel]].
    *
    * Helpers are provided for converting back and forth in `Par.apply`
    * for wrapping any `Task` value and `Par.unwrap` for unwrapping.
    *
    * The encoding is based on the "newtypes" project by
    * Alexander Konovalov, chosen because it's devoid of boxing issues and
    * a good choice until opaque types will land in Scala.
    */
  type Par[+A] = Par.Type[A]

  /** Newtype encoding, see the [[Task.Par]] type alias
    * for more details.
    */
  object Par extends Newtype1[Task]
}