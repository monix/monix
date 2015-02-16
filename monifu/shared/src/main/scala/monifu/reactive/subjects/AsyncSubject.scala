/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
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

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Subject, Subscriber}

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.concurrent.Future


/**
 * An `AsyncSubject` emits the last value (and only the last value) emitted by the source Observable,
 * and only after that source Observable completes.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.AsyncSubject.png" />
 *
 * If the source terminates with an error, the `AsyncSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.AsyncSubject.e.png" />
 */
final class AsyncSubject[T] extends Subject[T,T] { self =>
  import monifu.reactive.subjects.AsyncSubject._

  private[this] val state = Atomic(Active(Set.empty[Subscriber[T]]) : State[T])
  private[this] var onNextHappened = false
  private[this] var currentElem: T = _

  @tailrec
  def subscribeFn(subscriber: Subscriber[T]): Unit =
    state.get match {
      case current @ Active(set) =>
        if (!state.compareAndSet(current, Active(set + subscriber)))
          subscribeFn(subscriber)
      case CompletedEmpty =>
        subscriber.observer.onComplete()
      case CompletedError(ex) =>
        subscriber.observer.onError(ex)
      case Completed(value) =>
        implicit val s = subscriber.scheduler
        subscriber.observer.onNext(value).onSuccess {
          case Continue =>
            subscriber.observer.onComplete()
        }
    }

  def onNext(elem: T): Future[Ack] = {
    if (!onNextHappened) onNextHappened = true
    currentElem = elem
    Continue
  }

  @tailrec
  def onError(ex: Throwable): Unit = state.get match {
    case current @ Active(set) =>
      if (!state.compareAndSet(current, CompletedError(ex)))
        onError(ex)
      else
        for (s <- set) s.observer.onError(ex)

    case _ =>
      () // already completed, do nothing
  }

  @tailrec
  def onComplete(): Unit = state.get match {
    case current @ Active(set) =>
      if (onNextHappened)
        if (!state.compareAndSet(current, Completed(currentElem)))
          onComplete()
        else
          for (subscriber <- set) {
            implicit val s = subscriber.scheduler
            subscriber.observer.onNext(currentElem).onSuccess {
              case Continue =>
                subscriber.observer.onComplete()
            }
          }
      else
      if (!state.compareAndSet(current, CompletedEmpty))
        onComplete()
      else
        for (s <- set) s.observer.onComplete()

    case _ =>
      () // already completed, do nothing
  }
}

object AsyncSubject {
  def apply[T](): AsyncSubject[T] =
    new AsyncSubject[T]()

  private sealed trait State[+T]
  private case class Active[T](observers: Set[Subscriber[T]]) extends State[T]
  private case object CompletedEmpty extends State[Nothing]
  private case class Completed[+T](value: T) extends State[T]
  private case class CompletedError(ex: Throwable) extends State[Nothing]
}