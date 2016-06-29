/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.execution.misc

import scala.concurrent.{Future, Promise}
import scala.collection.immutable.Queue
import monix.execution.atomic.AtomicAny
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.misc.AsyncQueue.State
import scala.annotation.tailrec

/** And asynchronous queue implementation.
  *
  * On `poll`, if there are queued elements, it returns oe
  * immediately, otherwise it returns a `Future`
  */
final class AsyncQueue[T] private (elems: Queue[T]) extends Serializable {
  private[this] val stateRef =
    AtomicAny.withPadding(State(elems, Queue.empty), LeftRight128)

  /** If there are elements in the queue, returns one,
    * otherwise returns a `Future` that waits (asynchronously)
    * until items are offered.
    */
  @tailrec def poll(): Future[T] = stateRef.get match {
    case current @ State(elements, promises) =>
      if (elements.nonEmpty) {
        val (e, newQ) = elements.dequeue
        val update = State(newQ, promises)

        if (stateRef.compareAndSet(current, update))
          Future.successful(e)
        else
          poll()
      }
      else {
        val p = Promise[T]()
        val update = State(elements, promises.enqueue(p))

        if (stateRef.compareAndSet(current, update))
          p.future
        else
          poll()
      }
  }

  /** Enqueues an item in the queue, or feeds it to a waiting
    * consumer if there are such waiting consumers.
    */
  @tailrec def offer(elem: T): Unit = stateRef.get match {
    case current @ State(elements, promises) =>
      if (promises.nonEmpty) {
        val (p, q) = promises.dequeue
        val update = State(elements, q)
        if (stateRef.compareAndSet(current, update))
          p.success(elem)
        else
          offer(elem)
      }
      else {
        val update = State(elements.enqueue(elem), promises)
        if (!stateRef.compareAndSet(current, update))
          offer(elem)
      }
  }

  /** Clears the queue of all offered items or promises. */
  def clear(): Unit =
    stateRef.set(State(Queue.empty, Queue.empty))

  /** Clears the whole queue, then offers one item. */
  def clearAndOffer(elem: T): Unit =
    stateRef.set(State(Queue(elem), Queue.empty))
}

object AsyncQueue {
  /** Builder for an [[AsyncQueue]], given an initial
    * set of `elems`.
    */
  def apply[T](elems: T*): AsyncQueue[T] =
    from(Queue(elems:_*))

  /** Returns an empty [[AsyncQueue]]. */
  def empty[T]: AsyncQueue[T] =
    from(Queue.empty)

  /** Converts an immutable `Queue` to an [[AsyncQueue]]. */
  def from[T](queue: Queue[T]): AsyncQueue[T] =
    new AsyncQueue(queue)

  private final
  case class State[T](elements: Queue[T], promises: Queue[Promise[T]])
}
