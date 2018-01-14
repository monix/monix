/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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
import monix.execution.Ack.Continue
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.collection.mutable
import scala.concurrent.Future

private[reactive] final
class DropLastOperator[A](n: Long) extends Operator[A,A] {
  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var queue = mutable.Queue.empty[A]
      private[this] var length = 0

      override def onNext(elem: A): Future[Ack] = {
        queue.enqueue(elem)
        if (length >= n)
          out.onNext(queue.dequeue())
        else {
          length += 1
          Continue
        }
      }

      def onError(ex: Throwable): Unit = {
        queue = null // GC purposes
        out.onError(ex)
      }

      def onComplete(): Unit = {
        queue = null // GC purposes
        out.onComplete()
      }
    }
}