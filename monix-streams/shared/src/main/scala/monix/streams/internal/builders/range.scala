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

import monix.execution.internal.Platform
import monix.streams.{Observable, Ack}
import monix.streams.Ack.Continue
import scala.annotation.tailrec
import scala.util.Failure

private[monix] object range {
  /**
   * Implementation for [[Observable.range]]
   */
  def apply(from: Long, until: Long, step: Long = 1): Observable[Long] = {
    require(step != 0, "step must be a number different from zero")

    Observable.unsafeCreate { subscriber =>
      import subscriber.{scheduler => s}

      def scheduleLoop(from: Long, until: Long, step: Long): Unit =
        s.execute(new Runnable {
          private[this] val modulus = Platform.recommendedBatchSize - 1

          private[this] def isInRange(x: Long): Boolean = {
            (step > 0 && x < until) || (step < 0 && x > until)
          }

          def reschedule(from: Long, until: Long, step: Long): Unit =
            fastLoop(from, until, step, 0)

          @tailrec
          def fastLoop(from: Long, until: Long, step: Long, syncIndex: Int): Unit =
            if (isInRange(from)) {
              val ack = subscriber.onNext(from)
              val nextIndex = if (!ack.isCompleted) 0 else
                (syncIndex + 1) & modulus

              if (nextIndex != 0) {
                if (ack == Continue)
                  fastLoop(from + step, until, step, nextIndex)
                else ack.value.get match {
                  case Continue.AsSuccess =>
                    fastLoop(from + step, until, step, nextIndex)
                  case Failure(ex) =>
                    s.reportFailure(ex)
                  case _ =>
                    () // this was a Cancel, do nothing
                }
              }
              else ack.onComplete {
                case Continue.AsSuccess =>
                  reschedule(from + step, until, step)
                case Failure(ex) =>
                  s.reportFailure(ex)
                case _ =>
                  () // this was a Cancel, do nothing
              }
            }
            else
              subscriber.onComplete()

          def run(): Unit = {
            fastLoop(from, until, step, 0)
          }
        })

      scheduleLoop(from, until, step)
    }
  }
}
