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
import monifu.reactive.observers.ConnectableSubscriber
import monifu.reactive.{Ack, Observable, Subject, Subscriber}
import monifu.reactive.internals._
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
  * or the `initialValue` (as the seed) in case no value has yet been emitted, then continuing
  * to emit events subsequent to the time of invocation.
  *
  * When the source terminates in error, the `BehaviorSubject` will not emit any items to
  * subsequent subscribers, but instead it will pass along the error notification.
  *
  * @see [[monifu.reactive.Subject]]
  */
final class BehaviorSubject[T] private (initialValue: T) extends Subject[T,T] { self =>
  private[this] val subscribers = mutable.LinkedHashSet.empty[ConnectableSubscriber[T]]
  private[this] var cachedElem = initialValue
  private[this] var isDone = false
  private[this] var errorThrown: Throwable = null

  def onSubscribe(subscriber: Subscriber[T]): Unit =
    self.synchronized {
      if (errorThrown != null)
        subscriber.onError(errorThrown)
      else if (isDone)
        Observable.unit(cachedElem).onSubscribe(subscriber)
      else {
        val c = ConnectableSubscriber(subscriber)
        subscribers += c
        c.pushNext(cachedElem)
        c.connect()
      }
    }

  def onNext(elem: T): Future[Ack] = self.synchronized {
    if (isDone) Cancel else {
      cachedElem = elem

      val iterator = subscribers.iterator
      // counter that's only used when we go async, hence the null
      var result: PromiseCounter[Continue.type] = null

      while (iterator.hasNext) {
        val subscriber = iterator.next()
        // using the scheduler defined by each subscriber
        import subscriber.scheduler

        val ack = try subscriber.onNext(elem) catch {
          case NonFatal(ex) => Future.failed(ex)
        }

        // if execution is synchronous, takes the fast-path
        if (ack.isCompleted) {
          // subscriber canceled or triggered an error? then remove
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            subscribers -= subscriber
        }
        else {
          // going async, so we've got to count active futures for final Ack
          // the counter starts from 1 because zero implies isCompleted
          if (result == null) result = PromiseCounter(Continue, 1)
          result.acquire()

          ack.onComplete {
            case Continue.IsSuccess =>
              result.countdown()

            case _ =>
              // subscriber canceled or triggered an error? then remove
              subscribers -= subscriber
              result.countdown()
          }
        }
      }

      // has fast-path for completely synchronous invocation
      if (result == null) Continue else {
        result.countdown()
        result.future
      }
    }
  }

  override def onError(ex: Throwable): Unit =
    onCompleteOrError(ex)

  override def onComplete(): Unit =
    onCompleteOrError(null)

  private def onCompleteOrError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      errorThrown = ex
      isDone = true

      val iterator = subscribers.iterator
      while (iterator.hasNext) {
        val ref = iterator.next()

        if (ex != null)
          ref.onError(ex)
        else
          ref.onComplete()
      }

      subscribers.clear()
    }
  }
}

object BehaviorSubject {
  /** Builder for [[BehaviorSubject]] */
  def apply[T](initialValue: T): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue)
}
