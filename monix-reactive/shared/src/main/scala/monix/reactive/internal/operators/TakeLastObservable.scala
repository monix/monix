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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.Observable
import monix.reactive.observables.ChainedObservable
import monix.reactive.observers.Subscriber
import scala.collection.mutable

private[reactive] final class TakeLastObservable[A](source: Observable[A], n: Int)
  extends ChainedObservable[A] {

  override def unsafeSubscribeFn(conn: AssignableCancelable.Multi, out: Subscriber[A]): Unit = {
    ChainedObservable.subscribe(
      source,
      conn,
      new Subscriber[A] {
        implicit val scheduler: Scheduler = out.scheduler
        private[this] val queue = mutable.Queue.empty[A]
        private[this] var queued = 0

        def onNext(elem: A): Ack = {
          queue.enqueue(elem)
          if (queued < n)
            queued += 1
          else
            queue.dequeue()
          Continue
        }

        def onComplete(): Unit = {
          val other = Observable.fromIteratorUnsafe(queue.iterator)
          ChainedObservable.subscribe(other, conn, out)
        }

        def onError(ex: Throwable): Unit = {
          out.onError(ex)
        }
      }
    )
  }
}
