/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: https://monifu.org
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

package monifu.concurrent

import scala.language.higherKinds
import monifu.concurrent.Task.Callback
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.internal.concurrent.TaskRunnable
import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/** `Task` represents a specification for an asynchronous computation,
  * which when executed will produce an `A` as a result, along with
  * possible side-effects.
  *
  * Compared with `Future` from Scala's standard library, `Task` does
  * not represent a running computation, as `Task` does not execute
  * anything when working with its builders or operators and it does not
  * submit any work into any thread-pool, the execution eventually
  * taking place after `runAsync` is called and not before that.
  *
  * Note that `Task` is conservative in how it spawns logical threads.
  * Transformations like `map` and `flatMap` for example will default
  * to being executed on the logical thread on which the asynchronous
  * computation was started. But one shouldn't make assumptions about
  * how things will end up executed, as ultimately it is the
  * implementation's job to decide on the best execution model. All
  * you are guaranteed is asynchronous execution after executing `runAsync`.
  */
sealed abstract class Task[+T] { self =>
  /** Characteristic function for our [[Task]]. Never use this directly.
    *
    * @param scheduler is the [[Scheduler]] under that the `Task` will use to
    *                  fork threads, schedule with delay and to report errors
    * @param stackDepth represents the current stack depth, taking into account
    *                   this call as well
    * @param callback is the pair of `onSuccess` and `onError` methods that will
    *                 be called when the execution completes
    */
  def unsafeRunFn(scheduler: Scheduler, stackDepth: Int, callback: Callback[T]): Unit

  /** Internal utility providing a stack-safe `unsafeExecuteFn`, to be used
    * when constructing operators and builders.
    *
    * @param scheduler is the [[Scheduler]] under that the `Task` will use to
    *                  fork threads, schedule with delay and to report errors
    * @param stackDepth represents the current stack depth, taking into account
    *                   this call as well
    * @param callback is the pair of `onSuccess` and `onError` methods that will
    *                 be called when the execution completes
    */
  private[monifu] def stackSafeRun(scheduler: Scheduler, stackDepth: Int, callback: Callback[T]): Unit = {
    if (stackDepth > 0 && stackDepth < Scheduler.recommendedBatchSize)
      try unsafeRunFn(scheduler, stackDepth+1, callback) catch {
        case NonFatal(ex) =>
          callback.safeOnError(scheduler, stackDepth, ex)
      }
    else
      scheduler.scheduleOnce(TaskRunnable.AsyncUnsafeRun(self, scheduler, callback))
  }

  /** Triggers the asynchronous execution.
    *
    * To execute this, you need an implicit [[Scheduler]] in scope, so you
    * can import the default one:
    *
    * {{{
    *   import monifu.concurrent.Implicits.globalScheduler
    * }}}
    *
    * @return a [[Future]] that can be used to extract the result
    */
  def runAsync(implicit s: Scheduler): Future[T] = {
    val p = Promise[T]()
    // will trigger async execution, on another thread
    self.stackSafeRun(s, stackDepth = -1, new Callback[T] {
      def onError(ex: Throwable, stackDepth: Int): Unit =
        p.tryFailure(ex)

      def onSuccess(value: T, stackDepth: Int): Unit =
        p.trySuccess(value)
    })
    p.future
  }

  /** Triggers the asynchronous execution.
    *
    * To execute this, you need an implicit [[Scheduler]] in scope, so you
    * can import the default one:
    *
    * {{{
    *   import monifu.concurrent.Implicits.globalScheduler
    * }}}
    *
    * @param f is a function that will be called with the result on complete
    */
  def runAsync(f: Try[T] => Unit)(implicit s: Scheduler): Unit =
    stackSafeRun(s, -1, new Callback[T] {
      def onSuccess(value: T, stackDepth: Int) = f(Success(value))
      def onError(ex: Throwable, stackDepth: Int) = f(Failure(ex))
    })

