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

import monix.execution.CompletionCallback
import monix.execution.atomic.Atomic
import monix.execution.exceptions.APIContractViolationException
import scala.annotation.tailrec

/** Joins the two independently ordered halves of `Async.cont`.
  *
  * The continuation callback may be signaled before or after its `get` effect is
  * interpreted. The atomic state records whichever side arrives first:
  *
  *  - `Init` means neither side has arrived;
  *  - `Waiting` retains the callback installed by `get`;
  *  - `Success` and `Failure` retain the terminal result.
  *
  * Publishing the terminal state before invoking a waiter also makes duplicate
  * signals harmless. A second concurrent signal observes a terminal state and is
  * ignored.
  */
final class TaskCallbackIndirection[A] extends CompletionCallback[A] {
  import TaskCallbackIndirection._

  private[this] val atomic = Atomic(Init: State[A])

  @tailrec
  def register(cb: CompletionCallback[A]): Unit =
    atomic.get() match {
      case current @ Init =>
        if (!atomic.compareAndSet(current, Waiting(cb)))
          register(cb)
      case Success(a) =>
        // The result is already terminal; replay it synchronously.
        cb.onSuccess(a)
      case Failure(e) =>
        cb.onError(e)
      case Cancelled =>
        cb.onCancellation()
      case Waiting(_) =>
        // Only one `get` may wait at a time. A canceled wait clears this state
        // before a later `get` is evaluated.
        throw new APIContractViolationException(
          s"${getClass.getSimpleName} was already registered"
        )
    }

  @tailrec
  def clear(): Unit =
    atomic.get() match {
      case current @ Waiting(_) =>
        if (!atomic.compareAndSet(current, Init))
          clear()
      case Init | Success(_) | Failure(_) | Cancelled =>
        ()
    }

  @tailrec
  private def signalLoop(boxed: Terminal[A]): Unit =
    atomic.get() match {
      case current @ Init =>
        if (!atomic.compareAndSet(current, boxed))
          signalLoop(boxed) // retry
      case current @ Waiting(cb: CompletionCallback[A] @unchecked) =>
        // Publish the result before invoking the callback. Even if it throws,
        // a duplicate signal cannot invoke this waiter a second time.
        if (atomic.compareAndSet(current, boxed)) {
          boxed match {
            case Success(value) => cb.onSuccess(value)
            case Failure(error) => cb.onError(error)
            case Cancelled => cb.onCancellation()
          }
        } else {
          signalLoop(boxed) // retry
        }
      case Success(_) | Failure(_) | Cancelled =>
        ()
    }

  override def onSuccess(a: A): Unit =
    signalLoop(Success(a))

  override def onError(e: Throwable): Unit =
    signalLoop(Failure(e))

  override def onCancellation(): Unit =
    signalLoop(Cancelled)
}

object TaskCallbackIndirection {
  private sealed trait State[+A]
  private sealed trait Terminal[+A] extends State[A]
  private case object Init extends State[Nothing]
  private final case class Waiting[A](cb: CompletionCallback[A]) extends State[A]
  private final case class Success[A](a: A) extends Terminal[A]
  private final case class Failure(e: Throwable) extends Terminal[Nothing]
  private case object Cancelled extends Terminal[Nothing]
}
