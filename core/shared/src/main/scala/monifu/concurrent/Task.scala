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
import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
 * For modeling asynchronous computations.
 */
trait Task[+T] { self =>
  /**
   * Characteristic function for our [[Task]].
   */
  protected def unsafeRun(c: Callback[T]): Unit

  /**
   * Triggers the asynchronous execution.
   */
  def runAsync(implicit s: Scheduler): Future[T] = {
    val p = Promise[T]()
    unsafeRun(Callback(
      scheduler = s,
      trampoline = Trampoline(s),
      onSuccess = v => p.trySuccess(v),
      onError = ex => p.tryFailure(ex)
    ))
    p.future
  }

  /**
   * Triggers the asynchronous execution.
   *
   * @param f is a function that will be called with the result on complete
   * @return a [[Cancelable]] that can be used to cancel the in progress async computation
   */
  def runAsync(f: Try[T] => Unit)(implicit s: Scheduler): Unit =
    unsafeRun(Callback(
      scheduler = s,
      trampoline = Trampoline(s),
      onSuccess = v => f(Success(v)),
      onError = ex => f(Failure(ex))
    ))

  /**
   * Returns a new Task that applies the mapping function to
   * the element emitted by the source.
   */
  def map[U](f: T => U): Task[U] =
    Task.unsafeCreate[U] { cb =>
      self.unsafeRun(cb.copy(
        onError = cb.asyncOnError,
        onSuccess = value =>
          try {
            val u = f(value)
            cb.asyncOnSuccess(u)
          } catch {
            case NonFatal(ex) =>
              cb.asyncOnError(ex)
          }
      ))
    }

  /**
   * Given a source Task that emits another Task, this function
   * flattens the result, returning a Task equivalent to the
   * emitted Task by the source.
   */
  def flatten[U](implicit ev: T <:< Task[U]): Task[U] =
    Task.unsafeCreate { cb =>
      self.unsafeRun(cb.copy(
        onError = cb.asyncOnError,
        onSuccess = value => {
          val r = new Runnable { def run() = value.unsafeRun(cb) }
          if (!cb.trampoline.execute(r))
            cb.scheduler.execute(r)
        }
      ))
    }

  /**
   * Creates a new Task by applying a function to the successful
   * result of the source Task, and returns a task equivalent to
   * the result of the function.
   */
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task.unsafeCreate[U] { cb =>
      self.unsafeRun(cb.copy(
        onError = cb.asyncOnError,
        onSuccess = value => {
          // protection against user code
          try {
            val taskU = f(value)
            val r = new Runnable { def run() = taskU.unsafeRun(cb) }
            if (!cb.trampoline.execute(r))
              cb.scheduler.execute(r)
          } catch {
            case NonFatal(ex) =>
              cb.asyncOnError(ex)
          }
        }
      ))
    }

  /**
   * Returns a task that waits for the specified `timespan` before
   * executing and mirroring the result of the source.
   */
  def delay(timespan: FiniteDuration): Task[T] =
    Task.unsafeCreate[T] { callback =>
      // delaying execution
      callback.scheduler.scheduleOnce(timespan,
        new Runnable {
          override def run(): Unit = {
            self.unsafeRun(callback)
          }
        })
    }

  /**
   * Returns a failed projection of this task.
   *
   * The failed projection is a future holding a value of type `Throwable`,
   * emitting a value which is the throwable of the original task in
   * case the original task fails, otherwise if the source succeeds, then
   * it fails with a `NoSuchElementException`.
   */
  def failed: Task[Throwable] =
    Task.unsafeCreate { cb =>
      self.unsafeRun(cb.copy(
        onError = cb.asyncOnSuccess,
        onSuccess = value =>
          cb.asyncOnError(new NoSuchElementException("Task.failed"))
      ))
    }

  /**
   * Creates a new task that will handle any matching throwable
   * that this task might emit.
   */
  def onErrorRecover[U >: T](pf: PartialFunction[Throwable, U]): Task[U] =
    Task.unsafeCreate { cb =>
      self.unsafeRun(cb.copy(
        onSuccess = cb.asyncOnSuccess,
        onError = ex =>
          try {
            if (pf.isDefinedAt(ex))
              cb.asyncOnSuccess(pf(ex))
            else
              cb.asyncOnError(ex)
          } catch {
            case NonFatal(err) =>
              cb.scheduler.reportFailure(ex)
              cb.asyncOnError(err)
          }
      ))
    }

