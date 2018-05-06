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

import monix.eval.Callback
import monix.eval.Task.{Context, Error, Now}
import monix.eval.internal.TaskRunLoop.{Bind, CallStack, startFull}
import monix.execution.misc.Local
import monix.execution.schedulers.TrampolinedRunnable

private[internal] abstract class RestartCallback(context: Context, callback: Callback[Any])
  extends Callback[Any] { self =>

  private[this] val runLoopIndex = context.frameRef
  private[this] val scheduler = context.scheduler

  // Modified on prepare()
  private[this] var canCall = false
  private[this] var bFirst: Bind = _
  private[this] var bRest: CallStack = _

  // Mutated in onSuccess and onError, just before scheduling
  // onSuccessRun and onErrorRun
  private[this] var value: Any = _
  private[this] var error: Throwable = _

  /** Saves the context that needs to be restored after the async boundary.
    *
    * @param restoreLocalsNext is a boolean indicating whether the current
    *        local context should be restored after the async boundary or not
    */
  def prepare(bindCurrent: Bind, bindRest: CallStack, restoreLocalsNext: Boolean): Unit = {
    canCall = true
    this.bFirst = bindCurrent
    this.bRest = bindRest
  }

  final def onSuccess(value: Any): Unit =
    if (canCall && !context.shouldCancel) {
      canCall = false
      self.value = value
      scheduler.execute(onSuccessRun)
    }

  final def onError(error: Throwable): Unit =
    if (canCall && !context.shouldCancel) {
      canCall = false
      self.error = error
      scheduler.execute(onErrorRun)
    } else {
      // $COVERAGE-OFF$
      context.scheduler.reportFailure(error)
      // $COVERAGE-ON$
    }

  protected def prepareCallback: Callback[Any]
  private[this] val wrappedCallback = prepareCallback
  protected def afterFork(): Unit

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
          self.wrappedCallback,
          self,
          self.bFirst,
          self.bRest,
          self.runLoopIndex())
      }
    }
}

private[internal] object RestartCallback {
  /** Builder for [[RestartCallback]], returning a specific instance
    * optimized for the passed in `Task.Options`.
    */
  def apply(context: Context, callback: Callback[Any]): RestartCallback = {
    if (context.options.localContextPropagation)
      new WithLocals(context, callback)
    else
      new NoLocals(context, callback)
  }

  /** `RestartCallback` class meant for `localContextPropagation == false`. */
  private final class NoLocals(context: Context, callback: Callback[Any])
    extends RestartCallback(context, callback) {

    def prepareCallback: Callback[Any] = callback
    def afterFork(): Unit = ()
  }

  /** `RestartCallback` class meant for `localContextPropagation == true`. */
  private final class WithLocals(context: Context, callback: Callback[Any])
    extends RestartCallback(context, callback) {

    private[this] var preparedLocals: Local.Context = _
    private[this] var previousLocals: Local.Context = _

    override def prepare(bindCurrent: Bind, bindRest: CallStack, restoreLocalsNext: Boolean): Unit = {
      super.prepare(bindCurrent, bindRest, restoreLocalsNext)
      preparedLocals = if (restoreLocalsNext) Local.getContext() else null
    }

    def prepareCallback: Callback[Any] =
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

    def afterFork(): Unit = {
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
