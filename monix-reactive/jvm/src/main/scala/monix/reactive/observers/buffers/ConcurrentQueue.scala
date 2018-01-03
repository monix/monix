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

package monix.reactive.observers.buffers

import java.util

import monix.execution.internal.Platform
import monix.execution.internal.atomic.UnsafeAccess
import monix.execution.internal.math.nextPowerOf2
import org.jctools.queues._
import org.jctools.queues.MessagePassingQueue.Consumer
import org.jctools.queues.atomic.{MpscAtomicArrayQueue, MpscLinkedAtomicQueue}

import scala.collection.mutable

/** A simple internal interface providing the needed commonality between
  * Java's `util.AbstractQueue` and the JCTools `MessagePassingQueue`.
  */
private[buffers] abstract class ConcurrentQueue[A] {
  def isEmpty: Boolean
  def poll(): A
  def offer(elem: A): Boolean
  def drain(buffer: mutable.Buffer[A], limit: Int): Unit
}

private[buffers] object ConcurrentQueue {
  /** Builds a concurrent queue with a limited capacity. */
  def limited[A](capacity: Int): ConcurrentQueue[A] = {
    val maxCapacity = math.max(4, nextPowerOf2(capacity))
    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE) {
      new FromMessagePassingQueue[A](
        if (maxCapacity <= Platform.recommendedBatchSize)
          new MpscArrayQueue[A](maxCapacity)
        else {
          val initialCapacity = math.min(Platform.recommendedBatchSize, maxCapacity / 2)
          new MpscChunkedArrayQueue[A](initialCapacity, maxCapacity)
        }
      )
    }
    else {
      new FromAbstractQueue[A](new MpscAtomicArrayQueue[A](maxCapacity))
    }
  }

  /** Builds an unbounded queue. */
  def unbounded[A](): ConcurrentQueue[A] = {
    if (UnsafeAccess.IS_OPENJDK_COMPATIBLE) {
      val size = Platform.recommendedBatchSize
      new FromMessagePassingQueue[A](new MpscUnboundedArrayQueue[A](size))
    }
    else {
      val ref = new MpscLinkedAtomicQueue[A]()
      new FromAbstractQueue[A](ref)
    }
  }

  /** Builds an instance from a `java.util.AbstractQueue`. */
  private final class FromAbstractQueue[A](underlying: util.AbstractQueue[A])
    extends ConcurrentQueue[A] {

    def isEmpty: Boolean =
      underlying.isEmpty
    def offer(elem: A): Boolean =
      underlying.offer(elem)
    def poll(): A =
      underlying.poll()

    def drain(buffer: mutable.Buffer[A], limit: Int): Unit = {
      var fetched = 0

      while (fetched < limit) {
        val next = underlying.poll()
        if (next == null) return
        buffer += next
        fetched += 1
      }
    }
  }


  /** Builds an instance from a `MessagePassingQueue`. */
  private final class FromMessagePassingQueue[A](underlying: MessagePassingQueue[A])
    extends ConcurrentQueue[A] {

    def isEmpty: Boolean =
      underlying.isEmpty
    def offer(elem: A): Boolean =
      underlying.relaxedOffer(elem)
    def poll(): A =
      underlying.relaxedPoll()

    def drain(buffer: mutable.Buffer[A], limit: Int): Unit = {
      val consumer: Consumer[A] = new Consumer[A] { def accept(e: A): Unit = buffer += e }
      underlying.drain(consumer, limit)
    }
  }
}
