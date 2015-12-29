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

import monifu.concurrent.Task.Callback
import monifu.concurrent.cancelables._
import monifu.concurrent.atomic.padded.Atomic
import monifu.internal.TaskCollapsibleCancelable
import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/** `Task` represents a specification for an asynchronous computation,
  * which when executed will produce an `A` as a result, along with
  * possible side-effects.
  *
  * Compared with `Future` from Scala's standard library, `Task` does
  * not represent a running computation, as `Task` does not execute
  * anything when working with its builders or operators, it does not
  * submit any work into any thread-pool, the execution eventually
  * taking place after `runAsync` is called and not before that.
  *
  * Also compared with Scala's `Future`, `Task` is conservative in how
  * it spawns logical threads. Transformations like `map` and
  * `flatMap` for example will default to being executed on a local
  * trampoline instead. But you are not guaranteed a mechanism for
  * execution, the implementation ultimately deciding whether to
  * execute on the local trampoline, or to spawn a thread. All you are
  * guaranteed is that execution will be asynchronous as to not blow
  * up the stack.  You can force the spawning of a thread by using
  * [[Task.fork]].
  */
sealed abstract class Task[+T] { self =>
  /** Characteristic function for our [[Task]].
    *
    * @param scheduler is the [[Scheduler]] under that the `Task` will use to
    *                  fork threads, schedule with delay and to report errors
    * @param callback is the pair of `onSuccess` and `onError` methods that will
    *                 be called when the execution completes
    *
    * @return a [[Cancelable]] that can be used to cancel the in progress async computation
    */
  private[monifu] def unsafeRunFn(scheduler: Scheduler, callback: Callback[T]): Cancelable

