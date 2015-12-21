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

import language.higherKinds
import monifu.concurrent.cancelables._
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.schedulers._
import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
 * For modeling asynchronous computations.
 */
trait Task[+T] { self =>
  /**
   * Characteristic function for our [[Task]].
   *
   * Method is not meant to be used directly.
   * See [[Task.unsafeRun(f* unsafeRun(f)]] as an alternative.
   *
   * NOTE to implementors: `unsafeRun` should always execute asynchronously.
   */
  def unsafeRun(c: TaskCallback[T]): Cancelable

  /**
   * Triggers the asynchronous execution.
   *
   * @return a [[CancelableFuture]] that will complete with the
   *         result of our asynchronous computation.
   */
  def unsafeRun(implicit s: Scheduler): CancelableFuture[T] =
    CancelableFuture(this)

  /**
   * Triggers the asynchronous execution.
   *
   * @param f is a function that will be called with the result on complete
   * @return a [[Cancelable]] that can be used to cancel the in progress async computation
   */
  def unsafeRun(f: Try[T] => Unit)(implicit s: Scheduler): Cancelable =
    unsafeRun(new TaskCallback[T] {
      val scheduler = s
      def onError(ex: Throwable): Unit = f(Failure(ex))
      def onSuccess(value: T): Unit = f(Success(value))
    })

  /**
   * Returns a new Task that applies the mapping function to
   * the element emitted by the source.
   */
  def map[U](f: T => U): Task[U] =
    Task.unsafeCreate[U] { callback =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          callback.onError(ex)

        def onSuccess(value: T): Unit = {
          var streamError = true
          try {
            val u = f(value)
            streamError = false
            callback.onSuccess(u)
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
          }
        }
      })
    }

  /**
   * Given a source Task that emits another Task, this function
   * flattens the result, returning a Task equivalent to the
   * emitted Task by the source.
   */
  def flatten[U](implicit ev: T <:< Task[U]): Task[U] =
    Task.unsafeCreate { callback =>
      val cancelable = MultiAssignmentCancelable.collapsible()

      cancelable := self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onSuccess(value: T): Unit =
          cancelable := value.unsafeRun(callback)
        def onError(ex: Throwable): Unit =
          callback.onError(ex)
      })

      cancelable
    }

  /**
   * Creates a new Task by applying a function to the successful
   * result of the source Task, and returns a task equivalent to
   * the result of the function.
   *
   * Calling `flatMap` is literally the equivalent of:
   * {{{
   *   task.map(f).flatten
   * }}}
   */
  def flatMap[U](f: T => Task[U]): Task[U] =
    map(f).flatten

  /**
   * Returns a task that waits for the specified `timespan` before
   * executing and mirroring the result of the source.
   */
  def delay(timespan: FiniteDuration): Task[T] =
    Task.unsafeCreate[T] { callback =>
      val cancelable = MultiAssignmentCancelable()
      cancelable := callback.scheduler.scheduleOnce(timespan,
        new Runnable {
          override def run(): Unit = {
            cancelable := self.unsafeRun(callback)
          }
        })

      cancelable
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
    Task.unsafeCreate { callback =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler

        def onError(ex: Throwable): Unit =
          callback.onSuccess(ex)

        def onSuccess(value: T): Unit =
          callback.onError(new NoSuchElementException("Task.failed"))
      })
    }

  /**
   * Creates a new task that will handle any matching throwable
   * that this task might emit.
   */
  def onErrorRecover[U >: T](pf: PartialFunction[Throwable, U]): Task[U] =
    Task.unsafeCreate { callbackU =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callbackU.scheduler

        def onError(ex: Throwable): Unit = {
          var streamError = false
          try {
            if (pf.isDefinedAt(ex)) {
              val u = pf(ex)
              streamError = true
              callbackU.onSuccess(u)
            } else {
              callbackU.onError(ex)
            }
          } catch {
            case NonFatal(err) if streamError =>
              callbackU.scheduler.reportFailure(ex)
              callbackU.onError(err)
          }
        }

        def onSuccess(value: T): Unit =
          callbackU.onSuccess(value)
      })
    }

  /**
   * Creates a new task that will handle any matching throwable that this
   * task might emit by executing another task.
   */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Task[U]]): Task[U] = {
    Task.unsafeCreate { callbackU =>
      val cancelable = MultiAssignmentCancelable()

      cancelable := self.unsafeRun(new TaskCallback[T] {
        val scheduler = callbackU.scheduler

        def onError(ex: Throwable): Unit = {
          var streamError = true
          try {
            if (pf.isDefinedAt(ex)) {
              val newTask = pf(ex)
              streamError = false
              cancelable := newTask.unsafeRun(callbackU)
            } else {
              callbackU.onError(ex)
            }
          } catch {
            case NonFatal(err) if streamError =>
              callbackU.scheduler.reportFailure(ex)
              callbackU.onError(err)
          }
        }

        def onSuccess(value: T): Unit =
          callbackU.onSuccess(value)
      })

      cancelable
    }
  }

  /**
   * Returns a Task that mirrors the source Task but that triggers a
   * `TimeoutException` in case the given duration passes without the
   * task emitting any item.
   */
  def timeout(after: FiniteDuration): Task[T] =
    Task.unsafeCreate { callback =>
      val c = CompositeCancelable()

      c += callback.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (c.cancel())
              callback.onError(new TimeoutException(
                s"Task timed-out after $after of inactivity"))
          }
        })

      c += self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          if (c.cancel()) callback.onError(ex)
        def onSuccess(value: T): Unit =
          if (c.cancel()) callback.onSuccess(value)
      })

      Cancelable(c.cancel())
    }

  /**
   * Returns a Task that mirrors the source Task but switches to
   * the given backup Task in case the given duration passes without the
   * source emitting any item.
   */
  def timeout[U >: T](after: FiniteDuration, backup: Task[U]): Task[U] =
    Task.unsafeCreate { callback =>
      val isActive = CompositeCancelable()
      val cancelable = MultiAssignmentCancelable(isActive)

      isActive += callback.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (isActive.cancel())
              cancelable := backup.unsafeRun(callback)
          }
        })

      isActive += self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          if (isActive.cancel()) callback.onError(ex)
        def onSuccess(value: T): Unit =
          if (isActive.cancel()) callback.onSuccess(value)
      })

      cancelable
    }

  /**
   * Zips the values of `this` and `that` task, and creates a new task that
   * will emit the tuple of their results.
   */
  def zip[U](that: Task[U]): Task[(T, U)] =
    Task.unsafeCreate { callbackTU =>
      val c = CompositeCancelable()
      val state = Atomic(null : Either[T, U])

      c += self.unsafeRun(
        new TaskCallback[T] {
          val scheduler = callbackTU.scheduler
          def onError(ex: Throwable): Unit = {
            if (c.cancel())
              callbackTU.onError(ex)
          }

          @tailrec
          def onSuccess(t: T): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Left(t)))
                  onSuccess(t)
              case Right(u) =>
                callbackTU.onSuccess((t, u))
              case Left(_) =>
                ()
            }
        })


      c += that.unsafeRun(
        new TaskCallback[U] {
          val scheduler = callbackTU.scheduler
          def onError(ex: Throwable): Unit = {
            if (c.cancel())
              callbackTU.onError(ex)
          }

          @tailrec
          def onSuccess(u: U): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Right(u)))
                  onSuccess(u)
              case Left(t) =>
                callbackTU.onSuccess((t, u))
              case Right(_) =>
                ()
            }
        })

      Cancelable(c.cancel())
    }

  /**
   * Converts this task into a Scala `Future`, triggering its execution.
   */
  def asFuture(implicit s: Scheduler): CancelableFuture[T] =
    CancelableFuture(this)
}

