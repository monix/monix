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

package monix.eval.internal

import monix.execution.Callback
import monix.execution.atomic.Atomic
import monix.execution.exceptions.{APIContractViolationException, CallbackCalledMultipleTimesException}

import scala.annotation.tailrec

final class IOCallbackIndirection[E, A] extends Callback[E, A] {
  import IOCallbackIndirection._

  private[this] val atomic = Atomic(Init : State[E, A])

  def register(cb: Callback[E, A]): Unit =
    atomic.get() match {
      case current @ Init =>
        if (!atomic.compareAndSet(current, Waiting(cb)))
          register(cb)
      case Success(a) =>
        cb.onSuccess(a)
      case Failure(e) =>
        cb.onError(e)
      case Waiting(_) =>
        throw new APIContractViolationException(
          s"${getClass.getSimpleName} was already registered"
        )
    }

  @tailrec
  private def signalLoop(isSuccess: Boolean, unboxed: Any, boxed: State[E, A]): Unit =
    atomic.get() match {
      case current@Init =>
        if (!atomic.compareAndSet(current, boxed))
          signalLoop(isSuccess, unboxed, boxed) // retry
      case current@Waiting(cb: Callback[E, A] @unchecked) =>
        if (atomic.compareAndSet(current, boxed)) {
          if (isSuccess)
            cb.onSuccess(unboxed.asInstanceOf[A])
          else
            cb.onError(unboxed.asInstanceOf[E])
        } else {
          signalLoop(isSuccess, unboxed, boxed) // retry
        }
      case Success(_) | Failure(_) =>
        throw new CallbackCalledMultipleTimesException(getClass.getSimpleName)
    }

  override def onSuccess(a: A): Unit =
    signalLoop(isSuccess = true, a, Success(a))

  override def onError(e: E): Unit =
    signalLoop(isSuccess = false, e, Failure(e))
}

object IOCallbackIndirection {
  private sealed trait State[+E, +A]
  private final case object Init extends State[Nothing, Nothing]
  private final case class Waiting[E, A](cb: Callback[E, A]) extends State[E, A]
  private final case class Success[A](a: A) extends State[Nothing, A]
  private final case class Failure[E](e: E) extends State[E, Nothing]
}
