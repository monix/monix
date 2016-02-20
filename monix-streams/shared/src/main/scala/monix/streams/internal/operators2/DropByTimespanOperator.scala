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

package monix.streams.internal.operators2

import monix.execution.{Cancelable, Ack}
import monix.execution.Ack.Continue
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[streams] final class DropByTimespanOperator[A](timespan: FiniteDuration)
  extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] with Runnable { self =>
      implicit val scheduler = out.scheduler

      @volatile private[this] var shouldDrop = true
      private[this] val task: Cancelable = scheduler
        .scheduleOnce(timespan.length, timespan.unit, self)

      def onNext(elem: A): Future[Ack] = {
        if (shouldDrop)
          Continue
        else
          out.onNext(elem)
      }

      def onError(ex: Throwable): Unit = {
        task.cancel()
        out.onError(ex)
      }

      def onComplete(): Unit = {
        task.cancel()
        out.onComplete()
      }

      def run(): Unit = {
        shouldDrop = false
      }
    }
}