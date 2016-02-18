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

import monix.execution.Ack.{Cancel, Continue}
import monix.execution.{Ack, Scheduler}
import monix.streams.Observable
import monix.streams.observers.Subscriber
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Success, Failure}

/** Generates ranges */
private[streams] final class RangeObservable(from: Long, until: Long, step: Long = 1)
  extends Observable[Long] {

  require(step != 0, "step != 0")

  def unsafeSubscribeFn(subscriber: Subscriber[Long]): Unit = {
    val s = subscriber.scheduler
    if (!isInRange(from, until, step))
      subscriber.onComplete()
    else
      loop(subscriber, s.batchedExecutionModulus, from, until, step, 0)(s)
  }

  @tailrec
  private def loop(downstream: Subscriber[Long], modulus: Int,
    from: Long, until: Long, step: Long, syncIndex: Int)
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
        loop(downstream, modulus, nextFrom, until, step, nextIndex)
      else if (nextIndex == 0)
        asyncBoundary(ack, downstream, modulus, nextFrom, until, step)
    }
  }

  private def asyncBoundary(
    ack: Future[Ack],
    downstream: Subscriber[Long], modulus: Int,
    from: Long, until: Long, step: Long)
    (implicit s: Scheduler): Unit = {

    ack.onComplete {
      case Success(success) =>
        if (success == Continue)
          loop(downstream, modulus, from, until, step, 0)
      case Failure(ex) =>
        s.reportFailure(ex)
    }
  }

  private def isInRange(x: Long, until: Long, step: Long): Boolean = {
    (step > 0 && x < until) || (step < 0 && x > until)
  }
}