object Task {
  /**
   * Returns a new task that, when executed, will emit the
   * result of the given function executed asynchronously.
   */
  def apply[T](f: => T): Task[T] =
    Task.unsafeCreate { callback =>
      val cancelable = BooleanCancelable()
      callback.scheduler.execute(
        new Runnable {
          override def run(): Unit =
            if (!cancelable.isCanceled) {
              try callback.onSuccess(f) catch {
                case NonFatal(ex) =>
                  callback.onError(ex)
              }
            }
        })

      cancelable
    }

  /**
   * Builder for [[Task]] instances. Only use if you know what
   * you're doing.
   */
  def unsafeCreate[T](f: TaskCallback[T] => Cancelable): Task[T] =
    new Task[T] {
      def unsafeRun(c: TaskCallback[T]): Cancelable = f(c)
    }

  /**
   * Returns a task that on execution is always successful,
   * emitting the given element.
   */
  def successful[T](elem: T): Task[T] =
    Task.unsafeCreate { callback =>
      val cancelable = BooleanCancelable()
      callback.scheduler.execute(
        new Runnable {
          override def run(): Unit =
            if (!cancelable.isCanceled) {
              try callback.onSuccess(elem) catch {
                case NonFatal(ex) =>
                  callback.onError(ex)
              }
            }
        })

      cancelable
    }

  /**
   * Returns a task that on execution is always finishing
   * in error emitting the specified exception.
   */
  def error(ex: Throwable): Task[Nothing] =
    Task.unsafeCreate { callback =>
      val cancelable = BooleanCancelable()
      callback.scheduler.execute(
        new Runnable {
          override def run(): Unit =
            if (!cancelable.isCanceled) {
              callback.onError(ex)
            }
        })

      cancelable
    }

  /**
   * Converts the given `Future` into a `Task`.
   */
  def fromFuture[T](f: => Future[T]): Task[T] =
    Task.unsafeCreate { callback =>
      implicit val s = callback.scheduler
      val cancelable = Cancelable()
      f.onComplete {
        case Success(value) =>
          if (cancelable.cancel())
            callback.onSuccess(value)

        case Failure(ex) =>
          if (cancelable.cancel())
            callback.onError(ex)
      }
      cancelable
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
      val futures: Seq[CancelableFuture[T]] = in.map(_.unsafeRun(cb.scheduler)).toSeq
      val waiting = Cancelable()

      val cancelable = CompositeCancelable(futures.map(_.cancelable):_*)
      cancelable += waiting

      val seqF = futures.foldLeft(Future.successful(cbf(in))) { (acc, f) =>
        for (seq <- acc; elem <- f) yield seq += elem
      }

      seqF.onComplete {
        case Failure(ex) =>
          if (waiting.cancel()) cb.onError(ex)
        case Success(seq) =>
          if (waiting.cancel()) cb.onSuccess(seq.result())
      }

      cancelable
    }
}
