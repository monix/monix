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
import monifu.reactive.observers.ConnectableSubscriber
import monifu.reactive.{Ack, Subject, Subscriber}

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.Future


/**
 * `ReplaySubject` emits to any observer all of the items that were emitted
 * by the source, regardless of when the observer subscribes.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.ReplaySubject.png" />
 */
final class ReplaySubject[T] private (queue: Queue[T])
  extends Subject[T,T] { self =>

  import monifu.reactive.subjects.ReplaySubject.State
  import monifu.reactive.subjects.ReplaySubject.State._

  private[this] val state = Atomic(Empty(queue) : State[T])

  def subscribeFn(subscriber: Subscriber[T]): Unit = {
    @tailrec
    def loop(): ConnectableSubscriber[T] = {
      state.get match {
        case current @ Empty(cache) =>
          val connectable = ConnectableSubscriber(subscriber)
          connectable.pushNext(cache : _*)

          if (!state.compareAndSet(current, Active(Array(connectable), cache)))
            loop()
          else
            connectable

        case current @ Active(observers, cache) =>
          val connectable = ConnectableSubscriber(subscriber)
          connectable.pushNext(cache : _*)

          if (!state.compareAndSet(current, Active(observers :+ connectable, cache)))
            loop()
          else
            connectable

        case current @ Complete(cache, errorThrown) =>
          val connectable = ConnectableSubscriber(subscriber)
          connectable.pushNext(cache : _*)

          if (errorThrown ne null)
            connectable.pushError(errorThrown)
          else
            connectable.pushComplete()

          connectable
      }
    }

    loop().connect()
  }

  private[this] def emitNext(subscriber: Subscriber[T], elem: T): Future[Continue] = {
    implicit val s = subscriber.scheduler
    val obs = subscriber.observer

    obs.onNext(elem) match {
      case sync if sync.isCompleted =>
        sync.value.get match {
          case Continue.IsSuccess =>
            Continue

          case _ =>
            removeSubscription(subscriber)
            Continue
        }
      case async =>
        async.map {
          case Continue => Continue
          case Cancel =>
            removeSubscription(subscriber)
            Continue
        }
    }
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    state.get match {
      case current @ Empty(_) =>
        if (!state.compareAndSet(current, Empty(current.cache.enqueue(elem))))
          onNext(elem)
        else
          Continue

      case current @ Active(subscribers, cache) =>
        if (!state.compareAndSet(current, Active(subscribers, cache.enqueue(elem))))
          onNext(elem)

        else {
          var idx = 0
          var acc = Continue : Future[Continue]

          while (idx < subscribers.length) {
            val subscriber = subscribers(idx)
            implicit val s = subscriber.scheduler

            acc = if (acc == Continue || (acc.isCompleted && acc.value.get.isSuccess))
              emitNext(subscriber, elem)
            else {
              val f = emitNext(subscriber, elem)
              acc.flatMap(_ => f)
            }

            idx += 1
          }

          acc
        }

      case _ =>
        Cancel
    }
  }

  @tailrec
  def onComplete(): Unit =
    state.get match {
      case current @ Empty(cache) =>
        if (!state.compareAndSet(current, Complete(cache, null))) {
          onComplete() // retry
        }
      case current @ Active(observers, cache) =>
        if (!state.compareAndSet(current, Complete(cache, null))) {
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
        () // already complete, ignore
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Empty(cache) =>
        if (!state.compareAndSet(current, Complete(cache, ex))) {
          onError(ex) // retry
        }
      case current @ Active(observers, cache) =>
        if (!state.compareAndSet(current, Complete(cache, ex))) {
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
        () // already complete, ignore
    }

  @tailrec
  private[this] def removeSubscription(subscriber: Subscriber[T]): Unit = {
    state.get match {
      case current @ Active(observers, _) =>
        val update = current.copy(observers.filterNot(_ eq subscriber))
        if (!state.compareAndSet(current, update))
          removeSubscription(subscriber)

      case _ =>
        () // not active, do nothing
    }
  }
}

object ReplaySubject {
  def apply[T](): ReplaySubject[T] =
    new ReplaySubject[T](Queue.empty)

  def apply[T](initial: T*): ReplaySubject[T] =
    new ReplaySubject[T](Queue(initial: _*))

  private sealed trait State[T]
  private object State {
    case class Empty[T](cache: Queue[T]) extends State[T]
    case class Active[T](iterator: Array[ConnectableSubscriber[T]], cache: Queue[T]) extends State[T]
    case class Complete[T](cache: Queue[T], errorThrown: Throwable = null) extends State[T]
  }
}