  /** Returns a new Task that applies the mapping function to
    * the element emitted by the source.
    */
  def map[U](f: T => U): Task[U] =
    Task.unsafeCreate[U] { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnError(s, depth, ex)

        def onSuccess(value: T, depth: Int): Unit =
          try {
            val u = f(value)
            cb.safeOnSuccess(s, depth, u)
          } catch {
            case NonFatal(ex) =>
              cb.safeOnError(s, depth, ex)
          }
      })
    }

  /** Given a source Task that emits another Task, this function flattens the result,
    * returning a Task equivalent to the emitted Task by the source.
    */
  def flatten[U](implicit ev: T <:< Task[U]): Task[U] =
    Task.unsafeCreate { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnError(s, depth, ex)

        def onSuccess(value: T, depth: Int): Unit =
          value.stackSafeRun(s, depth, cb)
      })
    }

  /** Creates a new Task by applying a function to the successful
    * result of the source Task, and returns a task equivalent to
    * the result of the function.
    */
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task.unsafeCreate { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnError(s, depth, ex)

        def onSuccess(value: T, depth: Int): Unit =
          try {
            val taskU = f(value)
            taskU.stackSafeRun(s, depth, cb)
          }
          catch {
            case NonFatal(ex) =>
              cb.safeOnError(s, depth, ex)
          }
      })
    }

  /** Returns a task that waits for the specified `timespan` before
    * executing and mirroring the result of the source.
    */
  def delay(timespan: FiniteDuration): Task[T] =
    Task.unsafeCreate[T] { (s, depth, callback) =>
      // delaying execution
      s.scheduleOnce(timespan.length, timespan.unit,
        new Runnable {
          override def run(): Unit = {
            self.stackSafeRun(s, depth, callback)
          }
        })
    }

  /** Returns a failed projection of this task.
    *
    * The failed projection is a future holding a value of type `Throwable`,
    * emitting a value which is the throwable of the original task in
    * case the original task fails, otherwise if the source succeeds, then
    * it fails with a `NoSuchElementException`.
    */
  def failed: Task[Throwable] =
    Task.unsafeCreate { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnSuccess(s, depth, ex)
        def onSuccess(value: T, depth: Int): Unit =
          cb.safeOnError(s, depth, new NoSuchElementException("Task.failed"))
      })
    }

  /** Creates a new task that will handle any matching throwable
    * that this task might emit.
    */
  def onErrorRecover[U >: T](pf: PartialFunction[Throwable, U]): Task[U] =
    Task.unsafeCreate { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onSuccess(v: T, depth: Int): Unit =
          cb.safeOnSuccess(s, depth, v)

        def onError(ex: Throwable, depth: Int) =
          try {
            if (pf.isDefinedAt(ex))
              cb.safeOnSuccess(s, depth, pf(ex))
            else
              cb.safeOnError(s, depth, ex)
          } catch {
            case NonFatal(err) =>
              s.reportFailure(ex)
              cb.safeOnError(s, depth, err)
          }
      })
    }

  /** Creates a new task that will handle any matching throwable that this
    * task might emit by executing another task.
    */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Task[U]]): Task[U] =
    Task.unsafeCreate { (s, depth, cb) =>
      self.stackSafeRun(s, depth, new Callback[T] {
        def onSuccess(v: T, depth: Int) =
          cb.safeOnSuccess(s, depth, v)

        def onError(ex: Throwable, depth: Int): Unit =
          try {
            if (pf.isDefinedAt(ex)) {
              val newTask = pf(ex)
              newTask.stackSafeRun(s, depth, cb)
            }
            else
              cb.safeOnError(s, depth, ex)
          } catch {
            case NonFatal(err) =>
              s.reportFailure(ex)
              cb.safeOnError(s, depth, err)
          }
      })
    }

  /** Returns a Task that mirrors the source Task but that triggers a
    * `TimeoutException` in case the given duration passes without the
    * task emitting any item.
    */
  def timeout(after: FiniteDuration): Task[T] =
    Task.unsafeCreate { (s, depth, cb) =>
      val timeoutTask = SingleAssignmentCancelable()

      timeoutTask := s.scheduleOnce(after.length, after.unit,
        new Runnable {
          def run(): Unit =
            if (timeoutTask.cancel()) {
              val ex = new TimeoutException(s"Task timed-out after $after of inactivity")
              cb.safeOnError(s, depth, ex)
            }
        })

      self.stackSafeRun(s, depth, new Callback[T] {
        def onSuccess(v: T, depth: Int): Unit =
          if (timeoutTask.cancel()) cb.safeOnSuccess(s, depth, v)
        def onError(ex: Throwable, depth: Int): Unit =
          if (timeoutTask.cancel()) cb.safeOnError(s, depth, ex)
      })
    }

  /** Returns a Task that mirrors the source Task but switches to
    * the given backup Task in case the given duration passes without the
    * source emitting any item.
    */
  def timeout[U >: T](after: FiniteDuration, backup: Task[U]): Task[U] =
    Task.unsafeCreate { (s, depth, cb) =>
      val timeoutTask = SingleAssignmentCancelable()

      self.stackSafeRun(s, depth, new Callback[T] {
        def onSuccess(v: T, depth: Int): Unit =
          if (timeoutTask.cancel()) cb.safeOnSuccess(s, depth, v)
        def onError(ex: Throwable, depth: Int): Unit =
          if (timeoutTask.cancel()) cb.safeOnError(s, depth, ex)
      })

      timeoutTask := s.scheduleOnce(after.length, after.unit,
        new Runnable {
          def run(): Unit = {
            if (timeoutTask.cancel())
              backup.stackSafeRun(s, depth, cb)
          }
        })
    }

  /** Zips the values of `this` and `that` task, and creates a new task that
    * will emit the tuple of their results.
    */
  def zip[U](that: Task[U]): Task[(T, U)] =
    Task.unsafeCreate { (s, depth, cb) =>
      val state = Atomic(null : Either[T, U])

      self.stackSafeRun(s, depth, new Callback[T] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnError(s, depth, ex)

        @tailrec def onSuccess(t: T, depth: Int): Unit =
          state.get match {
            case null =>
              if (!state.compareAndSet(null, Left(t))) onSuccess(t, depth)
            case Right(u) =>
              cb.safeOnSuccess(s, depth, (t, u))
            case Left(_) =>
              ()
          }
      })

      that.stackSafeRun(s, depth, new Callback[U] {
        def onError(ex: Throwable, depth: Int): Unit =
          cb.safeOnError(s, depth, ex)

        @tailrec def onSuccess(u: U, depth: Int): Unit =
          state.get match {
            case null =>
              if (!state.compareAndSet(null, Right(u))) onSuccess(u, depth)
            case Left(t) =>
              cb.safeOnSuccess(s, depth, (t, u))
            case Right(_) =>
              ()
          }
      })
    }
}

