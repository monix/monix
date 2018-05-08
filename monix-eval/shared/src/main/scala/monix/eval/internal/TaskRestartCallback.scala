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

package monix.eval.internal

import monix.eval.Task.{Context, Error, Now}
import monix.eval.internal.TaskRunLoop.{Bind, CallStack, startFull}
import monix.eval.{Callback, Task}
import monix.execution.misc.Local
import monix.execution.schedulers.TrampolinedRunnable

private[internal] abstract class TaskRestartCallback(context: Context, callback: Callback[Any])
  extends Callback[Any] with TrampolinedRunnable { self =>

  private[this] val runLoopIndex = context.frameRef
  private[this] val scheduler = context.scheduler

  // Modified on prepare()
  private[this] var canCall = false
  private[this] var bFirst: Bind = _
  private[this] var bRest: CallStack = _
  private[this] var register: (Context, Callback[Any]) => Unit = _

  // Mutated in onSuccess and onError, just before scheduling
  // onSuccessRun and onErrorRun
  private[this] var value: Any = _
  private[this] var error: Throwable = _
  private[this] var trampolineAfter: Boolean = true

  final def start(task: Task.Async[Any], bindCurrent: Bind, bindRest: CallStack): Unit = {
    canCall = true
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
    this.register(context, this)
  }

  final def onSuccess(value: Any): Unit =
    if (canCall && !context.shouldCancel) {
      canCall = false
      self.value = value
      if (trampolineAfter)
        scheduler.execute(onSuccessRun)
      else
        onSuccessRun.run()
    }

  final def onError(error: Throwable): Unit =
    if (canCall && !context.shouldCancel) {
      canCall = false
      self.error = error
      if (trampolineAfter)
        scheduler.execute(onErrorRun)
      else
        onErrorRun.run()
    } else {
      // $COVERAGE-OFF$
      context.scheduler.reportFailure(error)
      // $COVERAGE-ON$
    }

  protected def prepareStart(task: Task.Async[_]): Unit = ()
  protected def prepareCallback: Callback[Any] = callback
  private[this] val wrappedCallback = prepareCallback
  protected def afterFork(): Unit = ()

  /** Reusable Runnable reference, to go lighter on memory allocations. */
  private[this] val onSuccessRun: TrampolinedRunnable =
    new TrampolinedRunnable {
      def run(): Unit = {
        afterFork()
        startFull(
          Now(self.value),
          context,
          self.wrappedCallback,
          self,
          self.bFirst,
          self.bRest,
          self.runLoopIndex())
      }
    }

  /** Reusable Runnable reference, to go lighter on memory allocations. */
  private[this] val onErrorRun: TrampolinedRunnable =
    new TrampolinedRunnable {
      def run(): Unit = {
        afterFork()
        startFull(
          Error(self.error),
          context,
          callback,
          self,
          self.bFirst,
          self.bRest,
          self.runLoopIndex())
      }
    }
}

private[internal] object TaskRestartCallback {
  /** Builder for [[TaskRestartCallback]], returning a specific instance
    * optimized for the passed in `Task.Options`.
    */
  def apply(context: Context, callback: Callback[Any]): TaskRestartCallback = {
    if (context.options.localContextPropagation)
      new WithLocals(context, callback)
    else
      new NoLocals(context, callback)
  }

  /** `RestartCallback` class meant for `localContextPropagation == false`. */
  private final class NoLocals(context: Context, callback: Callback[Any])
    extends TaskRestartCallback(context, callback)

  /** `RestartCallback` class meant for `localContextPropagation == true`. */
  private final class WithLocals(context: Context, callback: Callback[Any])
    extends TaskRestartCallback(context, callback) {

    private[this] var preparedLocals: Local.Context = _
    private[this] var previousLocals: Local.Context = _

    override protected def prepareStart(task: Task.Async[_]): Unit = {
      preparedLocals =
        if (task.restoreLocals) Local.getContext() else null
    }

    override def prepareCallback: Callback[Any] =
      new Callback[Any] {
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

    override def afterFork(): Unit = {
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