  /**
   * Creates a new task that will handle any matching throwable that this
   * task might emit by executing another task.
   */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Task[U]]): Task[U] =
    Task.unsafeCreate { cb =>
      self.unsafeRun(cb.copy(
        onSuccess = cb.asyncOnSuccess,
        onError = ex =>
          try {
            if (pf.isDefinedAt(ex)) {
              val newTask = pf(ex)
              newTask.unsafeRun(cb)
            } else {
              cb.asyncOnError(ex)
            }
          } catch {
            case NonFatal(err) =>
              cb.scheduler.reportFailure(ex)
              cb.asyncOnError(err)
          }
      ))
    }

  /**
   * Returns a Task that mirrors the source Task but that triggers a
   * `TimeoutException` in case the given duration passes without the
   * task emitting any item.
   */
  def timeout(after: FiniteDuration): Task[T] = {
    Task.unsafeCreate { cb =>
      val timeoutTask = SingleAssignmentCancelable()

      timeoutTask := cb.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (timeoutTask.cancel())
              cb.onError(
                new TimeoutException(s"Task timed-out after $after of inactivity"))
          }
        })

      self.unsafeRun(cb.copy(
        onSuccess = value =>
          if (timeoutTask.cancel()) cb.asyncOnSuccess(value),
        onError = ex =>
          if (timeoutTask.cancel()) cb.asyncOnError(ex)
      ))
    }
  }

  /**
   * Returns a Task that mirrors the source Task but switches to
   * the given backup Task in case the given duration passes without the
   * source emitting any item.
   */
  def timeout[U >: T](after: FiniteDuration, backup: Task[U]): Task[U] =
    Task.unsafeCreate { cb =>
      val timeoutTask = SingleAssignmentCancelable()

      timeoutTask := cb.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (timeoutTask.cancel())
              backup.unsafeRun(cb)
          }
        })

      self.unsafeRun(cb.copy(
        onSuccess = value =>
          if (timeoutTask.cancel()) cb.asyncOnSuccess(value),
        onError = ex =>
          if (timeoutTask.cancel()) cb.asyncOnError(ex)
      ))
    }

  /**
   * Zips the values of `this` and `that` task, and creates a new task that
   * will emit the tuple of their results.
   */
  def zip[U](that: Task[U]): Task[(T, U)] = {
    Task.unsafeCreate { cb =>
      val state = Atomic(null : Either[T, U])

      @tailrec def onSuccessT(t: T): Unit =
        state.get match {
          case null =>
            if (!state.compareAndSet(null, Left(t))) onSuccessT(t)
          case Right(u) =>
            cb.asyncOnSuccess((t, u))
          case Left(_) =>
            ()
        }

      @tailrec def onSuccessU(u: U): Unit =
        state.get match {
          case null =>
            if (!state.compareAndSet(null, Right(u))) onSuccessU(u)
          case Left(t) =>
            cb.asyncOnSuccess((t, u))
          case Right(_) =>
            ()
        }

      self.unsafeRun(cb.copy(onSuccessT, cb.asyncOnError))
      that.unsafeRun(cb.copy(onSuccessU, cb.asyncOnError))
    }
  }

  /**
   * Converts this task into a Scala `Future`, triggering its execution.
   */
  def asFuture(implicit s: Scheduler): Future[T] =
    runAsync(s)
}

