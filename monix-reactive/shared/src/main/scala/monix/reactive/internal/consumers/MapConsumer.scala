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

package monix.reactive.internal.consumers

import monix.execution.Callback
import monix.execution.Scheduler
import monix.execution.cancelables.AssignableCancelable
import scala.util.control.NonFatal
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

/** Implementation for [[monix.reactive.Consumer.map]]. */
private[reactive] final class MapConsumer[In, R, R2](source: Consumer[In, R], f: R => R2) extends Consumer[In, R2] {

  def createSubscriber(cb: Callback[Throwable, R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    val cb1 = new Callback[Throwable, R] {
      def onSuccess(value: R): Unit =
        s.execute(() => {
          var streamErrors = true
          try {
            val r2 = f(value)
            streamErrors = false
            cb.onSuccess(r2)
          } catch {
            case ex if NonFatal(ex) =>
              if (streamErrors) cb.onError(ex)
              else s.reportFailure(ex)
          }
        })

      def onError(ex: Throwable): Unit =
        s.execute(new Runnable {
          def run(): Unit = cb.onError(ex)
        })
    }

    source.createSubscriber(cb1, s)
  }
}
