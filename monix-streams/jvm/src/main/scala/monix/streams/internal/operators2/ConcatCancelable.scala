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
import org.sincron.atomic.{Atomic, PaddingStrategy}
import scala.annotation.tailrec

/** Internal [[Cancelable]] used in the [[ConcatMapObservable]] implementation. */
private[operators2] abstract class ConcatCancelableOrigin extends ConcatMapObservable.ConcatCancelable {
  protected val originRef = Atomic.withPadding(Cancelable.empty, PaddingStrategy.Left64)
}

private[operators2] abstract class ConcatCancelableErrors(delayErrors: Boolean)
  extends ConcatCancelableOrigin {

  protected val errorsRef = if (!delayErrors) null else
    Atomic.withPadding(Vector.empty[Throwable], PaddingStrategy.Left64)
}

private[streams] final class ConcatCancelableImpl[A]
  (out: Observer[A], delayErrors: Boolean)(implicit s: Scheduler)
  extends ConcatCancelableErrors(delayErrors) { self =>

  import ConcatCancelableImpl.{canceled, completed}

  private[this] val childStateRef =
    Atomic.withPadding(null : Cancelable, PaddingStrategy.LeftRight128)

  private def cancelOrigin(): Unit = {
    val oldState = originRef.getAndSet(completed)
    if (oldState ne completed) oldState.cancel()
  }

  def `:=`(value: Cancelable): Cancelable = {
    val oldState = originRef.getAndSet(value)
    if (oldState ne null) {
      cancelOrigin()
      throw new IllegalStateException("ConcatCancelable already had an origin")
    } else if (oldState eq completed) {
      cancelOrigin()
    }
    this
  }

  def `+=`(value: Cancelable): Cancelable = {
    val oldState = childStateRef.getAndSet(value)
    if (oldState eq canceled) {
      cancel()
      this
    }
    else if (oldState ne null)
      throw new IllegalStateException("Found non-null state")
    else
      this
  }

  @tailrec def onError(ex: Throwable): Unit = {
    val oldState = childStateRef.getAndSet(canceled)
    if (oldState != canceled && oldState != null) {
      oldState.cancel()
      onError(ex)
    }
    else if (delayErrors) {
      errorsRef.transform(_ :+ ex)
      tryOnComplete()
    }
    else if (originRef.getAndSet(completed) ne completed) {
      out.onError(ex)
    }
  }

  def tryOnComplete(): Unit = {
    val oldState = childStateRef.getAndSet(null)
    if (oldState == null)
      if (originRef.getAndSet(completed) ne completed) {
        if (delayErrors) {
          val errors = errorsRef.get
          if (errors.nonEmpty) out.onError(CompositeException(errors))
          else out.onComplete()
        }
        else
          out.onComplete()
      }
  }

  @tailrec def cancel(): Unit = {
    val oldState = childStateRef.getAndSet(canceled)
    if (oldState != canceled && oldState != null) {
      oldState.cancel()
      cancel()
    } else {
      cancelOrigin()
    }
  }
}

private[streams] object ConcatCancelableImpl {
  private final val canceled: Cancelable =
    new Cancelable { def cancel(): Unit = () }

  private final val completed: Cancelable =
    new Cancelable { def cancel(): Unit = () }
}