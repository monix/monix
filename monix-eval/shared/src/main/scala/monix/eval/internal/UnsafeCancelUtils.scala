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

import cats.effect.CancelToken
import monix.catnap.CancelableF
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import monix.execution.internal.Platform

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

private[eval] object UnsafeCancelUtils {
  /**
    * Internal API.
    */
  def taskToCancelable(task: Task[Unit])(implicit s: Scheduler): Cancelable = {
    if (task == Task.unit) Cancelable.empty
    else Cancelable(() => task.runAsyncAndForget(s))
  }

  /**
    * Internal API — very unsafe!
    */
  private[internal] def cancelAllUnsafe(
    cursor: Iterable[AnyRef /* Cancelable | Task[Unit] | CancelableF[Task] */ ]): CancelToken[Task] = {

    if (cursor.isEmpty)
      Task.unit
    else
      Task.suspend {
        val frame = new CancelAllFrame(cursor.iterator)
        frame.loop()
      }
  }

  /**
    * Internal API — very unsafe!
    */
  private[internal] def unsafeCancel(
    task: AnyRef /* Cancelable | Task[Unit] | CancelableF[Task] */ ): CancelToken[Task] = {

    task match {
      case ref: Task[Unit] @unchecked =>
        ref
      case ref: CancelableF[Task] @unchecked =>
        ref.cancel
      case ref: Cancelable =>
        ref.cancel()
        Task.unit
      case other =>
        // $COVERAGE-OFF$
        reject(other)
      // $COVERAGE-ON$
    }
  }

  /**
    * Internal API — very unsafe!
    */
  private[internal] def getToken(task: AnyRef /* Cancelable | Task[Unit] | CancelableF[Task] */ ): CancelToken[Task] =
    task match {
      case ref: Task[Unit] @unchecked =>
        ref
      case ref: CancelableF[Task] @unchecked =>
        ref.cancel
      case ref: Cancelable =>
        Task(ref.cancel())
      case other =>
        // $COVERAGE-OFF$
        reject(other)
      // $COVERAGE-ON$
    }

  /**
    * Internal API — very unsafe!
    */
  private[internal] def triggerCancel(task: AnyRef /* Cancelable | Task[Unit] | CancelableF[Task] */ )(implicit
    s: Scheduler): Unit = {

    task match {
      case ref: Task[Unit] @unchecked =>
        ref.runAsyncAndForget
      case ref: CancelableF[Task] @unchecked =>
        ref.cancel.runAsyncAndForget
      case ref: Cancelable =>
        try ref.cancel()
        catch {
          case NonFatal(e) => s.reportFailure(e)
        }
      case other =>
        // $COVERAGE-OFF$
        reject(other)
      // $COVERAGE-ON$
    }
  }

  // Optimization for `cancelAll`
  private final class CancelAllFrame(cursor: Iterator[AnyRef /* Cancelable | Task[Unit] | CancelableF[Task] */ ])
    extends StackFrame[Unit, Task[Unit]] {

    private[this] val errors = ListBuffer.empty[Throwable]

    def loop(): CancelToken[Task] = {
      var task: Task[Unit] = null

      while ((task eq null) && cursor.hasNext) {
        cursor.next() match {
          case ref: Task[Unit] @unchecked =>
            task = ref
          case ref: CancelableF[Task] @unchecked =>
            task = ref.cancel
          case ref: Cancelable =>
            try {
              ref.cancel()
            } catch {
              case NonFatal(e) =>
                errors += e
            }
          case other =>
            // $COVERAGE-OFF$
            reject(other)
          // $COVERAGE-ON$
        }
      }

      if (task ne null) {
        task.flatMap(this)
      } else {
        errors.toList match {
          case Nil =>
            Task.unit
          case first :: rest =>
            Task.raiseError(Platform.composeErrors(first, rest: _*))
        }
      }
    }

    def apply(a: Unit): Task[Unit] =
      loop()

    def recover(e: Throwable): Task[Unit] = {
      errors += e
      loop()
    }
  }

  private def reject(other: AnyRef): Nothing = {
    // $COVERAGE-OFF$
    throw new IllegalArgumentException(s"Don't know how to cancel: $other")
    // $COVERAGE-ON$
  }
}
