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

package monix.streams.internal.builders

import monix.execution.Ack.{Continue, Cancel}
import monix.execution.{Ack, Scheduler}
import monix.streams.Observable
import monix.streams.observers.Subscriber
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Success, Failure}
import scala.util.control.NonFatal

/** Converts any `Iterator` into an observable */
private[streams] final
class IteratorAsObservable[T](iterator: Iterator[T]) extends Observable[T] {

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit = {
    import subscriber.{scheduler => s}
    var streamError = true

    try {
      val isEmpty = iterator.isEmpty
      streamError = false

      if (isEmpty)
        subscriber.onComplete()
      else
        fastLoop(iterator, subscriber, s.batchedExecutionModulus, 0)(s)
    } catch {
      case NonFatal(ex) if streamError =>
        subscriber.onError(ex)
    }
  }

  private def reschedule(ack: Future[Ack], iter: Iterator[T],
    out: Subscriber[T], s: Scheduler, modulus: Int): Unit = {

    ack.onComplete {
      case Success(next) =>
        if (next == Continue)
          fastLoop(iter, out, modulus, 0)(s)
      case Failure(ex) =>
        s.reportFailure(ex)
    }(s)
  }

  @tailrec
  private def fastLoop(iter: Iterator[T], out: Subscriber[T],
    modulus: Int, syncIndex: Int)(implicit s: Scheduler): Unit = {

    // the result of onNext calls, on which we must do back-pressure
    var ack: Future[Ack] = Continue
    // we do not want to catch errors from our interaction with our observer,
    // since SafeObserver should take care of than, hence we must only
    // catch and stream errors related to the interactions with the iterator
    var streamError = true
    // true in case our iterator is seen to be empty and we must signal onComplete
    var iteratorIsDone = false
    // non-null in case we caught an iterator related error and we must signal onError
    var iteratorTriggeredError: Throwable = null

    try {
      if (iter.hasNext) {
        val next = iter.next()
        streamError = false
        ack = out.onNext(next)
      } else {
        iteratorIsDone = true
      }
    } catch {
      case NonFatal(ex) if streamError =>
        iteratorTriggeredError = ex
    }

    if (iteratorIsDone)
      out.onComplete()
    else if (iteratorTriggeredError != null)
      out.onError(iteratorTriggeredError)
    else {
      val nextIndex =
        if (ack == Continue) (syncIndex + 1) & modulus
        else if (ack == Cancel) -1
        else 0

      if (nextIndex > 0)
        fastLoop(iter, out, modulus, nextIndex)
      else if (nextIndex == 0)
        reschedule(ack, iter, out, s, modulus)
    }
  }
}