object Task {
  /** Returns a new task that, when executed, will emit the
    * result of the given function executed asynchronously.
    */
  def apply[T](f: => T): Task[T] =
    Task.unsafeCreate { (scheduler, depth, callback) =>
      callback.safeOnSuccess(scheduler, depth, f)
    }

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[T](elem: T): Task[T] =
    new Now(elem)

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def error(ex: Throwable): Task[Nothing] =
    new Fail(ex)

  /** Create a `Task` from an asynchronous computation, which takes the form
    * of a function with which we can register a callback. This can be used
    * to translate from a callback-based API to a straightforward monadic
    * version.
    *
    * @param register is a function that will be called when this `Task` is
    *                 executed, receiving a callback as a parameter,
    *                 a callback that the user is supposed to call in order
    *                 to signal the desired outcome of this `Task`.
   */
  def async[T](register: (Try[T] => Unit) => Unit): Task[T] =
    Task.unsafeCreate { (s, depth, cb) =>
      try register {
        case Success(value) => cb.safeOnSuccess(s, depth, value)
        case Failure(ex) => cb.safeOnError(s, depth, ex)
      } catch {
        case NonFatal(ex) =>
          cb.safeOnError(s, depth, ex)
      }
    }

  /** Converts the given Scala `Future` into a `Task` */
  def fromFuture[T](f: => Future[T]): Task[T] =
    Task.unsafeCreate { (s, depth, callback) =>
      f.onComplete {
        case Success(value) =>
          callback.safeOnSuccess(s, depth, value)
        case Failure(ex) =>
          callback.safeOnError(s, depth, ex)
      }(s)
    }