object Task {
  /**
   * Returns a new task that, when executed, will emit the
   * result of the given function executed asynchronously.
   */
  def apply[T](f: => T): Task[T] =
    Task.unsafeCreate { callback =>
      callback.scheduler.execute(
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

  /**
   * Builder for [[Task]] instances. Only use if you know what
   * you're doing.
   */
  def unsafeCreate[T](f: Callback[T] => Unit): Task[T] =
    new Task[T] {
      override def unsafeRun(c: Callback[T]): Unit = f(c)
    }

  /**
   * Promote a non-strict value to a `Task`, catching exceptions in
   * the process.
   */
  def delay[T](f: => T): Task[T] =
    Task.unsafeCreate { callback =>
      val r = new Runnable {
        def run(): Unit =
          try callback.onSuccess(f) catch {
            case NonFatal(ex) =>
              callback.onError(ex)
          }
      }

      if (!callback.trampoline.execute(r))
        callback.scheduler.execute(r)
    }

  /**
   * Returns a `Task` that produces the same result as the given `Task`,
   * but forks its evaluation off into a separate (logical) thread.
   */
  def fork[T](f: => Task[T]): Task[T] =
    Task(f).flatten

  /**
   * Returns a task that on execution is always successful,
   * emitting the given element.
   */
  def now[T](elem: T): Task[T] =
    Task.unsafeCreate { callback =>
      try callback.onSuccess(elem) catch {
        case NonFatal(ex) =>
          callback.asyncOnError(ex)
      }
    }

  /**
   * Returns a task that on execution is always finishing
   * in error emitting the specified exception.
   */
  def fail(ex: Throwable): Task[Nothing] =
    Task.unsafeCreate(_.asyncOnError(ex))

  /**
   * Converts the given `Future` into a `Task`.
   */
  def fromFuture[T](f: => Future[T]): Task[T] =
    Task.unsafeCreate { callback =>
      implicit val s = callback.scheduler
      f.onComplete {
        case Success(value) =>
          try callback.onSuccess(value) catch {
            case NonFatal(ex) =>
              callback.asyncOnError(ex)
          }
        case Failure(ex) =>
          callback.asyncOnError(ex)
      }
    }

  /**
   * Simple version of Futures.traverse. Transforms a `TraversableOnce[Task[T]]` into a
   * `Task[TraversableOnce[A]]`. Useful for reducing many Tasks into a single one.
   *
   * NOTE: the futures will be executed in parallel if the underlying scheduler allows it.
   */
  def sequence[T, M[X] <: TraversableOnce[X]](in: M[Task[T]])(implicit cbf: CanBuildFrom[M[Task[T]], T, M[T]]): Task[M[T]] =
    Task.unsafeCreate { cb =>
      implicit val s = cb.scheduler
      val futures = in.map(_.runAsync(cb.scheduler)).toSeq
      val seqF = futures.foldLeft(Future.successful(cbf(in))) { (acc, f) =>
        for (seq <- acc; elem <- f) yield seq += elem
      }

      seqF.onComplete {
        case Failure(ex) =>
          cb.asyncOnError(ex)

        case Success(seq) =>
          try cb.onSuccess(seq.result()) catch {
            case NonFatal(ex) =>
              cb.asyncOnError(ex)
          }
      }
    }

  /**
   * Returns a new Task that will generate the result of the first
   * task in the list that is completed.
   */
  def firstCompletedOf[T](tasks: TraversableOnce[Task[T]]): Task[T] =
    Task.unsafeCreate { cb =>
      implicit val s = cb.scheduler
      val futures = tasks.map(_.runAsync(cb.scheduler))

      Future.firstCompletedOf(futures).onComplete {
        case Success(value) =>
          try cb.onSuccess(value) catch {
            case NonFatal(ex) =>
              cb.asyncOnError(ex)
          }
        case Failure(ex) =>
          cb.asyncOnError(ex)
      }
    }

  /**
   * Represents a callback that should be called asynchronously,
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
  final case class Callback[-T](
    onSuccess: T => Unit,
    onError: Throwable => Unit,
    scheduler: Scheduler,
    trampoline: Trampoline) { self =>

    /** Push a task in the scheduler for triggering onSuccess */
    def asyncOnSuccess(value: T): Unit = {
      val r = new Runnable {
        def run(): Unit =
          try self.onSuccess(value) catch {
            case NonFatal(ex) =>
              asyncOnError(ex)
          }
      }

      if (!trampoline.execute(r))
        scheduler.execute(r)
    }

    /** Push a task in the scheduler for triggering onError */
    def asyncOnError(ex: Throwable): Unit =
      scheduler.execute(new Runnable {
        def run(): Unit = self.onError(ex)
      })
  }
}
