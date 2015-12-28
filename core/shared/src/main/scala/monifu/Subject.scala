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
 
package monifu

import monifu.concurrent.Scheduler
import monifu.observables.LiftOperators2
import org.reactivestreams.{Processor, Subscriber => RSubscriber, Subscription}
import scala.concurrent.Future

/** A `Subject` is a sort of bridge or proxy that acts both as an
  * [[Observer]] and as an [[Observable]] and that must respect the contract of both.
  *
  * Because it is a `Observer`, it can subscribe to an `Observable` and because it is an `Observable`,
  * it can pass through the items it observes by re-emitting them and it can also emit new items.
  *
  * Useful to build multicast Observables or reusable processing pipelines.
  */
trait Subject[I, +T] extends Observable[T] with Observer[I]
  with LiftOperators2[I, T, Subject] { self =>

  protected def liftToSelf[U](f: Observable[T] => Observable[U]): Subject[I, U] =
    new Subject[I,U] {
      def onNext(elem: I): Future[Ack] = self.onNext(elem)
      def onError(ex: Throwable): Unit = self.onError(ex)
      def onComplete(): Unit = self.onComplete()

      private[this] val lifted = f(self)
      def unsafeSubscribeFn(subscriber: Subscriber[U]): Unit =
        lifted.unsafeSubscribeFn(subscriber)
    }

  override def toReactive[U >: T](implicit s: Scheduler): Processor[I, U] =
    Subject.toReactiveProcessor(self, s.env.batchSize)

  def toReactive[U >: T](bufferSize: Int)(implicit s: Scheduler): Processor[I, U] =
    Subject.toReactiveProcessor(self, bufferSize)
}

object Subject {
  /** Transforms the source [[Subject]] into a `org.reactivestreams.Processor`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveProcessor[I,O](source: Subject[I,O], bufferSize: Int)(implicit s: Scheduler): Processor[I,O] = {
    new Processor[I,O] {
      private[this] val subscriber =
        Subscriber(source, s).toReactive(bufferSize)

      def subscribe(subscriber: RSubscriber[_ >: O]): Unit = {
        source.unsafeSubscribeFn(Subscriber.fromReactiveSubscriber(subscriber))
      }

      def onSubscribe(s: Subscription): Unit = {
        subscriber.onSubscribe(s)
      }

      def onNext(t: I): Unit = {
        subscriber.onNext(t)
      }

      def onError(t: Throwable): Unit = {
        subscriber.onError(t)
      }

      def onComplete(): Unit = {
        subscriber.onComplete()
      }
    }
  }
}