  /** Builder for [[Task]] instances. For usage on implementing
    * operators or builders. Only use if you know what you're doing.
    */
  def unsafeCreate[T](f: (Scheduler, Int, Callback[T]) => Unit): Task[T] =
    new Task[T] {
      def unsafeRunFn(s: Scheduler, sd: Int, cb: Callback[T]): Unit =
        f(s, sd, cb)
    }

  /** Represents a callback that should be called asynchronously,
    * having the execution managed by the given `scheduler`.
    * Used by [[Task]] to signal the completion of asynchronous
    * computations.
    *
    * The `scheduler` represents our execution context under which
    * the asynchronous computation (leading to either `onSuccess` or `onError`)
    * should run.
    *
    * The `onSuccess` method is called only once, with the successful
    * result of our asynchronous computation, whereas `onError` is called
    * if the result is an error.
    */
  abstract class Callback[-T] { self =>
    /** To be called only once, on successful completion of a [[Task]].
      *
      * @param value is the successful result to signal
      * @param stackDepth is the current stack depth (this call included)
      */
    def onSuccess(value: T, stackDepth: Int): Unit

    /** To be called only once, on failure of a [[Task]].
      *
      * @param ex is the failure
      * @param stackDepth is the current stack depth (this call included)
      */
    def onError(ex: Throwable, stackDepth: Int): Unit

    /** Wraps [[onSuccess]] in a safe function that, in case the stack depth
      * has exceeded the allowed maximum, forks a new logical thread
      * on the given scheduler.
      *
      * @param s is the scheduler on top of which we'll fork, if needed
      * @param value is the successful result to signal
      * @param stackDepth is the current stack depth (this call included)
      */
    def safeOnSuccess(s: Scheduler, stackDepth: Int, value: T): Unit = {
      if (stackDepth < Scheduler.recommendedBatchSize)
        try onSuccess(value, stackDepth+1) catch {
          case NonFatal(ex) =>
            safeOnError(s, stackDepth, ex)
        }
      else
        s.execute(TaskRunnable.AsyncOnSuccess(self, value))
    }

    /** Wraps [[onError]] in a safe function that, in case the stack depth
      * has exceeded the allowed maximum, forks a new logical thread
      * on the given scheduler.
      *
      * @param s is the scheduler on top of which we'll fork, if needed
      * @param ex is the failure
      * @param stackDepth is the current stack depth (this call included)
      */
    def safeOnError(s: Scheduler, stackDepth: Int, ex: Throwable): Unit = {
      if (stackDepth < Scheduler.recommendedBatchSize)
        onError(ex, stackDepth+1)
      else
        s.execute(TaskRunnable.AsyncOnError(self, ex))
    }
  }

  /** Optimized task for already known strict values.
    * Internal to Monifu, not for public consumption.
    *
    * See [[Task.now]] instead.
    */
  private final class Now[+T](value: T) extends Task[T] {
    def unsafeRunFn(s: Scheduler, sd: Int, cb: Callback[T]): Unit =
      try cb.safeOnSuccess(s, sd, value) catch {
        case NonFatal(ex) =>
          cb.safeOnError(s, sd, ex)
      }

    override def runAsync(implicit s: Scheduler): Future[T] =
      Future.successful(value)
  }

  /** Optimized task for failed outcomes.
    * Internal to Monifu, not for public consumption.
    *
    * See [[Task.error]] instead.
    */
  private final class Fail(ex: Throwable) extends Task[Nothing] {
    def unsafeRunFn(s: Scheduler, sd: Int, cb: Callback[Nothing]): Unit = {
      cb.safeOnError(s, sd, ex)
    }

    override def runAsync(implicit s: Scheduler): Future[Nothing] =
      Future.failed(ex)
  }
}
