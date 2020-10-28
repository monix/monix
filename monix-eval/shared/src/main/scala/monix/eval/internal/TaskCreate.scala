/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.eval.internal

import java.util.concurrent.RejectedExecutionException

import cats.effect.{CancelToken, IO}
import monix.eval.Task.Context
import monix.eval.{Coeval, Task}
import monix.execution.atomic.AtomicInt
import monix.execution.exceptions.CallbackCalledMultipleTimesException
import monix.execution.internal.Platform
import monix.execution.schedulers.{StartAsyncBatchRunnable, TrampolinedRunnable}
import monix.execution.{Callback, Cancelable, Scheduler, UncaughtExceptionReporter}

import scala.util.control.NonFatal

private[eval] object TaskCreate {
  /**
    * Implementation for `cats.effect.Concurrent#cancelable`.
    */
  def cancelableEffect[A](k: (Either[Throwable, A] => Unit) => CancelToken[Task]): Task[A] =
    cancelable0((_, cb) => k(cb))

  /**
    * Implementation for `Task.cancelable`
    */
  def cancelable0[A](fn: (Scheduler, Callback[Throwable, A]) => CancelToken[Task]): Task[A] = {
    val start = new Cancelable0Start[A, CancelToken[Task]](fn) {
      def setConnection(ref: TaskConnectionRef, token: CancelToken[Task])(implicit s: Scheduler): Unit =
        ref := token
    }
    TracedAsync[A](start, trampolineBefore = false, trampolineAfter = false, traceKey = fn)
  }

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableIO[A](start: (Scheduler, Callback[Throwable, A]) => CancelToken[IO]): Task[A] =
    cancelable0((sc, cb) => Task.from(start(sc, cb)))

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableCancelable[A](fn: (Scheduler, Callback[Throwable, A]) => Cancelable): Task[A] = {
    val start = new Cancelable0Start[A, Cancelable](fn) {
      def setConnection(ref: TaskConnectionRef, token: Cancelable)(implicit s: Scheduler): Unit =
        ref := token
    }
    TracedAsync[A](start, trampolineBefore = false, trampolineAfter = false, traceKey = fn)
  }

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableCoeval[A](start: (Scheduler, Callback[Throwable, A]) => Coeval[Unit]): Task[A] =
    cancelable0((sc, cb) => Task.from(start(sc, cb)))

  /**
    * Implementation for `Task.async0`
    */
  def async0[A](fn: (Scheduler, Callback[Throwable, A]) => Any): Task[A] = {
    val start = (ctx: Context, cb: Callback[Throwable, A]) => {
      implicit val s = ctx.scheduler
      val cbProtected = new CallbackForCreate(ctx, shouldPop = false, cb)
      try {
        fn(s, cbProtected)
        ()
      } catch {
        case e if NonFatal(e) =>
          if (!cbProtected.tryOnError(e)) {
            s.reportFailure(e)
          }
      }
    }
    TracedAsync[A](start, trampolineBefore = false, trampolineAfter = false, traceKey = fn)
  }

  /**
    * Implementation for `cats.effect.Async#async`.
    *
    * It duplicates the implementation of `Task.async0` with the purpose
    * of avoiding extraneous callback allocations.
    */
  def async[A](k: Callback[Throwable, A] => Unit): Task[A] = {
    val start = (ctx: Context, cb: Callback[Throwable, A]) => {
      implicit val s = ctx.scheduler
      val cbProtected = new CallbackForCreate(ctx, shouldPop = false, cb)
      try {
        k(cbProtected)
      } catch {
        case e if NonFatal(e) =>
          if (!cbProtected.tryOnError(e)) {
            s.reportFailure(e)
          }
      }
    }
    TracedAsync[A](start, trampolineBefore = false, trampolineAfter = false, traceKey = k)
  }

  /**
    * Implementation for `Task.asyncF`.
    */
  def asyncF[A](k: Callback[Throwable, A] => Task[Unit]): Task[A] = {
    val start = (ctx: Context, cb: Callback[Throwable, A]) => {
      implicit val s = ctx.scheduler
      // Creating new connection, because we can have a race condition
      // between the bind continuation and executing the generated task
      val ctx2 = Context(ctx.scheduler, ctx.options)
      val conn = ctx.connection
      conn.push(ctx2.connection.cancel)

      val cbProtected = new CallbackForCreate(ctx, shouldPop = true, cb)
      try {
        // Provided callback takes care of `conn.pop()`
        val task = k(cbProtected)
        Task.unsafeStartNow(task, ctx2, new ForwardErrorCallback(cbProtected))
      } catch {
        case e if NonFatal(e) =>
          if (!cbProtected.tryOnError(e)) {
            s.reportFailure(e)
          }
      }
    }
    TracedAsync[A](start, trampolineBefore = false, trampolineAfter = false, traceKey = k)
  }

  private abstract class Cancelable0Start[A, Token](fn: (Scheduler, Callback[Throwable, A]) => Token)
    extends ((Context, Callback[Throwable, A]) => Unit) {

    def setConnection(ref: TaskConnectionRef, token: Token)(implicit s: Scheduler): Unit

    final def apply(ctx: Context, cb: Callback[Throwable, A]): Unit = {
      implicit val s = ctx.scheduler
      val conn = ctx.connection
      val cancelable = TaskConnectionRef()
      conn push cancelable.cancel

      val cbProtected = new CallbackForCreate(ctx, shouldPop = true, cb)
      try {
        val ref = fn(s, cbProtected)
        // Optimization to skip the assignment, as it's expensive
        if (!ref.isInstanceOf[Cancelable.IsDummy])
          setConnection(cancelable, ref)
      } catch {
        case e if NonFatal(e) =>
          if (!cbProtected.tryOnError(e)) {
            s.reportFailure(e)
          }
      }
    }
  }

  private final class ForwardErrorCallback(cb: Callback[Throwable, _])(implicit r: UncaughtExceptionReporter)
    extends Callback[Throwable, Unit] {

    override def onSuccess(value: Unit): Unit = ()
    override def onError(e: Throwable): Unit =
      if (!cb.tryOnError(e)) {
        r.reportFailure(e)
      }
  }

  private final class CallbackForCreate[A](ctx: Context, threadId: Long, shouldPop: Boolean, cb: Callback[Throwable, A])
    extends Callback[Throwable, A] with TrampolinedRunnable {

    private[this] val state = AtomicInt(0)
    private[this] var value: A = _
    private[this] var error: Throwable = _
    private[this] var isSameThread = false

    def this(ctx: Context, shouldPop: Boolean, cb: Callback[Throwable, A]) =
      this(ctx, Platform.currentThreadId(), shouldPop, cb)

    override def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) {
        throw new CallbackCalledMultipleTimesException("onSuccess")
      }

    override def tryOnSuccess(value: A): Boolean = {
      if (state.compareAndSet(0, 1)) {
        this.value = value
        startExecution()
        true
      } else {
        false
      }
    }

    override def onError(e: Throwable): Unit =
      if (!tryOnError(e)) {
        throw new CallbackCalledMultipleTimesException("onError", e)
      }

    override def tryOnError(e: Throwable): Boolean = {
      if (state.compareAndSet(0, 2)) {
        this.error = e
        startExecution()
        true
      } else {
        false
      }
    }

    private def startExecution(): Unit = {
      // Cleanup of the current finalizer
      if (shouldPop) ctx.connection.pop()
      // Optimization — if the callback was called on the same thread
      // where it was created, then we are not going to fork
      // This is not safe to do when localContextPropagation enabled
      isSameThread = Platform.currentThreadId() == threadId
      try {
        ctx.scheduler.execute(
          if (isSameThread && !ctx.options.localContextPropagation)
            this
          else
            StartAsyncBatchRunnable(this, ctx.scheduler)
        )
      } catch {
        case e: RejectedExecutionException =>
          forceErrorReport(e)
      }
    }

    override def run(): Unit = {
      if (!isSameThread) {
        ctx.frameRef.reset()
      }
      state.get() match {
        case 1 =>
          val v = value
          value = null.asInstanceOf[A]
          cb.onSuccess(v)
        case 2 =>
          val e = error
          error = null
          cb.onError(e)
      }
    }

    private def forceErrorReport(e: RejectedExecutionException): Unit = {
      value = null.asInstanceOf[A]
      if (error != null) {
        val e = error
        error = null
        ctx.scheduler.reportFailure(e)
      }
      Callback.signalErrorTrampolined(cb, e)
    }
  }
}
