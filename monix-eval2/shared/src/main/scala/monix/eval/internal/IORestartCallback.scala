/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
package internal

import monix.eval.IO.AsyncSimple.{AsyncTrampolined, BoundaryPolicy}
import monix.execution.exceptions.APIContractViolationException
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{Callback, Scheduler}
import scala.util.control.NonFatal

private[internal] class IORestartCallback(
  fiber: IOFiber[_],
  scheduler: Scheduler,
) extends Callback[Throwable, Any] {
  self =>

  /** Set in [[start]] */
  private[this] var boundaryAfterPolicy: BoundaryPolicy = AsyncTrampolined
  private[this] var transportedHasValue: Boolean = false
  private[this] var transportedValue: Any = _
  private[this] var transportedError: Throwable = _

  private[this] var task: IO.AsyncSimple[_] = _

  private[this] var _startWithAsyncShifted: Runnable = _
  private[this] var _startWithAsyncTrampolined: Runnable = _
  private[this] var _signalValueShiftedRef: Runnable = _
  private[this] var _signalValueTrampolinedRef: TrampolinedRunnable = _

  final def start(task: IO.AsyncSimple[_]): Unit = {
    this.boundaryAfterPolicy = task.boundaryAfter
    if (task.boundaryBefore == IO.AsyncSimple.Synchronous) {
      safeStart(task)
    } else {
      this.task = task
      if (task.boundaryBefore == AsyncTrampolined)
        scheduler.execute(startWithAsyncShifted())
      else
        scheduler.execute(startWithAsyncTrampolined())
    }
  }

  private final def onSuccessOrError(hasValue: Boolean, value: Any, error: Throwable): Unit = {
    val boundaryAfterPolicy = self.boundaryAfterPolicy
    self.boundaryAfterPolicy = AsyncTrampolined

    if (boundaryAfterPolicy == IO.AsyncSimple.Synchronous) {
      if (hasValue)
        fiber.continueWithRef(IO.Pure(value))
      else
        fiber.continueWithRef(IO.RaiseError(error))
    } else {
      self.transportedHasValue = hasValue
      self.transportedValue = value
      self.transportedError = error
      if (boundaryAfterPolicy == IO.AsyncSimple.AsyncShifted)
        scheduler.execute(signalValueShifted())
      else
        scheduler.execute(signalValueTrampolined())
    }
  }
  override final def onSuccess(value: Any): Unit =
    onSuccessOrError(hasValue = true, value, null)

  override final def onError(e: Throwable): Unit =
    onSuccessOrError(hasValue = false, null, e)

  private final def safeStart(task: IO.AsyncSimple[_]): Unit =
    try {
      task.start(scheduler, this)
    } catch {
      case ex if NonFatal(ex) =>
        scheduler.reportFailure(
          new APIContractViolationException(
            "IO.AsyncSimple#start threw an unexpected exception, this is most likely a library bug",
            ex
          )
        )
    }

  private final def signalValueShifted(): Runnable = {
    if (_signalValueShiftedRef == null) {
      val trampolined = signalValueTrampolined()
      _signalValueShiftedRef = () => trampolined.run()
    }
    _signalValueShiftedRef
  }

  private final def signalValueTrampolined(): TrampolinedRunnable = {
    if (_signalValueTrampolinedRef == null) {
      _signalValueTrampolinedRef = new SignalValueTrampolinedRunnable
    }
    _signalValueTrampolinedRef
  }

  private final class SignalValueTrampolinedRunnable extends TrampolinedRunnable {
    override def run(): Unit =
      if (self.transportedHasValue) {
        val value = self.transportedValue
        self.transportedHasValue = false
        self.transportedValue = null
        fiber.continueWithRef(IO.Pure(value))
      } else {
        val error = self.transportedError
        self.transportedError = null
        fiber.continueWithRef(IO.RaiseError(error))
      }
  }

  private final def startWithAsyncTrampolined(): Runnable = {
    if (_startWithAsyncTrampolined == null)
      _startWithAsyncTrampolined = new StartTrampolinedRunnable
    _startWithAsyncTrampolined
  }

  private final def startWithAsyncShifted(): Runnable = {
    if (_startWithAsyncShifted == null) {
      val runnable = startWithAsyncTrampolined()
      _startWithAsyncShifted = () => runnable.run()
    }
    _startWithAsyncShifted
  }

  private final class StartTrampolinedRunnable extends TrampolinedRunnable {
    override def run(): Unit = {
      val task = self.task
      self.task = null
      safeStart(task)
    }
  }
}
