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

package monix.execution

import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.Platform
import monix.execution.internal.exceptions.matchError
import monix.execution.atomic.{AtomicAny, PaddingStrategy}

import scala.annotation.tailrec
import scala.collection.immutable.LongMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/**
  * `CancelablePromise` is a [[scala.concurrent.Promise]] implementation that
  * allows listeners to unsubscribe from receiving future results.
  *
  * It does so by:
  *
  *  - adding a low-level [[subscribe]] method, that allows for callbacks
  *    to be subscribed
  *  - returning [[CancelableFuture]] in its [[future]] method implementation,
  *    allowing created future objects to unsubscribe (being the high-level
  *    [[subscribe]] that should be preferred for most usage)
  *
  * Being able to unsubscribe listeners helps with avoiding memory leaks
  * in case of listeners or futures that are being timed-out due to
  * promises that take a long time to complete.
  *
  * @see [[subscribe]]
  * @see [[future]]
  */
sealed abstract class CancelablePromise[A] extends Promise[A] {
  /**
    * Returns a future that can unsubscribe from this promise's
    * notifications via cancelation.
    *
    * {{{
    *   val promise = CancelablePromise[Int]()
    *
    *   val future1 = promise.future
    *   val future2 = promise.future
    *
    *   for (r <- future1) println(s"Future1 completed with: $$r")
    *   for (r <- future2) println(s"Future2 completed with: $$r")
    *
    *   // Unsubscribing from the future notification, but only for future1
    *   future1.cancel()
    *
    *   // Completing our promise
    *   promise.success(99)
    *
    *   //=> Future2 completed with: 99
    * }}}
    *
    * Note that in the above example `future1` becomes non-terminating
    * after cancellation. By unsubscribing its listener, it will never
    * complete.
    *
    * This helps with avoiding memory leaks for futures that are being
    * timed-out due to promises that take a long time to complete.
    */
  def future: CancelableFuture[A]

  /** Low-level subscription method that registers a callback to be called
    * when this promise will complete.
    *
    * {{{
    *   val promise = CancelablePromise[Int]()
    *
    *   def subscribe(n: Int): Cancelable =
    *     promise.subscribe {
    *       case Success(str) =>
    *         println(s"Callback ($$n) completed with: $$str")
    *       case Failure(e) =>
    *         println(s"Callback ($$n) completed with: $$e")
    *     }
    *
    *   val token1 = subscribe(1)
    *   val token2 = subscribe(2)
    *
    *   // Unsubscribing from the future notification
    *   token1.cancel()
    *
    *   // Completing our promise
    *   promise.success(99)
    *
    *   //=> Callback (2) completed with: 99
    * }}}
    *
    * '''UNSAFE PROTOCOL:''' the implementation does not protect
    * against stack-overflow exceptions. There's no point in doing it
    * for such low level methods, because this is useful as middleware
    * and different implementations will have different ways to deal
    * with stack safety (e.g. `monix.eval.Task`).
    *
    * @param cb is a callback that will be called when the promise
    *        completes with a result, assuming that the returned
    *        cancelable token isn't canceled
    *
    * @return a cancelable token that can be used to unsubscribe the
    *         given callback, in order to prevent memory leaks, at
    *         which point the callback will never be called (if it
    *         wasn't called already)
    */
  def subscribe(cb: Try[A] => Unit): Cancelable
}

object CancelablePromise {
  /**
    * Builds an empty [[CancelablePromise]] object.
    *
    * @tparam A the type of the value in the promise
    *
    * @param ps is a configurable
    *        [[monix.execution.atomic.PaddingStrategy PaddingStrategy]]
    *        to avoid the false sharing problem
    *
    * @return the newly created promise object
    */
  def apply[A](ps: PaddingStrategy = NoPadding): CancelablePromise[A] =
    new Async[A](ps)

  /** Creates an already completed [[CancelablePromise]] with the specified
    * result or exception.
    *
    * @param value is the [[scala.util.Try]] result to signal to subscribers
    * @return the newly created promise object
    */
  def fromTry[A](value: Try[A]): CancelablePromise[A] =
    new Completed[A](value)

  /** Creates a [[CancelablePromise]] that's already completed with the
    * given successful result.
    *
    * @param value is the successful result to signal to subscribers
    * @return the newly created promise object
    */
  def successful[A](value: A): CancelablePromise[A] =
    fromTry(Success(value))

  /** Creates a [[CancelablePromise]] that's already completed in error.
    *
    * @param e is the error to signal to subscribers
    * @return the newly created promise object
    */
  def failed[A](e: Throwable): CancelablePromise[A] =
    fromTry(Failure(e))

