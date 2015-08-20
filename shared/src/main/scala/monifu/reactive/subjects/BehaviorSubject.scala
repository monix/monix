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

import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.observers.ConnectableSubscriber
import monifu.reactive.{Ack, Subject, Subscriber}

import scala.annotation.tailrec
import scala.concurrent.Future


/**
 * `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
 * or the `initialValue` (as the seed) in case no value has yet been emitted, then continuing
 * to emit events subsequent to the time of invocation.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 *
 * When the source terminates in error, the `BehaviorSubject` will not emit any items to
 * subsequent subscribers, but instead it will pass along the error notification.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 */
final class BehaviorSubject[T](initialValue: T) extends Subject[T,T] { self =>
  import monifu.reactive.subjects.BehaviorSubject.State
  import monifu.reactive.subjects.BehaviorSubject.State._

  private[this] val state = Atomic(Empty(initialValue) : State[T])

  def subscribeFn(subscriber: Subscriber[T]): Unit = {
    @tailrec
    def loop(): ConnectableSubscriber[T] = {
      state.get match {
        case current @ Empty(cachedValue) =>
          val obs = ConnectableSubscriber[T](subscriber)
          obs.pushNext(cachedValue)

          if (!state.compareAndSet(current, Active(Array(obs), cachedValue)))
            loop()
          else
            obs

        case current @ Active(observers, cachedValue) =>
          val obs = ConnectableSubscriber[T](subscriber)
          obs.pushNext(cachedValue)

          if (!state.compareAndSet(current, Active(observers :+ obs, cachedValue)))
            loop()
          else
            obs

        case current @ Complete(cachedValue, errorThrown) =>
          val obs = ConnectableSubscriber[T](subscriber)
          if (errorThrown eq null) {
            obs.pushNext(cachedValue)
            obs.pushComplete()
          }
          else {
            obs.pushError(errorThrown)
          }
          obs
      }
    }

    loop().connect()
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    state.get match {
      case current @ Empty(_) =>
        if (!state.compareAndSet(current, Empty(elem)))
          onNext(elem)
        else
          Continue

      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Active(observers, elem)))
          onNext(elem)

        else
          stream(observers, elem)

      case _ =>
        Cancel
    }
  }

  @tailrec
  def onComplete(): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).observer.onComplete()
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).observer.onError(ex)
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  private[this] def stream(array: Array[ConnectableSubscriber[T]], elem: T): Future[Continue] = {
    val newPromise = PromiseCounter[Continue](Continue, array.length)
    val length = array.length
    var idx = 0

    while (idx < length) {
      val subscriber = array(idx)
      val obs = subscriber.observer
      implicit val s = subscriber.scheduler

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

  @tailrec
  private[this] def removeSubscription(subscriber: Subscriber[T]): Unit =
    state.get match {
      case current @ Active(subscribers, cachedValue) =>
        val update = subscribers.filterNot(_ == subscriber)
        if (update.nonEmpty) {
          if (!state.compareAndSet(current, Active(update, cachedValue)))
            removeSubscription(subscriber)
        }
        else {
          if (!state.compareAndSet(current, Empty(cachedValue)))
            removeSubscription(subscriber)
        }
      case _ =>
        () // ignore
    }
}

object BehaviorSubject {
  def apply[T](initialValue: T): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue)

  private sealed trait State[T]
  private object State {
    case class Empty[T](cachedValue: T) extends State[T]
    case class Active[T](iterator: Array[ConnectableSubscriber[T]], cachedValue: T) extends State[T]
    case class Complete[T](cachedValue: T, errorThrown: Throwable = null) extends State[T]
  }
}