  /** Triggers the asynchronous execution.
    *
    * To execute this, you need an implicit [[Scheduler]] in scope, so you
    * can import the default one:
    *
    * {{{
    *   import monifu.concurrent.Implicits.globalScheduler
    * }}}
    *
    * NOTE: even though `Task` is describing an asynchronous computation,
    * the execution might still be trampolined and thus it can happen on
    * the current thread. If that's not desirable, then include an explicit
    * fork, like so:
    *
    * {{{
    *   Task.fork(task).runAsync
    * }}}
    *
    * @return a [[CancelableFuture]] that can be used to extract the result or
    *         to cancel the in progress async computation
    */
  def runAsync(implicit s: Scheduler): CancelableFuture[T] = {
    val p = Promise[T]()
    val task = self.unsafeRunFn(s, new Callback[T] {
      def onError(ex: Throwable): Unit =
        p.tryFailure(ex)

      def onSuccess(value: T): Unit =
        p.trySuccess(value)
    })

    val cancelable = Cancelable {
      p.tryFailure(new scala.concurrent.CancellationException)
      task.cancel()
    }

    CancelableFuture(p.future, cancelable)
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
    * NOTE: even though `Task` is describing an asynchronous computation,
    * the execution might still be trampolined and thus it can happen on
    * the current thread. If that's not desirable, then include an explicit
    * fork, like so:
    *
    * {{{
    *   Task.fork(task).runAsync {
    *     case Success(value) => logger.info(value)
    *     case Failure(ex) => logger.error(ex)
    *   }
    * }}}
    *
    * @param f is a function that will be called with the result on complete
    *
    * @return a [[Cancelable]] that can be used to cancel the in progress async computation
    */
  def runAsync(f: Try[T] => Unit)(implicit s: Scheduler): Cancelable =
    unsafeRunFn(s, Callback.safe(new Callback[T] {
      def onSuccess(value: T) = f(Success(value))
      def onError(ex: Throwable) = f(Failure(ex))
    }))

  /** Returns a new Task that applies the mapping function to
    * the element emitted by the source.
    */
  def map[U](f: T => U): Task[U] =
    Task.unsafeCreate[U] { (s,cb) =>
      self.unsafeRunFn(s, new Callback[T] {
        def onError(ex: Throwable): Unit =
          cb.asyncOnError(s, ex)

        def onSuccess(value: T): Unit =
          try {
            val u = f(value)
            cb.asyncOnSuccess(s, u)
          } catch {
            case NonFatal(ex) =>
              cb.asyncOnError(s, ex)
          }
      })
    }

  /** Given a source Task that emits another Task, this function flattens the result,
    * returning a Task equivalent to the emitted Task by the source.
    */
  def flatten[U](implicit ev: T <:< Task[U]): Task[U] =
    Task.unsafeCreate { (s,cb) =>
      val cancelable = TaskCollapsibleCancelable()

      cancelable() = self.unsafeRunFn(s, new Callback[T] {
        def onError(ex: Throwable): Unit =
          cb.asyncOnError(s, ex)

        def onSuccess(value: T): Unit = {
          val r = new Runnable { def run() =
            cancelable() = value.unsafeRunFn(s,cb)
          }

          if (!Trampoline.tryExecute(r, s)) s.execute(r)
        }
      })

      cancelable
    }

  /** Creates a new Task by applying a function to the successful
    * result of the source Task, and returns a task equivalent to
    * the result of the function.
    */
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task.unsafeCreate[U] { (s,cb) =>
      val cancelable = TaskCollapsibleCancelable()

      cancelable() = self.unsafeRunFn(s, new Callback[T] {
        def onError(ex: Throwable): Unit =
          cb.asyncOnError(s, ex)

        def onSuccess(value: T): Unit = {
          // protection against user code
          try {
            val taskU = f(value)
            val r = new Runnable { def run() =
              cancelable() = taskU.unsafeRunFn(s,cb) }
            if (!Trampoline.tryExecute(r, s)) s.execute(r)
          } catch {
            case NonFatal(ex) =>
              cb.asyncOnError(s, ex)
          }
        }
      })

      cancelable
    }

  /** Returns a task that waits for the specified `timespan` before
    * executing and mirroring the result of the source.
    */
  def delay(timespan: FiniteDuration): Task[T] =
    Task.unsafeCreate[T] { (s, callback) =>
      val cancelable = TaskCollapsibleCancelable()
      // delaying execution
      cancelable() = s.scheduleOnce(timespan.length, timespan.unit,
        new Runnable {
          override def run(): Unit = {
            cancelable() = self.unsafeRunFn(s, callback)
          }
        })
      cancelable
    }

  /** Returns a failed projection of this task.
    *
    * The failed projection is a future holding a value of type `Throwable`,
    * emitting a value which is the throwable of the original task in
    * case the original task fails, otherwise if the source succeeds, then
    * it fails with a `NoSuchElementException`.
    */
  def failed: Task[Throwable] =
    Task.unsafeCreate { (s,cb) =>
      self.unsafeRunFn(s, new Callback[T] {
        def onError(ex: Throwable): Unit =
          cb.asyncOnSuccess(s, ex)
        def onSuccess(value: T): Unit =
          cb.asyncOnError(s, new NoSuchElementException("Task.failed"))
      })
    }

  /** Creates a new task that will handle any matching throwable
    * that this task might emit.
    */
  def onErrorRecover[U >: T](pf: PartialFunction[Throwable, U]): Task[U] =
    Task.unsafeCreate { (s,cb) =>
      self.unsafeRunFn(s, new Callback[T] {
        def onSuccess(v: T) = cb.asyncOnSuccess(s,v)

        def onError(ex: Throwable) =
          try {
            if (pf.isDefinedAt(ex))
              cb.asyncOnSuccess(s, pf(ex))
            else
              cb.asyncOnError(s, ex)
          } catch {
            case NonFatal(err) =>
              s.reportFailure(ex)
              cb.asyncOnError(s, err)
          }
      })
    }

  /** Creates a new task that will handle any matching throwable that this
    * task might emit by executing another task.
    */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Task[U]]): Task[U] =
    Task.unsafeCreate { (s,cb) =>
      val cancelable = TaskCollapsibleCancelable()

      cancelable() = self.unsafeRunFn(s, new Callback[T] {
        def onSuccess(v: T) = cb.asyncOnSuccess(s,v)

        def onError(ex: Throwable): Unit =
          try {
            if (pf.isDefinedAt(ex)) {
              val newTask = pf(ex)
              cancelable() = newTask.unsafeRunFn(s,cb)
            } else {
              cb.asyncOnError(s,ex)
            }
          } catch {
            case NonFatal(err) =>
              s.reportFailure(ex)
              cb.asyncOnError(s, err)
          }
      })

      cancelable
    }

  /** Returns a Task that mirrors the source Task but that triggers a
    * `TimeoutException` in case the given duration passes without the
    * task emitting any item.
    */
  def timeout(after: FiniteDuration): Task[T] =
    Task.unsafeCreate { (s,cb) =>
      val cancelable =  CompositeCancelable()

      cancelable add s.scheduleOnce(after.length, after.unit,
        new Runnable {
          def run(): Unit = {
            if (cancelable.cancel())
              cb.onError(new TimeoutException(s"Task timed-out after $after of inactivity"))
          }
        })

      cancelable add self.unsafeRunFn(s, new Callback[T] {
        def onSuccess(v: T): Unit =
          if (cancelable.cancel()) cb.asyncOnSuccess(s,v)
        def onError(ex: Throwable): Unit =
          if (cancelable.cancel()) cb.asyncOnError(s,ex)
      })

      cancelable
    }

  /** Returns a Task that mirrors the source Task but switches to
    * the given backup Task in case the given duration passes without the
    * source emitting any item.
    */
  def timeout[U >: T](after: FiniteDuration, backup: Task[U]): Task[U] =
    Task.unsafeCreate { (s,cb) =>
      val composite = CompositeCancelable()
      val cancelable = TaskCollapsibleCancelable(composite)

      composite add self.unsafeRunFn(s, new Callback[T] {
        def onSuccess(v: T): Unit =
          if (composite.cancel()) cb.asyncOnSuccess(s,v)
        def onError(ex: Throwable): Unit =
          if (composite.cancel()) cb.asyncOnError(s,ex)
      })

      composite add s.scheduleOnce(after.length, after.unit,
        new Runnable {
          def run(): Unit = {
            if (composite.cancel())
              cancelable() = backup.unsafeRunFn(s,cb)
          }
        })

      cancelable
    }

  /** Zips the values of `this` and `that` task, and creates a new task that
    * will emit the tuple of their results.
    */
  def zip[U](that: Task[U]): Task[(T, U)] =
    Task.unsafeCreate { (s,cb) =>
      val state = Atomic(null : Either[T, U])
      val composite = CompositeCancelable()

      composite add self.unsafeRunFn(s, new Callback[T] {
        def onError(ex: Throwable) = cb.asyncOnError(s,ex)

        @tailrec def onSuccess(t: T): Unit =
          state.get match {
            case null =>
              if (!state.compareAndSet(null, Left(t))) onSuccess(t)
            case Right(u) =>
              cb.asyncOnSuccess(s, (t, u))
            case Left(_) =>
              ()
          }
      })

      composite add that.unsafeRunFn(s, new Callback[U] {
        def onError(ex: Throwable) = cb.asyncOnError(s,ex)

        @tailrec def onSuccess(u: U): Unit =
          state.get match {
            case null =>
              if (!state.compareAndSet(null, Right(u))) onSuccess(u)
            case Left(t) =>
              cb.asyncOnSuccess(s, (t, u))
            case Right(_) =>
              ()
          }
      })

      composite
    }
}

