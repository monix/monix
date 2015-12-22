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
  protected def unsafeRun(c: TaskCallback[T]): Unit

  /**
   * Triggers the asynchronous execution.
   */
  def runAsync(c: TaskCallback[T]): Unit =
    unsafeRun(TaskCallback.safe(c))

  /**
   * Triggers the asynchronous execution.
   */
  def runAsync(implicit s: Scheduler): Future[T] = {
    val p = Promise[T]()
    unsafeRun(new TaskCallback[T] {
      val scheduler = s
      def onError(ex: Throwable): Unit =
        p.tryFailure(ex)
      def onSuccess(value: T): Unit =
        p.trySuccess(value)
    })
    p.future
  }

  /**
   * Triggers the asynchronous execution.
   *
   * @param f is a function that will be called with the result on complete
   * @return a [[Cancelable]] that can be used to cancel the in progress async computation
   */
  def runAsync(f: Try[T] => Unit)(implicit s: Scheduler): Unit =
    unsafeRun(TaskCallback.safe(new TaskCallback[T] {
      val scheduler = s
      def onError(ex: Throwable): Unit = f(Failure(ex))
      def onSuccess(value: T): Unit = f(Success(value))
    }))

  /**
   * Returns a new Task that applies the mapping function to
   * the element emitted by the source.
   */
  def map[U](f: T => U): Task[U] =
    Task.unsafeCreate[U] { callback =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          callback.asyncOnError(ex)

        def onSuccess(value: T): Unit = {
          // protection against user code
          var streamError = true
          try {
            val u = f(value)
            streamError = false
            callback.asyncOnSuccess(u)
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
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          callback.asyncOnError(ex)

        def onSuccess(value: T): Unit = {
          // protection against stack overflows
          scheduler.execute(new Runnable {
            def run() = value.unsafeRun(callback)
          })
        }
      })
    }

  /**
   * Creates a new Task by applying a function to the successful
   * result of the source Task, and returns a task equivalent to
   * the result of the function.
   */
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task.unsafeCreate[U] { callback =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          callback.asyncOnError(ex)

        def onSuccess(value: T): Unit = {
          // protection against user code
          try {
            val taskU = f(value)
            scheduler.execute(new Runnable {
              def run() = taskU.unsafeRun(callback)
            })
          } catch {
            case NonFatal(ex) =>
              onError(ex)
          }
        }
      })
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
    Task.unsafeCreate { callback =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler

        def onError(ex: Throwable): Unit =
          callback.asyncOnSuccess(ex)
        def onSuccess(value: T): Unit =
          callback.asyncOnError(new NoSuchElementException("Task.failed"))
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

        def onSuccess(value: T): Unit =
          callbackU.asyncOnSuccess(value)

        def onError(ex: Throwable): Unit = {
          try {
            if (pf.isDefinedAt(ex))
              callbackU.asyncOnSuccess(pf(ex))
            else
              callbackU.asyncOnError(ex)
          } catch {
            case NonFatal(err) =>
              scheduler.reportFailure(ex)
              callbackU.asyncOnError(err)
          }
        }
      })
    }

  /**
   * Creates a new task that will handle any matching throwable that this
   * task might emit by executing another task.
   */
  def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Task[U]]): Task[U] = {
    Task.unsafeCreate { callbackU =>
      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callbackU.scheduler

        def onSuccess(value: T): Unit =
          callbackU.asyncOnSuccess(value)

        def onError(ex: Throwable): Unit = {
          try {
            if (pf.isDefinedAt(ex)) {
              val newTask = pf(ex)
              newTask.unsafeRun(callbackU)
            } else {
              callbackU.asyncOnError(ex)
            }
          } catch {
            case NonFatal(err) =>
              scheduler.reportFailure(ex)
              callbackU.asyncOnError(err)
          }
        }
      })
    }
  }

  /**
   * Returns a Task that mirrors the source Task but that triggers a
   * `TimeoutException` in case the given duration passes without the
   * task emitting any item.
   */
  def timeout(after: FiniteDuration): Task[T] = {
    Task.unsafeCreate { callback =>
      val timeoutTask = SingleAssignmentCancelable()

      timeoutTask := callback.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (timeoutTask.cancel())
              callback.onError(
                new TimeoutException(s"Task timed-out after $after of inactivity"))
          }
        })

      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          if (timeoutTask.cancel()) callback.asyncOnError(ex)
        def onSuccess(value: T): Unit =
          if (timeoutTask.cancel()) callback.asyncOnSuccess(value)
      })
    }
  }

  /**
   * Returns a Task that mirrors the source Task but switches to
   * the given backup Task in case the given duration passes without the
   * source emitting any item.
   */
  def timeout[U >: T](after: FiniteDuration, backup: Task[U]): Task[U] =
    Task.unsafeCreate { callback =>
      val timeoutTask = SingleAssignmentCancelable()

      timeoutTask := callback.scheduler.scheduleOnce(after,
        new Runnable {
          def run(): Unit = {
            if (timeoutTask.cancel())
              backup.unsafeRun(callback)
          }
        })

      self.unsafeRun(new TaskCallback[T] {
        val scheduler = callback.scheduler
        def onError(ex: Throwable): Unit =
          if (timeoutTask.cancel()) callback.asyncOnError(ex)
        def onSuccess(value: T): Unit =
          if (timeoutTask.cancel()) callback.asyncOnSuccess(value)
      })
    }

  /**
   * Zips the values of `this` and `that` task, and creates a new task that
   * will emit the tuple of their results.
   */
  def zip[U](that: Task[U]): Task[(T, U)] = {
    Task.unsafeCreate { callbackTU =>
      val state = Atomic(null : Either[T, U])

      // monitoring self
      self.unsafeRun(
        new TaskCallback[T] {
          val scheduler = callbackTU.scheduler
          def onError(ex: Throwable): Unit =
            callbackTU.asyncOnError(ex)

          @tailrec
          def onSuccess(t: T): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Left(t))) onSuccess(t)
              case Right(u) =>
                callbackTU.asyncOnSuccess((t, u))
              case Left(_) =>
                ()
            }
        })

      // monitoring the other
      that.unsafeRun(
        new TaskCallback[U] {
          val scheduler = callbackTU.scheduler
          def onError(ex: Throwable): Unit =
            callbackTU.asyncOnError(ex)

          @tailrec
          def onSuccess(u: U): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Right(u))) onSuccess(u)
              case Left(t) =>
                callbackTU.asyncOnSuccess((t, u))
              case Right(_) =>
                ()
            }
        })
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
  def unsafeCreate[T](f: TaskCallback[T] => Unit): Task[T] =
    new Task[T] {
      override def unsafeRun(c: TaskCallback[T]): Unit = f(c)
    }

  /**
   * Returns a task that on execution is always successful,
   * emitting the given element.
   */
  def success[T](elem: T): Task[T] =
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
  def error(ex: Throwable): Task[Nothing] =
    Task.unsafeCreate { cb =>
      cb.scheduler.execute(new Runnable {
        override def run(): Unit = cb.onError(ex)
      })
    }

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
}
