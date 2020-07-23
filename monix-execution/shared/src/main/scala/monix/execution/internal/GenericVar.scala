/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution.internal

import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import monix.execution.internal.collection.LinkedMap
import scala.annotation.tailrec

/**
  * Internal API — common implementation between:
  *
  *  - `monix.execution.AsyncVar`
  *  - `monix.catnap.MVar`
  */
private[monix] abstract class GenericVar[A, CancelToken] protected (initial: Option[A], ps: PaddingStrategy) {

  import GenericVar._
  private[this] val stateRef: AtomicAny[State[A]] =
    AtomicAny.withPadding(
      initial match { case None => State.empty[A]; case Some(a) => State(a) },
      ps
    )

  protected def makeCancelable(f: Id => Unit, id: Id): CancelToken
  protected def emptyCancelable: CancelToken

  protected final def unsafePut(a: A, await: Either[Nothing, Unit] => Unit): CancelToken = {
    stateRef.get() match {
      case current @ WaitForTake(value, listeners) =>
        val id = new Id
        val newMap = listeners.updated(id, (a, await))
        val update = WaitForTake(value, newMap)

        if (stateRef.compareAndSet(current, update)) {
          makeCancelable(putCancel, id)
        } else {
          unsafePut(a, await) // retry
        }

      case current @ WaitForPut(reads, takes) =>
        var first: Either[Nothing, A] => Unit = null
        val update: State[A] =
          if (takes.isEmpty) State(a)
          else {
            val (x, rest) = takes.dequeue
            first = x
            if (rest.isEmpty) State.empty[A]
            else WaitForPut(LinkedMap.empty, rest)
          }

        if (stateRef.compareAndSet(current, update)) {
          val value = Right(a)
          // Satisfies all current `read` requests found
          streamAll(value, reads)
          // Satisfies the first `take` request found
          if (first ne null) first(value)
          // Signals completion of `put`
          await(Constants.eitherOfUnit)
          emptyCancelable
        } else {
          unsafePut(a, await) // retry
        }
    }
  }

  protected final def unsafeTryPut(a: A): Boolean = {
    stateRef.get() match {
      case WaitForTake(_, _) => false

      case current @ WaitForPut(reads, takes) =>
        var first: Either[Nothing, A] => Unit = null
        val update: State[A] =
          if (takes.isEmpty) State(a)
          else {
            val (x, rest) = takes.dequeue
            first = x
            if (rest.isEmpty) State.empty[A]
            else WaitForPut(LinkedMap.empty, rest)
          }

        if (!stateRef.compareAndSet(current, update)) {
          unsafeTryPut(a) // retry
        } else {
          val value = Right(a)
          // Satisfies all current `read` requests found
          streamAll(value, reads)
          // Satisfies the first `take` request found
          if (first ne null) first(value)
          // Signals completion of `put`
          true
        }
    }
  }

  private val putCancel: (Id => Unit) = {
    @tailrec def loop(id: Id): Unit =
      stateRef.get() match {
        case current @ WaitForTake(_, queue) =>
          val update = current.copy(queue = queue - id)
          if (!stateRef.compareAndSet(current, update)) {
            loop(id) // retry
          }
        case _ =>
          ()
      }
    loop
  }

  protected final def unsafeTake(await: Either[Nothing, A] => Unit): CancelToken = {
    stateRef.get() match {
      case current @ WaitForTake(a, queue) =>
        val value = Right(a)
        if (queue.isEmpty) {
          if (stateRef.compareAndSet(current, State.empty)) {
            await(value)
            emptyCancelable
          } else {
            unsafeTake(await) // retry
          }
        } else {
          val ((ax, notify), xs) = queue.dequeue
          if (stateRef.compareAndSet(current, WaitForTake(ax, xs))) {
            await(value)
            notify(Constants.eitherOfUnit)
            emptyCancelable
          } else {
            unsafeTake(await) // retry
          }
        }

      case current @ WaitForPut(reads, takes) =>
        val id = new Id
        val newQueue = takes.updated(id, await)
        if (stateRef.compareAndSet(current, WaitForPut(reads, newQueue)))
          makeCancelable(takeCancel, id)
        else {
          unsafeTake(await) // retry
        }
    }
  }

  protected final def unsafeTryTake(): Option[A] = {
    val current: State[A] = stateRef.get()
    current match {
      case WaitForTake(value, queue) =>
        if (queue.isEmpty) {
          if (stateRef.compareAndSet(current, State.empty))
            // Signals completion of `take`
            Some(value)
          else {
            unsafeTryTake() // retry
          }
        } else {
          val ((ax, notify), xs) = queue.dequeue
          val update = WaitForTake(ax, xs)
          if (stateRef.compareAndSet(current, update)) {
            // Complete the `put` request waiting on a notification
            notify(Constants.eitherOfUnit)
            // Signals completion of `take`
            Some(value)
          } else {
            unsafeTryTake() // retry
          }
        }
      case WaitForPut(_, _) =>
        None
    }
  }

  private val takeCancel: (Id => Unit) = {
    @tailrec def loop(id: Id): Unit =
      stateRef.get() match {
        case current @ WaitForPut(reads, takes) =>
          val newMap = takes - id
          val update: State[A] = WaitForPut(reads, newMap)
          if (!stateRef.compareAndSet(current, update))
            loop(id)
        case _ =>
      }
    loop
  }

  protected final def unsafeRead(await: Either[Nothing, A] => Unit): CancelToken = {
    val current: State[A] = stateRef.get()
    current match {
      case WaitForTake(value, _) =>
        // A value is available, so complete `read` immediately without
        // changing the sate
        await(Right(value))
        emptyCancelable

      case WaitForPut(reads, takes) =>
        // No value available, enqueue the callback
        val id = new Id
        val newQueue = reads.updated(id, await)
        if (stateRef.compareAndSet(current, WaitForPut(newQueue, takes)))
          makeCancelable(readCancel, id)
        else
          unsafeRead(await) // retry
    }
  }

  protected final def unsafeTryRead(): Option[A] =
    stateRef.get() match {
      case WaitForTake(value, _) =>
        Some(value)
      case _ =>
        None
    }

  protected final def unsafeIsEmpty(): Boolean =
    stateRef.get() match {
      case WaitForTake(_, _) =>
        false
      case _ =>
        true
    }

  @tailrec
  private val readCancel: (Id => Unit) = {
    def loop(id: Id): Unit =
      stateRef.get() match {
        case current @ WaitForPut(reads, takes) =>
          val newMap = reads - id
          val update: State[A] = WaitForPut(newMap, takes)
          if (!stateRef.compareAndSet(current, update)) {
            loop(id) // retry
          }
        case _ =>
      }
    loop
  }

  // For streaming a value to a whole `reads` collection
  private final def streamAll(value: Either[Nothing, A], listeners: LinkedMap[Id, Either[Nothing, A] => Unit]): Unit = {

    val cursor = listeners.values.iterator
    while (cursor.hasNext) cursor.next().apply(value)
  }
}

private[monix] object GenericVar {

  /** Used with `LinkedMap` to identify callbacks that need to be cancelled. */
  private[monix] final class Id extends Serializable

  /** ADT modelling the internal state. */
  private sealed trait State[A]

  /** Private [[State]] builders.*/
  private object State {
    private[this] val ref = WaitForPut[Any](LinkedMap.empty, LinkedMap.empty)
    def apply[A](a: A): State[A] = WaitForTake(a, LinkedMap.empty)
    /** `Empty` state, reusing the same instance. */
    def empty[A]: State[A] = ref.asInstanceOf[State[A]]
  }

  /** `AsyncVar` state signaling it has `take` callbacks
    * registered and we are waiting for one or multiple
    * `put` operations.
    */
  private final case class WaitForPut[A](
    reads: LinkedMap[Id, Either[Nothing, A] => Unit],
    takes: LinkedMap[Id, Either[Nothing, A] => Unit])
    extends State[A]

  /** `AsyncVar` state signaling it has one or more values enqueued,
    * to be signaled on the next `take`.
    */
  private final case class WaitForTake[A](value: A, queue: LinkedMap[Id, (A, Either[Nothing, Unit] => Unit)])
    extends State[A]
}
