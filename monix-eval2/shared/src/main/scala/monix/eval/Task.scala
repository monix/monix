/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.kernel.{ Async, Deferred, Fiber, Outcome, Poll, Ref }
import cats.instances.list._
import monix.eval.internal.TaskFiber
import monix.eval.instances.CatsAsyncForTask
import monix.execution.{ Callback, Cancelable, CancelableFuture, Scheduler }
import monix.execution.compat.BuildFrom

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

/** A lazy computation that, when run, may produce a value or fail with an error.
  *
  * A `Task` does not evaluate its computation when it is created. It starts only
  * when it is run, and can be run more than once.
  */
sealed abstract class Task[+A] {
  private[eval] def tag: Byte
  private[eval] def accept[AA >: A, R](visitor: Task.Visitor[AA, R]): R

  def flatMap[B](f: A => Task[B]): Task[B] =
    Task.FlatMap(this, f)

  def map[B](f: A => B): Task[B] =
    flatMap(a => Task.pure(f(a)))

  /** Returns this task mapped to the supplied value. */
  def as[B](value: B): Task[B] = map(_ => value)

  /** Returns this task mapped to `Unit`. */
  def void: Task[Unit] = as(())

  /** Given a source task that emits another task, flattens the result and returns the emitted task. */
  def flatten[B](implicit ev: A <:< Task[B]): Task[B] = flatMap(ev)

  /** Runs this task first and then, when successful, the given task. Returns the result of the given task.
    *
    * Example:
    * {{{
    *   val combined = Task { println("first"); "first" } >> Task { println("second"); "second" }
    * }}}
    */
  def >>[B](next: => Task[B]): Task[B] = flatMap(_ => next)

  /** Runs this task first and then, when successful, the given task. Returns the result of the given task.
    *
    * See [[>>]] for the version with a by-name parameter.
    */
  def *>[B](next: Task[B]): Task[B] = flatMap(_ => next)

  /** Runs this task first and then, when successful, the given task. Returns the result of this task. */
  def <*[B](next: Task[B]): Task[A] = flatMap(a => next.as(a))

  /** Creates a new task that runs the given function on success and returns the original result. */
  def tapEval[B](f: A => Task[B]): Task[A] = flatMap(a => f(a).as(a))

  /** Alias for [[tapEval]]. */
  def flatTap[B](f: A => Task[B]): Task[A] = tapEval(f)

  /** Applies the given function to the result and returns a task of `Unit`.
    *
    * The function is applied when the task is run.
    */
  def foreachL(f: A => Unit): Task[Unit] = map(f)

  /** Repeatedly executes this task as long as it succeeds. It never produces a terminal value.
    *
    * Example:
    * {{{
    *   import scala.concurrent.duration._
    *   Task.eval(println("Tick!")).delayExecution(1.second).loopForever
    * }}}
    */
  def loopForever: Task[Nothing] = flatMap(_ => loopForever)

  /** Exposes an error from this task as a `Left`; a successful result becomes a `Right`. */
  def attempt: Task[Either[Throwable, A]] =
    map[Either[Throwable, A]](Right(_)).handleErrorWith(e => Task.pure(Left(e)))

  /** Exposes the result of this task as a `Try`, whether it succeeds or fails. */
  def materialize: Task[Try[A]] =
    map[Try[A]](Success(_)).handleErrorWith(e => Task.pure(Failure(e)))

  /** Converts a `Try` result back into a task. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Task[B] =
    flatMap(a => Task.fromTry(ev(a)))

  /** Transforms the result or recovers an error from this task.
    *
    * This is equivalent to `attempt.map(_.fold(recover, map))`.
    *
    * Example:
    * {{{
    *   Task.eval("42".toInt).redeem(_ => 0, value => value + 1)
    * }}}
    */
  def redeem[B](recover: Throwable => B, map: A => B): Task[B] =
    attempt.map(_.fold(recover, map))

  /** Transforms the result or recovers an error from this task with another task.
    *
    * This is equivalent to `attempt.flatMap(_.fold(recover, bind))`.
    */
  def redeemWith[B](recover: Throwable => Task[B], bind: A => Task[B]): Task[B] =
    attempt.flatMap(_.fold(recover, bind))

  def handleErrorWith[AA >: A](f: Throwable => Task[AA]): Task[AA] =
    Task.HandleErrorWith(this, f)

  /** Recovers from an error by running another task. */
  def onErrorHandleWith[B >: A](f: Throwable => Task[B]): Task[B] = handleErrorWith(f)

