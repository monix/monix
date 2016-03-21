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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final
class DoWorkOnDownstreamStopOperator[A](callback: => Unit) extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private def execute(): Unit =
        try callback catch { case NonFatal(ex) => out.scheduler.reportFailure(ex) }

      def onNext(elem: A): Future[Ack] = {
        val ack = out.onNext(elem)
        if (ack eq Continue) Continue
        else if (ack eq Stop) {
          execute()
          Stop
        } else {
          ack.onComplete(result => if (result.isFailure || (result.get eq Stop)) execute())
          ack
        }
      }

      def onError(ex: Throwable): Unit = out.onError(ex)
      def onComplete(): Unit = out.onComplete()
    }
}
