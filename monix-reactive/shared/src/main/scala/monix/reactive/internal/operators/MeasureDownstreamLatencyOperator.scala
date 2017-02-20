/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.Atomic
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final
class MeasureDownstreamLatencyOperator[A](cb: Long => Unit) extends Operator[A,A]  {
  override def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      private[this] val isActive = Atomic(true)
      implicit val scheduler = out.scheduler

      private def signalDuration(startedAt: Long)(ack: Future[Ack]): Future[Ack] = {
        val duration = scheduler.currentTimeMillis() - startedAt
        try {
          cb(duration)
          ack
        } catch {
          case NonFatal(ex) =>
            onError(ex)
            Stop
        }
      }

      def onNext(elem: A): Future[Ack] = {
        val startedAt = scheduler.currentTimeMillis()
        val ack = out.onNext(elem)

        ack match {
          case Continue | Stop =>
            val duration = scheduler.currentTimeMillis() - startedAt
            signalDuration(duration)(ack)
          case async =>
            async.flatMap(signalDuration(startedAt))
        }
      }

      def onError(ex: Throwable): Unit = {
        if (isActive.getAndSet(false))
          out.onError(ex)
        else
          scheduler.reportFailure(ex)
      }

      def onComplete(): Unit = {
        if (isActive.getAndSet(false)) out.onComplete()
      }
    }
}