object Task {
  /** Returns a new task that, when executed, will emit the
    * result of the given function executed asynchronously.
    */
  def apply[T](f: => T): Task[T] =
    Task.unsafeCreate { (scheduler, callback) =>
      scheduler.scheduleOnce(
        new Runnable {
          def run(): Unit = {
            // protecting against user code
            try callback.onSuccess(f) catch {
              case NonFatal(ex) =>
                callback.onError(ex)
            }
          }
        })
    }

  /** Promote a non-strict value to a `Task`, catching exceptions in
    * the process.
    *
    * Note that in comparison with [[Task.apply]], the given function
    * will execute on the local trampoline instead of spawning a
    * thread-pool.
    */
  def delay[T](f: => T): Task[T] =
    Task.unsafeCreate { (scheduler, callback) =>
      val r = new Runnable {
        def run(): Unit =
          try callback.onSuccess(f) catch {
            case NonFatal(ex) =>
              callback.onError(ex)
          }
      }

      if (!Trampoline.tryExecute(r, scheduler))
        scheduler.scheduleOnce(r)
      else
        Cancelable.empty
    }

  /** Returns a `Task` that produces the same result as the given `Task`,
    * but forks its evaluation off into a separate (logical) thread.
    */
  def fork[T](f: => Task[T]): Task[T] =
    Task.unsafeCreate { (scheduler, callback) =>
      scheduler.scheduleOnce(
        new Runnable {
          def run(): Unit =
            f.unsafeRunFn(scheduler, callback)
        })
    }

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[T](elem: T): Task[T] =
    new Now(elem)

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def fail(ex: Throwable): Task[Nothing] =
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
    Task.unsafeCreate { (s,cb) =>
      try register {
        case Success(value) => cb.asyncOnSuccess(s, value)
        case Failure(ex) => cb.asyncOnError(s, ex)
      } catch {
        case NonFatal(ex) =>
          cb.asyncOnError(s, ex)
      }

      Cancelable.empty
    }

