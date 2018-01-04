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

package monix.reactive.subjects

import monix.execution.Scheduler
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}
import org.reactivestreams.{Subscription, Processor => RProcessor, Subscriber => RSubscriber}

/** A `Subject` is a sort of bridge or proxy that acts both as an
  * [[Observer]] and as an [[Observable]] and that must respect
  * the contract of both.
  *
  * Because it is a `Observer`, it can subscribe to an `Observable`
  * and because it is an `Observable`, it can pass through the items
  * it observes by re-emitting them and it can also emit new items.
  *
  * Useful to build multicast Observables or reusable processing pipelines.
  */
abstract class Subject[I, +O] extends Observable[O] with Observer[I] { self =>
  /** Returns the number of connected subscribers.

    * Note this might be an expensive operation.
    *
    * Should be used for debugging purposes or for collecting
    * metrics, but don't overuse because the accessed state is
    * a volatile read, and counting subscribers might have linear
    * complexity, depending on the underlying data-structure.
    */
  def size: Int

  final def toReactiveProcessor[U >: O](implicit s: Scheduler): RProcessor[I, U] =
    toReactiveProcessor(s.executionModel.recommendedBatchSize)
  final def toReactiveProcessor[U >: O](bufferSize: Int)(implicit s: Scheduler): RProcessor[I, U] =
    Subject.toReactiveProcessor(this, bufferSize)
}

object Subject {
  /** Transforms the source [[Subject]] into a `org.reactivestreams.Procesor`
    * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
    * specification.
    *
    * @param bufferSize a strictly positive number, representing the size
    *                   of the buffer used and the number of elements requested
    *                   on each cycle when communicating demand, compliant with
    *                   the reactive streams specification
    */
  def toReactiveProcessor[I,O](source: Subject[I,O], bufferSize: Int)(implicit s: Scheduler): RProcessor[I,O] = {
    new RProcessor[I,O] {
      private[this] val subscriber: RSubscriber[I] =
        Subscriber(source, s).toReactive(bufferSize)

      def subscribe(subscriber: RSubscriber[_ >: O]): Unit = {
        val sub = SingleAssignmentCancelable()
        sub := source.unsafeSubscribeFn(Subscriber.fromReactiveSubscriber(subscriber, sub))
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