  /** Recovers from an error by mapping it to a value. */
  def onErrorHandle[B >: A](f: Throwable => B): Task[B] =
    handleErrorWith(e => Task.eval(f(e)))

  /** Recovers from matching errors by running another task. */
  def onErrorRecoverWith[B >: A](f: PartialFunction[Throwable, Task[B]]): Task[B] =
    handleErrorWith(e => f.applyOrElse(e, (error: Throwable) => Task.raiseError[B](error)))

  /** Recovers from matching errors by mapping them to values. */
  def onErrorRecover[B >: A](f: PartialFunction[Throwable, B]): Task[B] =
    onErrorRecoverWith(f.andThen(value => Task.pure(value)))

  /** Runs the backup task if this task fails. */
  def onErrorFallbackTo[B >: A](backup: Task[B]): Task[B] = handleErrorWith(_ => backup)

  /** Runs the given function if this task fails, then raises the original error if the function succeeds.
    *
    * If the function fails, its error is returned instead.
    *
    * Example:
    * {{{
    *   Task.raiseError[Int](new RuntimeException("source"))
    *     .tapError(error => Task.eval(println(error)))
    * }}}
    */
  def tapError[B](f: Throwable => Task[B]): Task[A] =
    handleErrorWith(e => f(e).flatMap(_ => Task.raiseError(e)))

  /** Retries this task after an error.
    *
    * For a positive `maxRetries`, the total number of executions is at most `maxRetries + 1`.
    * A nonpositive value does not retry.
    */
  def onErrorRestart(maxRetries: Long): Task[A] =
    handleErrorWith(e => if (maxRetries > 0) onErrorRestart(maxRetries - 1) else Task.raiseError(e))

  /** Retries this task after an error for as long as the predicate returns `true`. */
  def onErrorRestartIf(p: Throwable => Boolean): Task[A] =
    handleErrorWith(e => if (p(e)) onErrorRestartIf(p) else Task.raiseError(e))

  /** Restarts this task after an error using the supplied state to choose the next task. */
  def onErrorRestartLoop[S, B >: A](initial: S)(f: (Throwable, S, S => Task[B]) => Task[B]): Task[B] =
    handleErrorWith[B](e => f(e, initial, state => onErrorRestartLoop[S, B](state)(f)))

  /** Repeats this task until the predicate returns `true`. */
  def restartUntil(p: A => Boolean): Task[A] =
    flatMap(a => if (p(a)) Task.pure(a) else restartUntil(p))

  /** Returns the error as a successful value, or fails with `NoSuchElementException` if this task succeeds. */
  def failed: Task[Throwable] =
    redeemWith(Task.pure, _ => Task.raiseError(new NoSuchElementException("Task.failed")))

  /** Runs the given task if this task is canceled. */
  def onCancel(fin: Task[Unit]): Task[A] =
    Task.OnCancel(this, fin)

  /** Alias for [[onCancel]]. */
  def doOnCancel(fin: Task[Unit]): Task[A] = onCancel(fin)

  /** Runs the given function when this task succeeds or fails, but not when it is canceled. */
  def doOnFinish(f: Option[Throwable] => Task[Unit]): Task[A] =
    guaranteeCase[A] { (outcome: Outcome[Task, Throwable, A]) =>
      outcome match {
        case Outcome.Succeeded(_) => f(None)
        case Outcome.Errored(error) => f(Some(error))
        case Outcome.Canceled() => Task.unit
      }
    }

  /** Makes this task uninterruptible until it completes. */
  def uncancelable: Task[A] = Task.uncancelable(_ => this)

  /** Runs the given finalizer when this task succeeds, fails, or is canceled.
    *
    * @see [[guaranteeCase]] for the version that can distinguish between termination conditions
    * @see [[bracket]] for the operation that acquires and releases a resource
    */
  def guarantee(finalizer: Task[Unit]): Task[A] =
    Task.catsEffectAsyncForTask.guarantee(this, finalizer)

  /** Runs the given finalizer when this task succeeds, fails, or is canceled, passing the result to the function.
    *
    * The `Outcome` distinguishes success, error, and cancellation. Cancellation waits for the finalizer to finish.
    * See [[guarantee]] for the version that does not inspect the result.
    */
  def guaranteeCase[B >: A](finalizer: Outcome[Task, Throwable, B] => Task[Unit]): Task[B] =
    Task.catsEffectAsyncForTask.guaranteeCase[B](this)(finalizer)

