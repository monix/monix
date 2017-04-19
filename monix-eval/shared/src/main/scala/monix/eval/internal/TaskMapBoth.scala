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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.Ack.Stop
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.atomic.{Atomic, AtomicAny}
import monix.execution.cancelables.StackedCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Cancelable, Scheduler}

import scala.annotation.tailrec

private[monix] object TaskMapBoth {
  /**
    * Implementation for `Task.mapBoth`.
    */
  def apply[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] = {
    /* For signaling the values after the successful completion of both tasks. */
    def sendSignal(mainConn: StackedCancelable, cb: Callback[R], a1: A1, a2: A2)
      (implicit s: Scheduler): Unit = {

      var streamErrors = true
      try {
        val r = f(a1, a2)
        streamErrors = false
        mainConn.pop()
        cb.asyncOnSuccess(r)
      } catch {
        case NonFatal(ex) if streamErrors =>
          // Both tasks completed by this point, so we don't need
          // to worry about the `state` being a `Stop`
          mainConn.pop()
          cb.asyncOnError(ex)
      }
    }

    /* For signaling an error. */
    @tailrec def sendError(mainConn: StackedCancelable, state: AtomicAny[AnyRef], cb: Callback[R], ex: Throwable)
      (implicit s: Scheduler): Unit = {

      // Guarding the contract of the callback, as we cannot send an error
      // if an error has already happened because of the other task
      state.get match {
        case Stop =>
          // We've got nowhere to send the error, so report it
          s.reportFailure(ex)
        case other =>
          if (!state.compareAndSet(other, Stop))
            sendError(mainConn, state, cb, ex)(s) // retry
          else {
            mainConn.pop().cancel()
            cb.asyncOnError(ex)(s)
          }
      }
    }

    // The resulting task will be executed asynchronously
    Task.unsafeCreate { (context, cb) =>
      // Initial asynchronous boundary
      context.scheduler.executeTrampolined { () =>
        implicit val s = context.scheduler
        val mainConn = context.connection
        // for synchronizing the results
        val state = Atomic.withPadding(null: AnyRef, LeftRight128)
        val task1 = StackedCancelable()
        val context1 = context.copy(connection = task1)
        val task2 = StackedCancelable()
        val context2 = context.copy(connection = task2)
        mainConn push Cancelable.collection(Array(task1, task2))

        // Light asynchronous boundary; with most scheduler implementations
        // it will not fork a new (logical) thread!
        Task.unsafeStartTrampolined(fa1, context1, new Callback[A1] {
          @tailrec def onSuccess(a1: A1): Unit =
            state.get match {
              case null => // null means this is the first task to complete
                if (!state.compareAndSet(null, Left(a1))) onSuccess(a1)
              case ref@Right(a2) => // the other task completed, so we can send
                sendSignal(mainConn, cb, a1, a2.asInstanceOf[A2])(s)
              case Stop => // the other task triggered an error
                () // do nothing
              case s@Left(_) =>
                // This task has triggered multiple onSuccess calls
                // violating the protocol. Should never happen.
                onError(new IllegalStateException(s.toString))
            }

          def onError(ex: Throwable): Unit =
            sendError(mainConn, state, cb, ex)(s)
        })

        // Light asynchronous boundary
        Task.unsafeStartTrampolined(fa2, context2, new Callback[A2] {
          @tailrec def onSuccess(a2: A2): Unit =
            state.get match {
              case null => // null means this is the first task to complete
                if (!state.compareAndSet(null, Right(a2))) onSuccess(a2)
              case ref@Left(a1) => // the other task completed, so we can send
                sendSignal(mainConn, cb, a1.asInstanceOf[A1], a2)(s)
              case Stop => // the other task triggered an error
                () // do nothing
              case s@Right(_) =>
                // This task has triggered multiple onSuccess calls
                // violating the protocol. Should never happen.
                onError(new IllegalStateException(s.toString))
            }

          def onError(ex: Throwable): Unit =
            sendError(mainConn, state, cb, ex)(s)
        })
      }
    }
  }
}
