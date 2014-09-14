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

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Subject}

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future}


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
final class AsyncSubject[T] private (ec: ExecutionContext) extends Subject[T,T] { self =>
  import monifu.reactive.subjects.AsyncSubject._

  override implicit val context = ec
  private[this] val state = Atomic(Active(Set.empty[Observer[T]]) : State[T])
  private[this] var onNextHappened = false
  private[this] var currentElem: T = _

  @tailrec
  def subscribeFn(observer: Observer[T]): Unit =
    state.get match {
      case current @ Active(set) =>
        if (!state.compareAndSet(current, Active(set + observer)))
          subscribeFn(observer)
      case CompletedEmpty =>
        observer.onComplete()
      case CompletedError(ex) =>
        observer.onError(ex)
      case Completed(value) =>
        observer.onNext(value).onSuccess {
          case Continue =>
            observer.onComplete()
        }
    }

  def onNext(elem: T): Future[Ack] = {
    if (!onNextHappened) onNextHappened = true
    currentElem = elem
    Continue
  }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Active(set) =>
        if (!state.compareAndSet(current, CompletedError(ex)))
          onError(ex)
        else
          for (obs <- set) obs.onError(ex)

      case _ => // already completed, do nothing
    }

  @tailrec
  def onComplete() =
    state.get match {
      case current @ Active(set) =>
        if (onNextHappened)
          if (!state.compareAndSet(current, Completed(currentElem)))
            onComplete()
          else
            for (obs <- set) obs.onNext(currentElem).onSuccess {
              case Continue => obs.onComplete()
            }
        else
          if (!state.compareAndSet(current, CompletedEmpty))
            onComplete()
          else
            for (obs <- set) obs.onComplete()

      case _ => // already completed, do nothing
    }
}

object AsyncSubject {
  def apply[T]()(implicit ec: ExecutionContext): AsyncSubject[T] =
    new AsyncSubject[T](ec)

  private sealed trait State[+T]
  private case class Active[T](observers: Set[Observer[T]]) extends State[T]
  private case object CompletedEmpty extends State[Nothing]
  private case class Completed[+T](value: T) extends State[T]
  private case class CompletedError(ex: Throwable) extends State[Nothing]
}