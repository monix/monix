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

package monix.streams.broadcast

import monix.execution.Scheduler
import monix.execution.internal.Platform
import monix.streams.observers.Subscriber
import monix.streams.{Observable, Observer}
import org.reactivestreams.{Processor => RProcessor, Subscriber => RSubscriber, Subscription}
import scala.language.reflectiveCalls

/** A `Processor` is a sort of bridge or proxy that acts both as an
  * [[Observer]] and as an [[Observable]] and that must respect the contract of both.
  *
  * Because it is a `Observer`, it can subscribe to an `Observable` and because it is an `Observable`,
  * it can pass through the items it observes by re-emitting them and it can also emit new items.
  *
  * Useful to build multicast Observables or reusable processing pipelines.
  */
trait Processor[I, +O] extends Observable[O] with Observer[I] {
  override def toReactive[U >: O](implicit s: Scheduler): RProcessor[I, U] =
    Processor.toReactiveProcessor(this, Platform.recommendedBatchSize)

  def toReactive[U >: O](bufferSize: Int)(implicit s: Scheduler): RProcessor[I, U] =
    Processor.toReactiveProcessor(this, bufferSize)
}

object Processor {
  /** Transforms the source [[Processor]] into a `org.reactivestreams.RProcesor`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveProcessor[I,O](source: Processor[I,O], bufferSize: Int)(implicit s: Scheduler): RProcessor[I,O] = {
    new RProcessor[I,O] {
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