  /** Acquires a resource, uses it, and releases it when `use` completes or is canceled.
    *
    * The `bracket` operation releases the resource if `use` succeeds, fails, or is canceled.
    *
    * Example:
    * {{{
    *   import scala.io.Source
    *   Task.eval(Source.fromString("hello"))
    *     .bracket(source => Task.eval(source.mkString))(source => Task.eval(source.close()))
    * }}}
    */
  def bracket[B](use: A => Task[B])(release: A => Task[Unit]): Task[B] =
    Task.catsEffectAsyncForTask.bracket(this)(use)(release)

  /** Acquires a resource, uses it, and releases it with the outcome of `use`.
    *
    * The `Outcome` distinguishes success, error, and cancellation. Cancellation waits for release to finish.
    * See [[bracket]] for the version that does not inspect the outcome.
    */
  def bracketCase[B](use: A => Task[B])(release: (A, Outcome[Task, Throwable, B]) => Task[Unit]): Task[B] =
    Task.catsEffectAsyncForTask.bracketCase(this)(use)(release)

  /** Waits for the given duration before evaluating this task. */
  def delayExecution(delay: FiniteDuration): Task[A] = Task.sleep(delay).flatMap(_ => this)

  /** Returns a task that evaluates this task when run and delays its successful result by the given duration.
    *
    * Errors are returned without delay.
    *
    * Example:
    * {{{
    *   import scala.concurrent.duration._
    *   Task(1 + 1).delayResult(3.seconds)
    * }}}
    *
    * This is equivalent to `flatMap(a => Task.sleep(delay).map(_ => a))`.
    */
  def delayResult(delay: FiniteDuration): Task[A] = flatMap(a => Task.sleep(delay).as(a))

  /** Returns a task that fails with a timeout error if this task does not complete within the given duration. */
  def timeout(after: FiniteDuration): Task[A] = Task.catsEffectAsyncForTask.timeout(this, after)

  /** Returns a task that fails with the given exception if this task does not complete within the given duration. */
  def timeoutWith(after: FiniteDuration, exception: Exception): Task[A] =
    timeoutTo(after, Task.raiseError(exception))

  /** Returns a task that runs the backup if this task does not complete within the given duration.
    *
    * On timeout, this task is canceled and its finalizers finish before the backup starts.
    */
  def timeoutTo[B >: A](after: FiniteDuration, backup: Task[B]): Task[B] =
    Task.catsEffectAsyncForTask.timeoutTo(this: Task[B], after, backup)

  /** Uses a task to determine the duration before timing out this task. */
  def timeoutL(after: Task[FiniteDuration]): Task[A] = after.flatMap(timeout)

  /** Uses a task to determine the duration before running the backup task. */
  def timeoutToL[B >: A](after: Task[FiniteDuration], backup: Task[B]): Task[B] =
    after.flatMap(duration => timeoutTo(duration, backup))

  /** Returns the elapsed duration and the result of this task. */
  def timed: Task[(FiniteDuration, A)] = Task.catsEffectAsyncForTask.timed(this)

  /** Evaluates this task on the given execution context, then restores the previous context. */
  def evalOn(ec: ExecutionContext): Task[A] = Task.EvalOn(this, ec)

  /** Evaluates this task on the given scheduler, then restores the previous scheduler. */
  def executeOn(s: Scheduler): Task[A] = evalOn(s)

  /** Reschedules execution before evaluating this task. */
  def executeAsync: Task[A] = Task.shift.flatMap(_ => this)

  /** Reschedules execution after this task succeeds. */
  def asyncBoundary: Task[A] = flatMap(a => Task.shift.as(a))

  def start: Task[Fiber[Task, Throwable, A @uncheckedVariance]] =
    Task.Start(this)

  /** Starts this task in a new fiber and discards its result. */
  def startAndForget: Task[Unit] = start.void

  /** Starts a new fiber and returns its effectful cancellation action.
    * Evaluating the returned token requests cancellation and waits for finalizers;
    * merely obtaining it does not cancel the fiber.
    */
  def unsafeRunAsync(cb: Callback[Throwable, A])(implicit s: Scheduler): CancelToken[Task] = {
    val fiber = new TaskFiber[A](this, Callback.safe(cb))(s)
    s.execute(fiber)
    fiber.cancel
  }

  def unsafeRunAndForget()(implicit s: Scheduler): Unit = {
    val _ = unsafeRunAsync(Callback.empty)
  }

