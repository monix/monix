/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.builders

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observable, Subscriber}
import scala.annotation.tailrec
import scala.util.Failure

private[reactive] object repeat {
  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def apply[T](elems: T*): Observable[T] = {
    if (elems.isEmpty) {
      Observable.empty
    }
    else if (elems.length == 1) {
      Observable.create { subscriber =>
        // first execution is always asynchronous
        subscriber.scheduler
          .execute(new RepeatOneLoop(subscriber, elems.head))
      }
    }
    else {
      Observable.fromIterable(elems).repeat
    }
  }

  private
  final class RepeatOneLoop[T](subscriber: Subscriber[T], elem: T) extends Runnable {
    import subscriber.{scheduler => s}
    private[this] val o = subscriber
    private[this] val modulus = s.env.batchSize - 1

    def run(): Unit = {
      fastLoop(0)
    }

    @tailrec
    def fastLoop(syncIndex: Int): Unit = {
      val ack = o.onNext(elem)
      val nextIndex = if (!ack.isCompleted) 0 else
        (syncIndex + 1) & modulus

      if (nextIndex != 0) {
        if (ack == Continue)
          fastLoop(nextIndex)
        else ack.value.get match {
          case Continue.IsSuccess =>
            fastLoop(nextIndex)
          case Failure(ex) =>
            s.reportFailure(ex)
          case _ =>
            () // this was a Cancel, do nothing
        }
      }
      else ack.onComplete {
        case Continue.IsSuccess =>
          run()
        case Failure(ex) =>
          s.reportFailure(ex)
        case _ =>
          () // this was a Cancel, do nothing
      }
    }
  }
}
