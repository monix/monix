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
import monifu.reactive.Observable
import scala.annotation.tailrec

private[reactive] object range {
  /**
   * Creates an Observable that emits items in the given range.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/range.png" />
   *
   * @param from the range start
   * @param until the range end
   * @param step increment step, either positive or negative
   */
  def apply(from: Long, until: Long, step: Long = 1): Observable[Long] = {
    require(step != 0, "step must be a number different from zero")

    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      def scheduleLoop(from: Long, until: Long, step: Long): Unit =
        s.execute(new Runnable {
          private[this] def isInRange(x: Long): Boolean = {
            (step > 0 && x < until) || (step < 0 && x > until)
          }

          def reschedule(from: Long, until: Long, step: Long): Unit =
            fastLoop(from, until, step)

          @tailrec
          def fastLoop(from: Long, until: Long, step: Long): Unit =
            if (isInRange(from))
              o.onNext(from) match {
                case sync if sync.isCompleted =>
                  sync.value.get match {
                    case Continue.IsSuccess =>
                      fastLoop(from + step, until, step)
                    case _ =>
                      () // do nothing else
                  }

                case async =>
                  async.onComplete {
                    case Continue.IsSuccess =>
                      reschedule(from + step, until, step)
                    case _ =>
                      () // do nothing else
                  }
              }
            else
              o.onComplete()

          def run(): Unit = {
            fastLoop(from, until, step)
          }
        })

      scheduleLoop(from, until, step)
    }
  }
}
