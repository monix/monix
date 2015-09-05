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
 
package monifu.reactive.subjects

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals.PromiseCounter
import monifu.reactive.{Ack, Subject, Subscriber}
import scala.concurrent.Future


/**
 * A `PublishSubject` emits to a subscriber only those items that are
 * emitted by the source subsequent to the time of the subscription
 *
 * If the source terminates with an error, the `PublishSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 */
final class PublishSubject[T] private () extends Subject[T,T] { self =>
  @volatile private[this] var subscribers = Vector.empty[Subscriber[T]]
  @volatile private[this] var isDone = false
  private[this] var errorThrown: Throwable = null

  private[this] def onDone(subscriber: Subscriber[T]) = {
    if (errorThrown != null)
      subscriber.onError(errorThrown)
    else
      subscriber.onComplete()
  }

  def onSubscribe(subscriber: Subscriber[T]): Unit =
    if (isDone) {
      // fast path
      onDone(subscriber)
    }
    else self.synchronized {
      if (isDone) onDone(subscriber) else
        subscribers = subscribers :+ subscriber
    }

  def onNext(elem: T): Future[Ack] = {
    if (isDone) Cancel else {
      val iterator = subscribers.iterator
      var result: PromiseCounter[Continue.type] = null

      while (iterator.hasNext) {
        val subscriber = iterator.next()
        import subscriber.scheduler

        val ack = subscriber.onNext(elem)
        if (ack.isCompleted) {
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            unsubscribe(subscriber)
        }
        else {
          if (result == null) result = PromiseCounter(Continue, 1)
          result.acquire()

          ack.onComplete {
            case Continue.IsSuccess =>
              result.countdown()
            case _ =>
              unsubscribe(subscriber)
              result.countdown()
          }
        }
      }

      if (result == null) Continue else {
        result.countdown()
        result.future
      }
    }
  }

  def onError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      errorThrown = ex
      isDone = true
      subscribers.foreach(_.onError(ex))
      subscribers = Vector.empty
    }
  }

  def onComplete(): Unit = self.synchronized {
    if (!isDone) {
      isDone = true
      subscribers.foreach(_.onComplete())
      subscribers = Vector.empty
    }
  }

  private[this] def unsubscribe(subscriber: Subscriber[T]): Continue =
    self.synchronized {
      subscribers = subscribers.filterNot(_ == subscriber)
      Continue
    }
}

object PublishSubject {
  /** Builder for [[PublishSubject]] */
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]()
}