  /**
    * Implements already completed `CancelablePromise` references.
    */
  private final class Completed[A](value: Try[A]) extends CancelablePromise[A] {
    def future: CancelableFuture[A] =
      CancelableFuture.fromTry(value)

    def subscribe(cb: Try[A] => Unit): Cancelable = {
      cb(value)
      Cancelable.empty
    }

    def isCompleted: Boolean = true
    def tryComplete(result: Try[A]): Boolean = false
  }

  /**
    * Actual implementation for `CancelablePromise`.
    */
  private final class Async[A](ps: PaddingStrategy) extends CancelablePromise[A] { self =>
    // States:
    //  - Try[A]: completed with a result
    //  - MapQueue: listeners queue
    private[this] val state = AtomicAny.withPadding[AnyRef](emptyMapQueue, ps)

    override def subscribe(cb: Try[A] => Unit): Cancelable =
      unsafeSubscribe(cb)

    override def isCompleted: Boolean =
      state.get().isInstanceOf[Try[_]]

    override def future: CancelableFuture[A] =
      state.get() match {
        case ref: Try[A] @unchecked =>
          CancelableFuture.fromTry(ref)
        case queue: MapQueue[AnyRef] @unchecked =>
          // Optimization over straight usage of `unsafeSubscribe`,
          // to avoid an extra `ref.get`
          val p = Promise[A]()
          val (id, update) = queue.enqueue(p)
          val cancelable =
            if (!state.compareAndSet(queue, update)) unsafeSubscribe(p)
            else new IdCancelable(id)
          CancelableFuture(p.future, cancelable)
        case other =>
          // $COVERAGE-OFF$
          matchError(other)
          // $COVERAGE-ON$
      }

    @tailrec
    override def tryComplete(result: Try[A]): Boolean = {
      state.get() match {
        case queue: MapQueue[AnyRef] @unchecked =>
          if (!state.compareAndSet(queue, result)) {
            // Failed, retry...
            tryComplete(result)
          } else if (queue eq emptyMapQueue) true
          else {
            var errors: ListBuffer[Throwable] = null
            val cursor = queue.iterator

            while (cursor.hasNext) {
              val cb = cursor.next()
              try {
                call(cb, result)
              } catch {
                // Aggregating errors, because we want to throw them all
                // as a composite and to prevent listeners from spoiling
                // the fun for other listeners by throwing errors
                case e if NonFatal(e) =>
                  if (errors eq null) errors = ListBuffer.empty
                  errors += e
              }
            }
            if (errors ne null) {
              // Throws all errors as a composite
              val x :: xs = errors.toList
              throw Platform.composeErrors(x, xs: _*)
            }
            true
          }
        case _ =>
          false
      }
    }

    private def call(cb: AnyRef, result: Try[A]): Unit =
      cb match {
        case f: (Try[A] => Unit) @unchecked =>
          f(result)
        case p: Promise[A] @unchecked =>
          p.complete(result)
          ()
        case other =>
          // $COVERAGE-OFF$
          matchError(other)
          // $COVERAGE-ON$
      }

    @tailrec def unsafeSubscribe(cb: AnyRef): Cancelable =
      state.get() match {
        case ref: Try[A] @unchecked =>
          call(cb, ref)
          Cancelable.empty

        case queue: MapQueue[Any] @unchecked =>
          val (id, update) = queue.enqueue(cb)
          if (!state.compareAndSet(queue, update)) unsafeSubscribe(cb)
          else new IdCancelable(id)

        case other =>
          // $COVERAGE-OFF$
          matchError(other)
          // $COVERAGE-ON$
      }

    private final class IdCancelable(id: Long) extends Cancelable {
      @tailrec def cancel(): Unit =
        state.get() match {
          case queue: MapQueue[_] =>
            if (!state.compareAndSet(queue, queue.dequeue(id)))
              cancel()
          case _ =>
            ()
        }
    }
  }

  private final case class MapQueue[+A](map: LongMap[A], nextId: Long) extends Iterable[A] {

    def enqueue[AA >: A](elem: AA): (Long, MapQueue[AA]) =
      (nextId, MapQueue(map.updated(nextId, elem), nextId + 1))

    def dequeue(id: Long): MapQueue[A] =
      MapQueue(map - id, nextId)

    def iterator: Iterator[A] =
      map.valuesIterator
  }

  private[this] val emptyMapQueue: MapQueue[Nothing] =
    MapQueue(LongMap.empty, 0)
}
