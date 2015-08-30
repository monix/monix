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
 
package monifu.reactive

import language.implicitConversions
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.{SynchronousSubscriber, SynchronousObserver}
import monifu.reactive.streams.{SubscriberAsReactiveSubscriber, SubscriberAsObserver, SynchronousSubscriberAsReactiveSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


/**
 * The Observer from the Rx pattern is the trio of callbacks that
 * get subscribed to an Observable for receiving events.
 *
 * The events received must follow the Rx grammar, which is:
 *      onNext *   (onComplete | onError)?
 *
 * That means an Observer can receive zero or multiple events, the stream
 * ending either in one or zero `onComplete` or `onError` (just one, not both),
 * and after onComplete or onError, a well behaved Observable implementation
 * shouldn't send any more onNext events.
 */
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]

  def onError(ex: Throwable): Unit

  def onComplete(): Unit
}

object Observer {
  /**
   * Given an `org.reactivestreams.Subscriber` as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification, it builds an [[Observer]] instance compliant with the Monifu Rx implementation.
   */
  def from[T](subscriber: RSubscriber[T])(implicit s: Scheduler): Observer[T] = {
    SubscriberAsObserver(subscriber)
  }

  /**
   * Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
   * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification.
   */
  def toSubscriber[T](observer: Observer[T], requestSize: Int = 128)(implicit s: Scheduler): RSubscriber[T] = {
    observer match {
      case sync: SynchronousObserver[_] =>
        val inst = sync.asInstanceOf[SynchronousObserver[T]]
        SynchronousSubscriberAsReactiveSubscriber(SynchronousSubscriber(inst, s), requestSize)
      case async =>
        SubscriberAsReactiveSubscriber(Subscriber(async, s), requestSize)
    }
  }

  /**
   * Implicit conversion from [[Observer]] to `org.reactivestreams.Subscriber`.
   */
  implicit def ObserverIsReactive[T](source: Observer[T])(implicit s: Scheduler): RSubscriber[T] =
    Observer.toSubscriber(source)
}