  /** Converts the given Scala `Future` into a `Task` */
  def fromFuture[T](f: => Future[T]): Task[T] =
    Task.unsafeCreate { (scheduler, callback) =>
      val cancelable = BooleanCancelable()
      f.onComplete {
        case Success(value) =>
          if (!cancelable.isCanceled)
            try callback.onSuccess(value) catch {
              case NonFatal(ex) =>
                callback.asyncOnError(scheduler, ex)
            }
        case Failure(ex) =>
          if (!cancelable.isCanceled)
            callback.asyncOnError(scheduler, ex)
      }(scheduler)
      cancelable
    }

  /** Builder for [[Task]] instances. For usage on implementing
    * operators or builders. Only use if you know what you're doing.
    */
  def unsafeCreate[T](f: (Scheduler, Callback[T]) => Cancelable): Task[T] =
    new Task[T] {
      override def unsafeRunFn(s: Scheduler, cb: Callback[T]): Cancelable =
        f(s,cb)
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
    def onSuccess(value: T): Unit
    def onError(ex: Throwable): Unit

    def asyncOnSuccess(s: Scheduler, value: T, fork: Boolean = false): Unit = {
      val r = new Runnable {
        def run(): Unit =
          try self.onSuccess(value) catch {
            case NonFatal(ex) =>
              asyncOnError(s, ex, fork)
          }
      }

      if (fork || !Trampoline.tryExecute(r, s))
        s.execute(r)
    }

    def asyncOnError(s: Scheduler, ex: Throwable, fork: Boolean = false): Unit = {
      val r = new Runnable {
        def run(): Unit = try self.onError(ex) catch {
          case NonFatal(err) =>
            s.reportFailure(ex)
            s.reportFailure(err)
        }
      }

      if (fork || !Trampoline.tryExecute(r, s))
        s.execute(r)
    }
  }

  private[monifu] object Callback {
    /** Wraps a callback into an implementation that tries to protect
      * against multiple onSuccess/onError calls and that defers failed
      * onSuccess to onError.
      *
      * Should only be used at the outer edge, in the user-facing runAsync.
      */
    def safe[T](cb: Callback[T]): Callback[T] =
      new Callback[T] {
        // very simple and non thread-safe protection
        // for our grammar
        private[this] var isActive = true

        def onSuccess(value: T): Unit =
          if (isActive) {
            isActive = false
            try cb.onSuccess(value) catch {
              case NonFatal(ex) =>
                cb.onError(ex)
            }
          }

        def onError(ex: Throwable): Unit =
          if (isActive) {
            isActive = false
            cb.onError(ex)
          }
      }
  }

  /** Optimized task for already known strict values.
    * Internal to Monifu, not for public consumption.
    *
    * See [[Task.now]] instead.
    */
  private final class Now[+T](value: T) extends Task[T] {
    def unsafeRunFn(scheduler: Scheduler, callback: Callback[T]): Cancelable = {
      try callback.onSuccess(value) catch {
        case NonFatal(ex) =>
          callback.asyncOnError(scheduler, ex)
      }

      Cancelable.empty
    }

    override def runAsync(implicit s: Scheduler): CancelableFuture[T] =
      CancelableFuture(Future.successful(value), Cancelable.empty)
  }

  /** Optimized task for failed outcomes.
    * Internal to Monifu, not for public consumption.
    *
    * See [[Task.fail]] instead.
    */
  private final class Fail(ex: Throwable) extends Task[Nothing] {
    override def unsafeRunFn(scheduler: Scheduler, callback: Callback[Nothing]): Cancelable = {
      callback.onError(ex)
      Cancelable.empty
    }

    override def runAsync(implicit s: Scheduler): CancelableFuture[Nothing] =
      CancelableFuture(Future.failed(ex), Cancelable.empty)
  }
}
