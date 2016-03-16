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

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.BooleanCancelable
import monix.execution.{Cancelable, Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Success, Failure}

/** Generates ranges */
private[reactive] final class RangeObservable(from: Long, until: Long, step: Long = 1)
  extends Observable[Long] {

  require(step != 0, "step != 0")

  def unsafeSubscribeFn(subscriber: Subscriber[Long]): Cancelable = {
    val s = subscriber.scheduler
    if (!isInRange(from, until, step)) {
      subscriber.onComplete()
      Cancelable.empty
    } else {
      val cancelable = BooleanCancelable()
      loop(cancelable, subscriber, s.batchedExecutionModulus, from, 0)(s)
      cancelable
    }
  }

  @tailrec
  private def loop(c: BooleanCancelable, downstream: Subscriber[Long],
    modulus: Int, from: Long, syncIndex: Int)
    (implicit s: Scheduler): Unit = {

    val ack = downstream.onNext(from)
    val nextFrom = from+step

    if (!isInRange(nextFrom, until, step))
      downstream.onComplete()
    else {
      val nextIndex =
        if (ack == Continue) (syncIndex + 1) & modulus
        else if (ack == Cancel) -1
        else 0

      if (nextIndex > 0)
        loop(c, downstream, modulus, nextFrom, nextIndex)
      else if (nextIndex == 0 && !c.isCanceled)
        asyncBoundary(c, ack, downstream, modulus, nextFrom)
    }
  }

  private def asyncBoundary(
    cancelable: BooleanCancelable,
    ack: Future[Ack],
    downstream: Subscriber[Long],
    modulus: Int,
    from: Long)
    (implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(success) =>
        if (success == Continue)
          loop(cancelable, downstream, modulus, from, 0)
      case Failure(ex) =>
        s.reportFailure(ex)
    }
  }

  private def isInRange(x: Long, until: Long, step: Long): Boolean = {
    (step > 0 && x < until) || (step < 0 && x > until)
  }
}


