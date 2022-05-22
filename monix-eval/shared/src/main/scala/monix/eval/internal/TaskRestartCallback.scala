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

package monix.eval.internal

import monix.eval.Task.{ Context, Error, Now }
import monix.eval.internal.TaskRunLoop.{ startFull, Bind, CallStack }
import monix.eval.Task
import monix.execution.Callback
import monix.execution.misc.Local
import monix.execution.schedulers.TrampolinedRunnable
import scala.annotation.unused

private[internal] abstract class TaskRestartCallback(contextInit: Context, callback: Callback[Throwable, Any])
  extends Callback[Throwable, Any] with TrampolinedRunnable {

  // Modified on prepare()
  private[this] var bFirst: Bind = _
  private[this] var bRest: CallStack = _
  private[this] var register: (Context, Callback[Throwable, Any]) => Unit = _

  // Mutated in onSuccess and onError, just before scheduling
  // onSuccessRun and onErrorRun
  private[this] var value: Any = _
  private[this] var error: Throwable = _
  private[this] var trampolineAfter: Boolean = true

  // Can change via ContextSwitch
  private[this] var context = contextInit

  final def contextSwitch(other: Context): Unit = {
    this.context = other
  }

  final def start(task: Task.Async[Any], bindCurrent: Bind, bindRest: CallStack): Unit = {
    this.bFirst = bindCurrent
    this.bRest = bindRest
    this.trampolineAfter = task.trampolineAfter
    prepareStart(task)

    if (task.trampolineBefore) {
      this.register = task.register
      context.scheduler.execute(this)
    } else {
      task.register(context, this)
    }
  }

  final def run(): Unit = {
    val fn = this.register
    this.register = null
    fn(context, this)
  }

  final def onSuccess(value: Any): Unit =
    if (!context.shouldCancel) {
      if (trampolineAfter) {
        this.value = value
        context.scheduler.execute(onSuccessRun)
      } else {
        syncOnSuccess(value)
      }
    }

  final def onError(error: Throwable): Unit =
    if (!context.shouldCancel) {
      if (trampolineAfter) {
        this.error = error
        context.scheduler.execute(onErrorRun)
      } else {
        syncOnError(error)
      }
    } else {
      // $COVERAGE-OFF$
      context.scheduler.reportFailure(error)
      // $COVERAGE-ON$
    }

  protected def prepareStart(@unused task: Task.Async[_]): Unit = ()
  protected def prepareCallback: Callback[Throwable, Any] = callback
  private[this] val wrappedCallback = prepareCallback

  protected def syncOnSuccess(value: Any): Unit = {
    val bFirst = this.bFirst
    val bRest = this.bRest
    this.bFirst = null
    this.bRest = null
    startFull(Now(value), context, wrappedCallback, this, bFirst, bRest, this.context.frameRef())
  }

  protected def syncOnError(error: Throwable): Unit = {
    val bFirst = this.bFirst
    val bRest = this.bRest
    this.bFirst = null
    this.bRest = null
    startFull(Error(error), context, this.wrappedCallback, this, bFirst, bRest, this.context.frameRef())
  }

  /** Reusable Runnable reference, to go lighter on memory allocations. */
  private[this] val onSuccessRun: TrampolinedRunnable =
    new TrampolinedRunnable {
      def run(): Unit = {
        val v = value
        value = null
        syncOnSuccess(v)
      }
    }

  /** Reusable Runnable reference, to go lighter on memory allocations. */
  private[this] val onErrorRun: TrampolinedRunnable =
    new TrampolinedRunnable {
      def run(): Unit = {
        val e = error
        error = null
        syncOnError(e)
      }
    }
}

private[internal] object TaskRestartCallback {
  /** Builder for [[TaskRestartCallback]], returning a specific instance
    * optimized for the passed in `Task.Options`.
    */
  def apply(context: Context, callback: Callback[Throwable, Any]): TaskRestartCallback = {
    if (context.options.localContextPropagation)
      new WithLocals(context, callback)
    else
      new NoLocals(context, callback)
  }

  /** `RestartCallback` class meant for `localContextPropagation == false`. */
  private final class NoLocals(context: Context, callback: Callback[Throwable, Any])
    extends TaskRestartCallback(context, callback)

  /** `RestartCallback` class meant for `localContextPropagation == true`. */
  private final class WithLocals(context: Context, callback: Callback[Throwable, Any])
    extends TaskRestartCallback(context, callback) {

    private[this] var preparedLocals: Local.Context = _
    private[this] var previousLocals: Local.Context = _

    override protected def prepareStart(task: Task.Async[_]): Unit = {
      preparedLocals = if (task.restoreLocals) Local.getContext() else null
    }

    override def prepareCallback: Callback[Throwable, Any] =
      new Callback[Throwable, Any] {
        def onSuccess(value: Any): Unit = {
          val locals = previousLocals
          if (locals ne null) Local.setContext(locals)
          callback.onSuccess(value)
        }

        def onError(ex: Throwable): Unit = {
          val locals = previousLocals
          if (locals ne null) Local.setContext(locals)
          callback.onError(ex)
        }
      }

    override protected def syncOnSuccess(value: Any): Unit = {
      setPreparedLocals()
      super.syncOnSuccess(value)
    }

    override protected def syncOnError(error: Throwable): Unit = {
      setPreparedLocals()
      super.syncOnError(error)
    }

    def setPreparedLocals(): Unit = {
      val preparedLocals = this.preparedLocals
      if (preparedLocals ne null) {
        previousLocals = Local.getContext()
        Local.setContext(preparedLocals)
      } else {
        previousLocals = null
      }
    }
  }
}
