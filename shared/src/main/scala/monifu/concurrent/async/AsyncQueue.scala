/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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
 
package monifu.concurrent.async

import scala.concurrent.{Promise, Future}
import scala.collection.immutable.Queue
import monifu.concurrent.atomic.padded.Atomic


final class AsyncQueue[T] private (elems: T*) {
  private[this] val state = Atomic(State(Queue(elems : _*), Queue.empty))

  def poll(): Future[T] =
    state.transformAndExtract {
      case State(elements, promises) =>
        if (elements.nonEmpty) {
          val (e, newQ) = elements.dequeue
          (Future.successful(e), State(newQ, promises))
        }
        else {
          val p = Promise[T]()
          (p.future, State(elements, promises.enqueue(p)))
        }
    }

  def offer(elem: T): Unit = {
    val p = state.transformAndExtract {
      case State(elements, promises) =>
        if (promises.nonEmpty) {
          val (p, q) = promises.dequeue
          (Some(p), State(elements, q))
        }
        else
          (None, State(elements.enqueue(elem), promises))
    }

    p.foreach(_.success(elem))
  }
  
  def clear(): Unit =
    state.set(State(Queue.empty, Queue.empty))

  def clearAndOffer(elem: T): Unit =
    state.set(State(Queue(elem), Queue.empty))

  private[this] case class State(elements: Queue[T], promises: Queue[Promise[T]])
}

object AsyncQueue {
  def apply[T](elems: T*) = new AsyncQueue[T](elems: _*)
  def empty[T] = new AsyncQueue[T]()
}
