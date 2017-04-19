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
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.reactive.Observable
import monix.reactive.exceptions.MultipleSubscribersException
import monix.reactive.observers.Subscriber
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Converts any `Iterator` into an observable */
private[reactive] final class IteratorAsObservable[T](
  iterator: Iterator[T],
  onFinish: Cancelable) extends Observable[T] {

  private[this] val wasSubscribed = Atomic(false)

  def unsafeSubscribeFn(out: Subscriber[T]): Cancelable = {
    if (wasSubscribed.getAndSet(true)) {
      out.onError(MultipleSubscribersException.build("InputStreamObservable"))
      Cancelable.empty
    } else {
      startLoop(out)
    }
  }

  private def startLoop(subscriber: Subscriber[T]): Cancelable = {
    import subscriber.{scheduler => s}
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
      }
      else {
        val cancelable = BooleanCancelable()
        // Starting the synchronous loop
        fastLoop(iterator, subscriber, cancelable, s.executionModel, 0)(s)
        cancelable
      }
    } catch {
      case NonFatal(ex) =>
        // We can only stream onError events if we have a guarantee
        // that no other final events happened, otherwise we could
        // violate the contract.
        if (streamErrors) {
          subscriber.onError(ex)
          Cancelable.empty
        } else {
          triggerCancel(s)
          s.reportFailure(ex)
          Cancelable.empty
        }
    }
  }

  /** Calls the onFinish callback ensuring that it doesn't throw errors,
    * or if it does, log them using our `Scheduler`.
    */
  private def triggerCancel(s: Scheduler): Unit =
    try onFinish.cancel() catch {
      case NonFatal(ex) =>
        s.reportFailure(ex)
    }

  /** In case of an asynchronous boundary, we reschedule the the
    * run-loop on another logical thread. Usage of `onComplete` takes
    * care of that.
    *
    * NOTE: the assumption of this method is that `iter` is
    * NOT empty, so the first call is `next()` and not `hasNext()`.
    */
  private def reschedule(ack: Future[Ack], iter: Iterator[T],
    out: Subscriber[T], c: BooleanCancelable, em: ExecutionModel)
    (implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(next) =>
        if (next == Continue)
          // If fastLoop throws, then it's a contract violation and
          // the only thing we can do is to log it
          try fastLoop(iter, out, c, em, 0) catch {
            case NonFatal(ex) =>
              triggerCancel(s)
              s.reportFailure(ex)
          }
        else {
          // Downstream Stop happened
          triggerCancel(s)
        }
      case Failure(ex) =>
        // The subscriber's `onNext` is not allowed to throw errors
        // because we don't know what to do with it. At this point the
        // behavior is undefined. So if it happens, we log the error
        // and trigger the `onFinish` cancelable.
        triggerCancel(s)
        s.reportFailure(ex)
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
  @tailrec private
  def fastLoop(iter: Iterator[T], out: Subscriber[T], c: BooleanCancelable,
    em: ExecutionModel, syncIndex: Int)(implicit s: Scheduler): Unit = {

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

    // We need to protect against errors, but we only take care of
    // iterator-related exceptions, otherwise we are dealing with a
    // contract violation and we won't take care of that
    try {
      val next = iter.next()
      iteratorHasNext = iter.hasNext
      streamErrors = false
      ack = out.onNext(next)
    } catch {
      case NonFatal(ex) if streamErrors =>
        iteratorTriggeredError = ex
    }

    // Signaling onComplete
    if (!iteratorHasNext) {
      streamErrors = true
      try {
        onFinish.cancel()
        streamErrors = false
        out.onComplete()
      } catch {
        case NonFatal(ex) if streamErrors =>
          out.onError(ex)
      }
    }
    else if (iteratorTriggeredError != null) {
      triggerCancel(s)
      // Signaling error only if the subscription isn't canceled
      if (!c.isCanceled) out.onError(iteratorTriggeredError)
      else s.reportFailure(iteratorTriggeredError)
    }
    else {
      // Logic for collapsing execution loops
      val nextIndex =
        if (ack == Continue) em.nextFrameIndex(syncIndex)
        else if (ack == Stop) -1
        else 0

      if (nextIndex > 0)
        fastLoop(iter, out, c, em, nextIndex)
      else if (nextIndex == 0 && !c.isCanceled)
        reschedule(ack, iter, out, c, em)
      else
        // Downstream Stop happened
        triggerCancel(s)
    }
  }
}