  /** Starts this Task and exposes its success/error channel as a `CancelableFuture`.
    * Future cancellation evaluates the fiber's effectful cancel token in a separate
    * run. Fiber cancellation has no callback value, so the canceled future remains
    * incomplete.
    */
  def unsafeRunToFuture()(implicit s: Scheduler): CancelableFuture[A] = {
    val p = Promise[A]()
    val cancel = unsafeRunAsync(Callback.fromPromise(p))
    CancelableFuture(p.future, Cancelable(cancel.unsafeRunAndForget _))
  }
}

object Task {

  implicit lazy val catsEffectAsyncForTask: Async[Task] =
    new CatsAsyncForTask

  def pure[A](a: A): Task[A] = Pure(a)

  def delay[A](thunk: => A): Task[A] =
    Delay(() => thunk)

  def defer[A](thunk: => Task[A]): Task[A] =
    Defer(() => thunk)

  def raiseError[A](e: Throwable): Task[A] =
    RaiseError(e)

  def never[A]: Task[A] =
    AsyncSimple((_, _) => ())

  val canceled: Task[Unit] =
    Cancelled

  def uncancelable[A](body: Poll[Task] => Task[A]): Task[A] =
    Uncancelable(body)

  def async[A](register: Callback[Throwable, A] => Unit): Task[A] =
    AsyncSimple((sc, cb) => protectRegistration(sc, cb)(register))

  def async0[A](register: (Scheduler, Callback[Throwable, A]) => Unit): Task[A] =
    AsyncSimple((sc, cb) => protectRegistration(sc, cb)(safe => register(sc, safe)))

  def cont[S, A](cont: (Callback[Throwable, S], Task[S]) => Task[A]): Task[A] =
    AsyncCont[S, A]((_, cb, get) => cont(cb, get))

  def cont0[S, A](cont: (Scheduler, Callback[Throwable, S], Task[S]) => Task[A]): Task[A] =
    AsyncCont(cont)

  /** Promotes a non-strict value into a task, evaluating it each time the task is run. */
  def apply[A](thunk: => A): Task[A] =
    eval(thunk)

  /** Returns a task that emits the given value when run. */
  def now[A](value: A): Task[A] =
    pure(value)

  /** Lifts a non-strict value into a task, evaluating it each time the task is run. */
  def eval[A](thunk: => A): Task[A] =
    delay(thunk)

  /** Lifts a non-strict value into a task that evaluates it once and reuses the result.
    *
    * Example:
    * {{{
    *   import scala.util.Random
    *   val randomNumber = Task.evalOnce(Random.nextInt())
    * }}}
    */
  def evalOnce[A](thunk: => A): Task[A] = {
    lazy val result: Try[A] = Try(thunk)
    defer(fromTry(result))
  }

  /** Lifts a value into a task that reschedules before evaluating it.
    *
    * This is equivalent to `Task.eval(thunk).executeAsync`.
    */
  def evalAsync[A](thunk: => A): Task[A] =
    shift >> eval(thunk)

  /** A task that emits `Unit`. */
  val unit: Task[Unit] =
    pure(())

  /** Converts a successful or failed `Try` into a task. */
  def fromTry[A](result: Try[A]): Task[A] =
    result match {
      case Success(value) => pure(value)
      case Failure(error) => raiseError(error)
    }

  /** Converts a `Right` into a successful task and a `Left` into a failed task. */
  def fromEither[E <: Throwable, A](value: Either[E, A]): Task[A] =
    value match {
      case Right(result) => pure(result)
      case Left(error) => raiseError(error)
    }

  /** Converts a `Right` into a successful task and maps a `Left` to an error. */
  def fromEither[E, A](toThrowable: E => Throwable)(value: Either[E, A]): Task[A] =
    value match {
      case Right(result) => pure(result)
      case Left(error) => raiseError(toThrowable(error))
    }

  /** Converts a defined option into a successful task, or raises the supplied error for `None`. */
  def fromOption[A](value: Option[A])(ifEmpty: => Throwable): Task[A] =
    value match {
      case Some(result) => pure(result)
      case None => raiseError(ifEmpty)
    }

  /** Defers task creation and provides the scheduler used to run the task.
    *
    * The function is called when the resulting task is run.
    */
  def deferAction[A](f: Scheduler => Task[A]): Task[A] =
    cont0[A, A]((scheduler: Scheduler, _: Callback[Throwable, A], _: Task[A]) => f(scheduler))

