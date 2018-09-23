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

package monix.eval.internal

import cats.effect.CancelToken
import monix.catnap.CancelableF
import monix.eval.Task
import monix.eval.internal.TaskConnectionComposite.{Active, Cancelled, State}
import monix.execution.{Cancelable, Scheduler}
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.atomic.{Atomic, AtomicAny}

import scala.annotation.tailrec
import scala.collection.GenTraversableOnce

private[internal] final class TaskConnectionComposite private
  (stateRef: AtomicAny[State]) {

  val cancel: CancelToken[Task] =
    Task.suspend {
      stateRef.getAndSet(Cancelled) match {
        case Cancelled => Task.unit
        case Active(set) =>
          UnsafeCancelUtils.cancelAllUnsafe(set)
      }
    }

  /** Adds a cancelation token to the underlying collection, if
    * this connection hasn't been cancelled yet, otherwise it
    * cancels the given token.
    */
  def add(token: CancelToken[Task])(implicit s: Scheduler): Unit =
    addAny(token)

  /** Adds a [[monix.execution.Cancelable]] to the underlying
    * collection, if this connection hasn't been cancelled yet,
    * otherwise it cancels the given cancelable.
    */
  def add(cancelable: Cancelable)(implicit s: Scheduler): Unit =
    addAny(cancelable)

  /** Adds a [[monix.catnap.CancelableF]] to the underlying
    * collection, if this connection hasn't been cancelled yet,
    * otherwise it cancels the given cancelable.
    */
  def add(conn: CancelableF[Task])(implicit s: Scheduler): Unit =
    addAny(conn)

  @tailrec
  private def addAny(ref: AnyRef/* CancelToken[Task] | CancelableF[Task] | Cancelable */)(implicit s: Scheduler): Unit =
    stateRef.get match {
      case Cancelled =>
        UnsafeCancelUtils.triggerCancel(ref)
      case current @ Active(set) =>
        if (!stateRef.compareAndSet(current, Active(set + ref))) {
          // $COVERAGE-OFF$
          addAny(ref)
          // $COVERAGE-ON$
        }
    }

  /**
    * Adds a whole collection of cancellation tokens, if the
    * connection is still active, or cancels the whole collection
    * otherwise.
    */
  def addAll(that: GenTraversableOnce[CancelToken[Task]])
    (implicit s: Scheduler): Unit = {

    @tailrec def loop(that: Iterable[CancelToken[Task]]): Unit =
      stateRef.get match {
        case Cancelled =>
          UnsafeCancelUtils.cancelAllUnsafe(that).runAsyncAndForget
        case current @ Active(set) =>
          if (!stateRef.compareAndSet(current, Active(set ++ that))) {
            // $COVERAGE-OFF$
            loop(that)
            // $COVERAGE-ON$
          }
      }

    loop(that.toIterable.seq)
  }

  /**
    * Removes the given token reference from the underlying collection.
    */
  def remove(token: CancelToken[Task]): Unit =
    removeAny(token)

  /**
    * Removes a specific [[monix.execution.Cancelable]] reference
    * from the underlying collection.
    */
  def remove(cancelable: Cancelable): Unit =
    removeAny(cancelable)

  /**
    * Removes a specific [[monix.catnap.CancelableF]] reference
    * from the underlying collection.
    */
  def remove(conn: CancelableF[Task]): Unit =
    removeAny(conn)

  @tailrec
  private def removeAny(ref: AnyRef): Unit =
    stateRef.get match {
      case Cancelled => ()
      case current @ Active(set) =>
        if (!stateRef.compareAndSet(current, Active(set - ref))) {
          // $COVERAGE-OFF$
          removeAny(ref)
          // $COVERAGE-ON$
        }
    }
}

private[internal] object TaskConnectionComposite {
  /**
    * Builder for [[TaskConnectionComposite]].
    */
  def apply(initial: CancelToken[Task]*): TaskConnectionComposite =
    new TaskConnectionComposite(
      Atomic.withPadding(Active(Set(initial:_*)) : State, LeftRight128))

  private sealed abstract class State
  private final case class Active(set: Set[AnyRef/* CancelToken[Task] | CancelableF[Task] | Cancelable */]) extends State
  private case object Cancelled extends State
}