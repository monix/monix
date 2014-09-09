/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.reactive.subjects

import scala.concurrent.Future
import monifu.reactive.Ack
import monifu.reactive.Ack.{Continue, Cancel}
import monifu.concurrent.Scheduler
import monifu.reactive.{Subject, Observer}
import monifu.reactive.internals.PromiseCounter
import monifu.reactive.internals.FutureAckExtensions
import monifu.concurrent.locks.SpinLock


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
final class PublishSubject[T] private (s: Scheduler) extends Subject[T,T] {
  implicit val scheduler = s
  
  private[this] val lock = SpinLock()
  private[this] var isCompleted = false
  private[this] var errorThrown: Throwable = null
  @volatile private[this] var subscriptions = Array.empty[Observer[T]]

  def subscribeFn(observer: Observer[T]): Unit =
    lock.enter {
      if (!isCompleted)
        subscriptions = createSubscription(subscriptions, observer)
      else if (errorThrown ne null)
        observer.onError(errorThrown)
      else
        observer.onComplete()
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
    lock.enter {
      if (!isCompleted) {
        isCompleted = true
        errorThrown = ex

        var idx = 0
        while (idx < subscriptions.length) {
          subscriptions(idx).onError(ex)
          idx += 1
        }
      }
    }

  def onComplete() =
    lock.enter {
      if (!isCompleted) {
        isCompleted = true

        var idx = 0
        while (idx < subscriptions.length) {
          subscriptions(idx).onComplete()
          idx += 1
        }
      }
    }

  private[this] def streamToMany(array: Array[Observer[T]], elem: T): Future[Continue] = {
    val newPromise = PromiseCounter[Continue](Continue, array.length)
    val length = array.length
    var idx = 0

    while (idx < length) {
      val obs = array(idx)
      obs.onNext(elem).onCompleteNow {
        case Continue.IsSuccess =>
          newPromise.countdown()
        case _ =>
          removeSubscription(obs)
          newPromise.countdown()
      }

      idx += 1
    }

    newPromise.future
  }

  private[this] def removeSubscription(observer: Observer[T]): Unit =
    lock.enter {
      subscriptions = subscriptions.filter(_ != observer)
    }

  private[this] def createSubscription(observers: Array[Observer[T]], instance: Observer[T]): Array[Observer[T]] =
    lock.enter {
      if (!observers.contains(instance))
        observers :+ instance
      else
        observers
    }
}

object PublishSubject {
  def apply[T]()(implicit scheduler: Scheduler): PublishSubject[T] =
    new PublishSubject[T](scheduler)
}