  /** Lifts a non-strict Scala `Future` into a task. */
  def deferFuture[A](future: => Future[A]): Task[A] =
    defer(fromFuture(future))

  /** Creates a future when the task is run, using the current scheduler. */
  def deferFutureAction[A](f: Scheduler => Future[A]): Task[A] =
    deferAction(scheduler => fromFuture(f(scheduler)))

  /** Converts a Scala `Future` into a task.
    *
    * Canceling the task stops waiting for a plain `Future`. If the future is a
    * [[monix.execution.CancelableFuture]], its cancellation token is also run.
    */
  def fromFuture[A](future: Future[A]): Task[A] =
    defer {
      future.value match {
        case Some(result) => fromTry(result)
        case None =>
          val cancel = future match {
            case cf: CancelableFuture[_] => eval(cf.cancelable.cancel())
            case _ => unit
          }
          catsEffectAsyncForTask.fromFutureCancelable(pure((future, cancel)))
      }
    }

  /** Applies `f` repeatedly until it returns a `Right` value. */
  def tailRecM[A, B](initial: A)(f: A => Task[Either[A, B]]): Task[B] =
    catsEffectAsyncForTask.tailRecM(initial)(f)

  /** Creates a task from an effectful callback registration.
    *
    * The registration task runs before the callback wait begins. Cancellation requested
    * during registration takes effect after registration returns.
    */
  def asyncF[A](register: Callback[Throwable, A] => Task[Unit]): Task[A] =
    catsEffectAsyncForTask.async { callback =>
      defer(register(Callback.fromAttempt[Throwable, A](callback))).as(Some(unit))
    }

  /** Creates a task from a callback registration that returns a cancellation action.
    *
    * The cancellation action runs if the task is canceled before the callback signals a result.
    */
  def cancelable[A](register: Callback[Throwable, A] => CancelToken[Task]): Task[A] =
    catsEffectAsyncForTask.async { callback =>
      eval(register(Callback.fromAttempt[Throwable, A](callback))).map(token => Some(token))
    }

  /** Creates a cancelable task and provides the scheduler to its callback registration. */
  def cancelable0[A](register: (Scheduler, Callback[Throwable, A]) => CancelToken[Task]): Task[A] =
    deferAction(scheduler => cancelable(callback => register(scheduler, callback)))

  /** Creates a reference initialized with the given value. */
  def ref[A](initial: A): Task[Ref[Task, A]] =
    catsEffectAsyncForTask.ref(initial)

  /** Creates an empty deferred value that can be completed once and awaited by other tasks. */
  def deferred[A]: Task[Deferred[Task, A]] =
    catsEffectAsyncForTask.deferred[A]

  /** Reschedules the current task before continuing. */
  val cede: Task[Unit] =
    catsEffectAsyncForTask.cede

  /** Alias for [[cede]]. */
  def shift: Task[Unit] =
    cede

  /** Waits for the given duration before completing. */
  def sleep(duration: FiniteDuration): Task[Unit] =
    catsEffectAsyncForTask.sleep(duration)

  /** Returns the current monotonic time. */
  def monotonic: Task[FiniteDuration] =
    catsEffectAsyncForTask.monotonic

  /** Returns the current real time. */
  def realTime: Task[FiniteDuration] =
    catsEffectAsyncForTask.realTime

  /** Returns the execution context of the current task. */
  def executionContext: Task[ExecutionContext] =
    catsEffectAsyncForTask.executionContext

  /** Returns the given task if `condition` is true, otherwise `Task.unit`.
    *
    * @see [[unless]] for the inverse
    */
  def when(condition: Boolean)(action: => Task[Unit]): Task[Unit] =
    if (condition) action else unit

  /** Returns the given task if `condition` is false, otherwise `Task.unit`.
    *
    * @see [[when]] for the inverse
    */
  def unless(condition: Boolean)(action: => Task[Unit]): Task[Unit] =
    if (condition) unit else action

  /** Raises the given error when `condition` is true. */
  def raiseWhen(condition: Boolean)(error: => Throwable): Task[Unit] =
    when(condition)(raiseError(error))

  /** Raises the given error when `condition` is false. */
  def raiseUnless(condition: Boolean)(error: => Throwable): Task[Unit] =
    unless(condition)(raiseError(error))

