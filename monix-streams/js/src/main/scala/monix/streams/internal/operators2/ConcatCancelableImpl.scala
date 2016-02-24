/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators2

import monix.execution.{Cancelable, Scheduler}
import monix.streams.Observer
import monix.streams.exceptions.CompositeException
import scala.collection.mutable

/** Internal [[Cancelable]] used in the [[ConcatMapObservable]] implementation. */
private[streams] final class ConcatCancelableImpl[A]
  (out: Observer[A], delayErrors: Boolean)(implicit s: Scheduler)
  extends ConcatMapObservable.ConcatCancelable { self =>

  import ConcatCancelableImpl.canceled

  private[this] var isComplete = false
  private[this] var state = null : Cancelable
  private[this] val errors = if (delayErrors)
    mutable.ArrayBuffer.empty[Throwable] else null

  private[this] var originRef = Cancelable.empty
  private def cancelOrigin(): Unit =
    originRef.cancel()

  def `:=`(value: Cancelable): Cancelable =
    self.synchronized {
      if (originRef == null)
        throw new IllegalArgumentException
      else if (isComplete)
        originRef.cancel()
      else
        originRef = value
      this
    }

  def `+=`(value: Cancelable): Cancelable = {
    if (state eq canceled)
      value.cancel()
    else if (state ne null)
      throw new IllegalStateException("Non-null state in ConcatCancelable")
    else
      state = value
    this
  }

  def onError(ex: Throwable): Unit = {
    val oldState = state
    state = canceled

    if (oldState != canceled && oldState != null)
      oldState.cancel()

    if (!isComplete) {
      if (delayErrors) {
        errors += ex
        tryOnComplete()
      } else {
        isComplete = true
        out.onError(ex)
      }
    }
  }

  def tryOnComplete(): Unit = {
    val oldState = state
    state = null

    if (oldState == null) {
      if (!isComplete) {
        isComplete = true
        if (delayErrors && errors.nonEmpty)
          out.onError(CompositeException(errors))
        else
          out.onComplete()
      }
    }
  }

  def cancel(): Unit = {
    val oldState = state
    state = null

    if (oldState != canceled && oldState != null) oldState.cancel()
    cancelOrigin()
  }
}

private[streams] object ConcatCancelableImpl {
  private final val canceled: Cancelable =
    new Cancelable { def cancel(): Unit = () }
}