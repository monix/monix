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

package monix.reactive.internal.builders

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.atomic.Atomic
import monix.execution.cancelables.BooleanCancelable
import monix.execution.compat.internal.toSeq
import monix.execution.exceptions.APIContractViolationException
import monix.execution.{ Ack, Cancelable, ExecutionModel, Scheduler }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

private[reactive] final class BufferedIteratorAsObservable[A](iterator: Iterator[A], bufferSize: Int)
  extends Observable[Seq[A]] {
  require(bufferSize > 0, "bufferSize must be strictly positive")

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[Seq[A]]): Cancelable = {
    if (wasSubscribed.getAndSet(true)) {
      out.onError(APIContractViolationException("InputStreamObservable"))
      Cancelable.empty
    } else {
      startLoop(out)
    }
  }

  private def startLoop(subscriber: Subscriber[Seq[A]]): Cancelable = {
    import subscriber.{ scheduler => s }
    // Protect against contract violations - we are only allowed to
    // call onError if no other terminal event has been called.
    var streamErrors = true
    try {
      val iteratorHasNext = iterator.hasNext
      streamErrors = false
      // Short-circuiting empty iterators, as there's no reason to
      // start the streaming if we have no elements
      if (!iteratorHasNext) {
        subscriber.onComplete()
        Cancelable.empty
      } else {
        // Starting the synchronous loop
        val cancelable = BooleanCancelable()
        fastLoop(iterator, subscriber, cancelable, s.executionModel, 0)(s)
        cancelable
      }
    } catch {
      case ex if NonFatal(ex) =>
        // We can only stream onError events if we have a guarantee
        // that no other final events happened, otherwise we could
        // violate the contract.
        if (streamErrors) {
          subscriber.onError(ex)
          Cancelable.empty
        } else {
          s.reportFailure(ex)
          Cancelable.empty
        }
    }
  }

  /** In case of an asynchronous boundary, we reschedule the the
    * run-loop on another logical thread. Usage of `onComplete` takes
    * care of that.
    *
    * NOTE: the assumption of this method is that `iter` is
    * NOT empty, so the first call is `next()` and not `hasNext()`.
    */
  private def reschedule(
    ack: Future[Ack],
    iter: Iterator[A],
    out: Subscriber[Seq[A]],
    c: BooleanCancelable,
    em: ExecutionModel
  )(implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(next) =>
        if (next == Continue)
          // If fastLoop throws, then it's a contract violation and
          // the only thing we can do is to log it
          try {
            fastLoop(iter, out, c, em, 0)
          } catch {
            case ex if NonFatal(ex) =>
              // Protocol violation
              s.reportFailure(ex)
          }
      case Failure(ex) =>
        out.onError(ex)
    }
  }

  /** The `fastLoop` is a tail-recursive function that goes through the
    * elements of our iterator, one by one, and tries to push them
    * synchronously, for as long as the `ExecutionModel` permits.
    *
    * After it encounters an asynchronous boundary (i.e. an
    * uncompleted `Future` returned by `onNext`), then we
    * [[reschedule]] the loop on another logical thread.
    *
    * NOTE: the assumption of this method is that `iter` is
    * NOT empty, so the first call is `next()` and not `hasNext()`.
    */
  @tailrec private def fastLoop(
    iter: Iterator[A],
    out: Subscriber[Seq[A]],
    c: BooleanCancelable,
    em: ExecutionModel,
    syncIndex: Int
  )(implicit s: Scheduler): Unit = {
    // The result of onNext calls, on which we must do back-pressure
    var ack: Future[Ack] = Continue
    // We do not want to catch errors from our interaction with our
    // observer, since SafeObserver should take care of than, hence we
    // must only catch and stream errors related to the interactions
    // with the iterator
    var streamErrors = true
    // True in case our iterator is seen to be empty and we must
    // signal onComplete
    var iteratorHasNext = true
    // non-null in case we caught an iterator related error and we
    // must signal onError
    var iteratorTriggeredError: Throwable = null

    val buffer = new Array[AnyRef](bufferSize)
    var size = 0

    try {
      while (iteratorHasNext && size < bufferSize) {
        val next = iter.next()
        buffer(size) = next.asInstanceOf[AnyRef]
        size += 1
        iteratorHasNext = iter.hasNext
      }

      ack =
        if (size == bufferSize) out.onNext(toSeq(buffer))
        else out.onNext(toSeq(buffer.take(size)))

    } catch {
      case NonFatal(ex) if streamErrors =>
        iteratorTriggeredError = ex
    }

    // Signaling onComplete
    if (iteratorTriggeredError != null) {
      // Signaling error only if the subscription isn't canceled
      if (!c.isCanceled) out.onError(iteratorTriggeredError)
      else s.reportFailure(iteratorTriggeredError)
    } else if (!iteratorHasNext) {
      streamErrors = true
      // Iterator has finished
      out.onComplete()
    } else {
      // Logic for collapsing execution loops
      val nextIndex =
        if (ack == Continue) em.nextFrameIndex(syncIndex + (size - 1))
        else if (ack == Stop) -1
        else 0

      if (nextIndex > 0)
        fastLoop(iter, out, c, em, nextIndex)
      else if (nextIndex == 0 && !c.isCanceled)
        reschedule(ack, iter, out, c, em)
    }
  }
}
