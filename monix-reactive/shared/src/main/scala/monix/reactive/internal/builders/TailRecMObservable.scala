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

package monix.reactive.internal.builders

import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.AtomicBoolean
import monix.execution.cancelables.{SingleAssignmentCancelable, StackedCancelable}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/** Implementation for `Observable.tailRecM`. */
private[monix] final class TailRecMObservable[A,B](seed: A, f: A => Observable[Either[A,B]])
  extends Observable[B] {

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    implicit val s = out.scheduler
    val connection = StackedCancelable()
    val future = loop(seed, out, connection).flatMap(identity)

    // Purpose of the future is to signal when all streaming
    // has finished, such that we can signal a final event:
    // this is the only place where `onComplete` and `onError`
    // are called, not in `loop`
    future.syncOnComplete {
      case Success(_) => out.onComplete()
      case Failure(ex) => out.onError(ex)
    }

    connection
  }

  /** A tail-recursive loop that pushes events into our `Subscriber`,
    * respecting its contract.
    *
    * NOTE: we return `Future[Future[Ack]]` in order for the implementation
    * to use constant memory, because we need to be able to discard whatever
    * we stored in memory for our last `onNext`. If we returned `Future[Ack]`
    * instead, then it would be impossible for the implementation to use
    * constant memory.
    */
  private def loop(seed: A, out: Subscriber[B], conn: StackedCancelable): Future[Future[Ack]] = {
    val callback = Promise[Future[Ack]]()

    // Protects against user code
    var streamErrors = true
    try {
      val next = f(seed)
      streamErrors = false
      val c = SingleAssignmentCancelable()
      conn.push(c)

      c := next.unsafeSubscribeFn(new Subscriber[Either[A, B]] {
        override def scheduler = s
        private[this] implicit val s = out.scheduler

        // We need to protect `conn.pop()` - unfortunately
        // there has to be an order for `push` and `pop` on
        // that `StackedCancelable`, so we need to `pop` before
        // any other `push` can happen. Because of this and
        // because its usage in `sendNextB` is concurrent with
        // `onComplete`, unfortunately we need it. Hopefully
        // on Java 8 it is fast enough as we are doing a
        // `getAndSet` instead of `compareAndSet`.
        private[this] val isActive = AtomicBoolean(true)

        // Stores the last acknowledgement we received from
        // `out.onNext` - to be used for applying back-pressure
        // where needed.
        private[this] var lastAck: Future[Ack] = Continue

        // Pushes a final result (for this iteration of `loop` at least),
        // but before that it pops the current cancelable from our
        // `StackedCancelable`, for freeing memory
        private def tryFinish(ack: Future[Ack]): Boolean =
          if (!isActive.getAndSet(false)) false else {
            conn.pop()
            callback.success(ack)
            true
          }

        // We got a B, so this function sends it downstream
        private def sendNextB(b: B): Future[Ack] = {
          lastAck = out.onNext(b)
          lastAck.syncOnComplete {
            case Success(value) =>
              if (value == Stop) tryFinish(value)
            case Failure(ex) =>
              if (!tryFinish(Future.failed(ex)))
                s.reportFailure(ex)
          }
          lastAck
        }

        // We got an A, so this means we need to subscribe
        // to another observable
        private def continueWithA(a: A): Future[Ack] = {
          if (conn.isCanceled) {
            lastAck = Stop
            lastAck
          } else {
            // Forcing async boundary - because of Future's optimizations,
            // doing flatMaps like this is fairly efficient, as the
            // callbacks get executed on an "internal executor"
            lastAck = Future(loop(a, out, conn)).flatMap(_.flatMap(x => x))
            lastAck
          }
        }

        def onNext(elem: Either[A, B]): Future[Ack] =
          elem match {
            case Right(b) => sendNextB(b)
            case Left(a) => continueWithA(a)
          }

        def onComplete(): Unit =
          tryFinish(lastAck)

        def onError(ex: Throwable): Unit = {
          // If back-pressure is required for whatever reason,
          // then we need to wait for `lastAck` before signaling
          // an error, hence we wait. Note that `onError` is still
          // tail-recursive, just as `onComplete`
          val f = lastAck.flatMap {
            case Continue => Future.failed(ex)
            case Stop =>
              s.reportFailure(ex)
              Stop
          }

          if (!tryFinish(f))
            s.reportFailure(ex)
        }
      })
    } catch {
      case NonFatal(ex) =>
        if (streamErrors) callback.success(Future.failed(ex))
        else out.scheduler.reportFailure(ex)
    }

    callback.future
  }
}