  /** Combines two tasks sequentially and applies `f` to their results. */
  def map2[A1, A2, R](fa1: Task[A1], fa2: Task[A2])(f: (A1, A2) => R): Task[R] =
    fa1.flatMap(a1 => fa2.map(a2 => f(a1, a2)))

  /** Combines three tasks sequentially and applies `f` to their results. */
  def map3[A1, A2, A3, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])(
    f: (A1, A2, A3) => R
  ): Task[R] =
    fa1.flatMap(a1 => fa2.flatMap(a2 => fa3.map(a3 => f(a1, a2, a3))))

  /** Combines four tasks sequentially and applies `f` to their results. */
  def map4[A1, A2, A3, A4, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])(
    f: (A1, A2, A3, A4) => R
  ): Task[R] =
    fa1.flatMap(a1 => fa2.flatMap(a2 => fa3.flatMap(a3 => fa4.map(a4 => f(a1, a2, a3, a4)))))

  /** Combines five tasks sequentially and applies `f` to their results. */
  def map5[A1, A2, A3, A4, A5, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])(
    f: (A1, A2, A3, A4, A5) => R
  ): Task[R] =
    fa1.flatMap(a1 =>
      fa2.flatMap(a2 => fa3.flatMap(a3 => fa4.flatMap(a4 => fa5.map(a5 => f(a1, a2, a3, a4, a5)))))
    )

  /** Combines six tasks sequentially and applies `f` to their results. */
  def map6[A1, A2, A3, A4, A5, A6, R](
    fa1: Task[A1],
    fa2: Task[A2],
    fa3: Task[A3],
    fa4: Task[A4],
    fa5: Task[A5],
    fa6: Task[A6]
  )(f: (A1, A2, A3, A4, A5, A6) => R): Task[R] =
    fa1.flatMap(a1 =>
      fa2.flatMap(a2 =>
        fa3.flatMap(a3 =>
          fa4.flatMap(a4 => fa5.flatMap(a5 => fa6.map(a6 => f(a1, a2, a3, a4, a5, a6))))
        )
      )
    )

  /** Combines two tasks in parallel and applies `f` to their results. */
  def mapBoth[A1, A2, R](fa1: Task[A1], fa2: Task[A2])(f: (A1, A2) => R): Task[R] =
    parMap2(fa1, fa2)(f)

  /** Combines two tasks in parallel and applies `f` to their results. */
  def parMap2[A1, A2, R](fa1: Task[A1], fa2: Task[A2])(f: (A1, A2) => R): Task[R] =
    catsEffectAsyncForTask.both(fa1, fa2).map { case (a1, a2) => f(a1, a2) }

  /** Combines three tasks in parallel and applies `f` to their results. */
  def parMap3[A1, A2, A3, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])(
    f: (A1, A2, A3) => R
  ): Task[R] =
    parMap2(parMap2(fa1, fa2)((_, _)), fa3) { case ((a1, a2), a3) => f(a1, a2, a3) }

  /** Combines four tasks in parallel and applies `f` to their results. */
  def parMap4[A1, A2, A3, A4, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])(
    f: (A1, A2, A3, A4) => R
  ): Task[R] =
    parMap2(parMap3(fa1, fa2, fa3)((_, _, _)), fa4) { case ((a1, a2, a3), a4) => f(a1, a2, a3, a4) }

  /** Combines five tasks in parallel and applies `f` to their results. */
  def parMap5[A1, A2, A3, A4, A5, R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])(
    f: (A1, A2, A3, A4, A5) => R
  ): Task[R] =
    parMap2(parMap4(fa1, fa2, fa3, fa4)((_, _, _, _)), fa5) {
      case ((a1, a2, a3, a4), a5) => f(a1, a2, a3, a4, a5)
    }

  /** Combines six tasks in parallel and applies `f` to their results. */
  def parMap6[A1, A2, A3, A4, A5, A6, R](
    fa1: Task[A1],
    fa2: Task[A2],
    fa3: Task[A3],
    fa4: Task[A4],
    fa5: Task[A5],
    fa6: Task[A6]
  )(f: (A1, A2, A3, A4, A5, A6) => R): Task[R] =
    parMap2(parMap5(fa1, fa2, fa3, fa4, fa5)((_, _, _, _, _)), fa6) {
      case ((a1, a2, a3, a4, a5), a6) => f(a1, a2, a3, a4, a5, a6)
    }

  /** Returns the result of the first task to complete and cancels the other task. */
  def race[A, B](fa: Task[A], fb: Task[B]): Task[Either[A, B]] =
    catsEffectAsyncForTask.race(fa, fb)

  /** Given a collection of tasks, runs them one by one and gathers the results in the same collection.
    *
    * This operation runs tasks in order. See [[parSequence]] for the concurrent version.
    *
    * Example:
    * {{{
    *   Task.sequence(Vector(Task.now(1), Task.now(2)))
    * }}}
    */
  def sequence[A, M[X] <: Iterable[X]](in: M[Task[A]])(implicit bf: BuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    traverse(in)(identity)

  /** Applies a function to each element, runs the resulting tasks in order, and gathers their results.
    *
    * See [[parTraverse]] for the concurrent version.
    */
  def traverse[A, B, M[X] <: Iterable[X]](in: M[A])(f: A => Task[B])(
    implicit bf: BuildFrom[M[A], B, M[B]]
  ): Task[M[B]] =
    defer {
      val iterator = in.iterator
      val builder = bf.newBuilder(in)

      def loop: Task[M[B]] =
        if (iterator.hasNext)
          defer(f(iterator.next())).flatMap { value =>
            val _ = builder += value
            defer(loop)
          }
        else pure(builder.result())

      loop
    }

  private def rebuild[From, A, C](from: From, bf: BuildFrom[From, A, C], values: Iterable[A]): C = {
    val builder = bf.newBuilder(from)
    val _ = builder ++= values
    builder.result()
  }

  /** Given a collection of tasks, runs them in parallel and gathers the results in the same collection. */
  def parSequence[A, M[X] <: Iterable[X]](in: M[Task[A]])(
    implicit bf: BuildFrom[M[Task[A]], A, M[A]]
  ): Task[M[A]] =
    parTraverse(in)(identity)

  /** Applies a function to each element and runs the resulting tasks in parallel.
    *
    * The results are returned in the same order as the input elements. Use [[parTraverseN]] to limit parallelism.
    */
  def parTraverse[A, B, M[X] <: Iterable[X]](in: M[A])(f: A => Task[B])(
    implicit bf: BuildFrom[M[A], B, M[B]]
  ): Task[M[B]] =
    defer {
      catsEffectAsyncForTask
        .parTraverseN(Int.MaxValue)(in.toList)(value => defer(f(value)))
        .map(values => rebuild(in, bf, values))
    }

  /** Runs the given tasks in parallel with at most `parallelism` tasks running at once.
    *
    * Results are returned in input order. See [[parSequence]] for an unbounded version.
    */
  def parSequenceN[A](parallelism: Int)(in: Iterable[Task[A]]): Task[List[A]] =
    parTraverseN(parallelism)(in)(identity)

  /** Applies a function to each element and runs the resulting tasks with bounded parallelism.
    *
    * At most `parallelism` tasks run at once, and results are returned in input order.
    *
    * Example:
    * {{{
    *   import scala.concurrent.duration._
    *   Task.parTraverseN(2)(List(1, 2, 3))(n => Task.sleep(n.seconds).as(n))
    * }}}
    *
    * See [[parTraverse]] for an unbounded version.
    */
  def parTraverseN[A, B](parallelism: Int)(in: Iterable[A])(f: A => Task[B]): Task[List[B]] =
    defer(catsEffectAsyncForTask.parTraverseN(parallelism)(in.toList)(value => defer(f(value))))

  private def protectRegistration[A](
    scheduler: Scheduler,
    callback: Callback[Throwable, A]
  )(register: Callback[Throwable, A] => Unit): Unit = {
    // A registration exception is an effect error only while no result has won.
    // Once the callback has completed, the exception is late and can only be
    // reported to the scheduler.
    val safe = Callback.safe(callback)(scheduler)
    try register(safe)
    catch {
      case NonFatal(error) =>
        if (!safe.tryOnError(error))
          scheduler.reportFailure(error)
    }
  }

  private[eval] final case class Pure[+A](a: A) extends Task[A] {
    def tag: Byte = Pure.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Pure {
    final val TAG: Byte = 0
  }

  private[eval] final case class RaiseError(e: Throwable) extends Task[Nothing] {
    def tag: Byte = RaiseError.TAG
    def accept[AA >: Nothing, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object RaiseError {
    final val TAG: Byte = 1
  }

  private[eval] final case class FlatMap[A, +B](source: Task[A], f: A => Task[B]) extends Task[B] {
    def tag: Byte = FlatMap.TAG
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object FlatMap {
    final val TAG: Byte = 2
  }

  private[eval] final case class HandleErrorWith[A, +B](source: Task[A], f: Throwable => Task[B])
    extends Task[B] {
    def tag: Byte = HandleErrorWith.TAG

    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object HandleErrorWith {
    final val TAG: Byte = 3
  }

  private[eval] final case class OnCancel[+A](source: Task[A], onCancel: Task[Unit]) extends Task[A] {
    def tag: Byte = OnCancel.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object OnCancel {
    final val TAG: Byte = 4
  }

  /** An asynchronous registration performed under fiber ownership.
    *
    * Each suspension has its own [[internal.TaskRestartCallback]].
    * The fiber schedules inactive resumptions and yields at batch boundaries, so
    * registration and callback publication need no additional boundary.
    */
  private[eval] final case class AsyncSimple[+A](
    register: (Scheduler, Callback[Throwable, A]) => Unit
  ) extends Task[A] {
    def tag: Byte = AsyncSimple.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object AsyncSimple {
    final val TAG: Byte = 5
  }

  /** Primitive continuation handshake used to implement Cats Effect `Async.cont`.
    *
    * The callback and `get` effect may be observed in either order. The fiber joins
    * them through [[internal.TaskCallbackIndirection]], retaining an early result until
    * `get` is evaluated.
    */
  private[eval] final case class AsyncCont[A, +B](
    cont: (Scheduler, Callback[Throwable, A], Task[A]) => Task[B]
  ) extends Task[B] {
    def tag: Byte = AsyncCont.TAG
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object AsyncCont {
    final val TAG: Byte = 6
  }

  private[eval] case object Cancelled extends Task[Nothing] {
    final val TAG: Byte = 7
    def tag: Byte = TAG
    def accept[AA >: Nothing, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class Uncancelable[+A](body: Poll[Task] => Task[A]) extends Task[A] {
    def tag: Byte = Uncancelable.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Uncancelable {
    final val TAG: Byte = 8
  }

  /** A request to lower one cancellation mask level.
    *
    * The `owner` must be the interpreting fiber, and `id` must match its current
    * mask depth.
    * This prevents a token from lowering another fiber or a different current level;
    * as required by `Poll`, callers must keep the token within its creating body.
    */
  private[eval] final case class PollRegion[+A](source: Task[A], id: Int, owner: AnyRef) extends Task[A] {
    def tag: Byte = PollRegion.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object PollRegion {
    final val TAG: Byte = 9
  }

  private[eval] final case class Start[A](source: Task[A]) extends Task[Fiber[Task, Throwable, A]] {
    def tag: Byte = Start.TAG
    def accept[AA >: Fiber[Task, Throwable, A], R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Start {
    final val TAG: Byte = 10
  }

  private[eval] final case class EvalOn[+A](source: Task[A], ec: ExecutionContext) extends Task[A] {
    def tag: Byte = 11
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  /** Restores a context without opening another scoped `evalOn` region. */
  private[eval] final case class ResumeOn[+A](
    source: Task[A],
    scheduler: Scheduler,
    executionContext: ExecutionContext
  ) extends Task[A] {
    def tag: Byte = 12
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class Delay[+A](thunk: () => A) extends Task[A] {
    def tag: Byte = 13
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class Defer[+A](thunk: () => Task[A]) extends Task[A] {
    def tag: Byte = 14
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] case object ReadExecutionContext extends Task[ExecutionContext] {
    def tag: Byte = 15
    def accept[AA >: ExecutionContext, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  /** Visitor used by the runtime. Tags let the hot run-loop dispatch selected nodes
    * directly while retaining typed handlers for the remaining nodes.
    */
  private[eval] trait Visitor[A, +R] {
    def visit(ref: Pure[A]): R
    def visit(ref: RaiseError): R
    def visit[S](ref: FlatMap[S, A]): R
    def visit[S](ref: HandleErrorWith[S, A]): R
    def visit(ref: OnCancel[A]): R
    def visit[S](ref: AsyncCont[S, A]): R
    def visit(ref: AsyncSimple[A]): R
    def visit(ref: Cancelled.type): R
    def visit(ref: Uncancelable[A]): R
    def visit(ref: PollRegion[A]): R
    def visit[S](ref: Start[S]): R
    def visit(ref: EvalOn[A]): R
    def visit(ref: ResumeOn[A]): R
    def visit(ref: Delay[A]): R
    def visit(ref: Defer[A]): R
    def visit(ref: ReadExecutionContext.type): R
  }
}
