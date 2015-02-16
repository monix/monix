/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive.subjects

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals.{FutureAckExtensions, PromiseCounter}
import monifu.reactive.{Ack, Subject, Subscriber}
import scala.concurrent.Future


/**
 * A `PublishSubject` emits to a subscriber only those items that are
 * emitted by the source subsequent to the time of the subscription
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.PublishSubject.png" />
 *
 * If the source terminates with an error, the `PublishSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.PublishSubject.e.png" />
 */
final class PublishSubject[T] extends Subject[T,T] {
  private[this] val lock = new AnyRef
  private[this] var isCompleted = false
  private[this] var errorThrown: Throwable = null
  @volatile private[this] var subscriptions = Array.empty[Subscriber[T]]

  def subscribeFn(subscriber: Subscriber[T]): Unit =
    lock.synchronized {
      if (!isCompleted)
        subscriptions = createSubscription(subscriptions, subscriber)
      else if (errorThrown ne null)
        subscriber.observer.onError(errorThrown)
      else
        subscriber.observer.onComplete()
    }

  def onNext(elem: T): Future[Ack] = {
    if (!isCompleted) {
      val observers = subscriptions
      if (observers.nonEmpty)
        streamToMany(observers, elem)
      else
        Continue
    }
    else
      Cancel
  }

  def onError(ex: Throwable) =
    lock.synchronized {
      if (!isCompleted) {
        isCompleted = true
        errorThrown = ex

        var idx = 0
        while (idx < subscriptions.length) {
          subscriptions(idx).observer.onError(ex)
          idx += 1
        }
      }
    }

  def onComplete() =
    lock.synchronized {
      if (!isCompleted) {
        isCompleted = true

        var idx = 0
        while (idx < subscriptions.length) {
          subscriptions(idx).observer.onComplete()
          idx += 1
        }
      }
    }

  private[this] def streamToMany(array: Array[Subscriber[T]], elem: T): Future[Continue] = {
    val newPromise = PromiseCounter[Continue](Continue, array.length)
    val length = array.length
    var idx = 0

    while (idx < length) {
      val subscriber = array(idx)
      implicit val s = subscriber.scheduler
      val obs = subscriber.observer

      obs.onNext(elem).onCompleteNow {
        case Continue.IsSuccess =>
          newPromise.countdown()
        case _ =>
          removeSubscription(subscriber)
          newPromise.countdown()
      }

      idx += 1
    }

    newPromise.future
  }

  private[this] def removeSubscription(subscriber: Subscriber[T]): Unit =
    lock.synchronized {
      subscriptions = subscriptions.filter(_ != subscriber)
    }

  private[this] def createSubscription(observers: Array[Subscriber[T]], instance: Subscriber[T]): Array[Subscriber[T]] =
    lock.synchronized {
      if (!observers.contains(instance))
        observers :+ instance
      else
        observers
    }
}

object PublishSubject {
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]()
}