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
import monifu.reactive._
import monifu.reactive.internals._
import monifu.reactive.internals.collection.{DropHeadOnOverflowQueue, UnlimitedBuffer, Buffer}
import scala.concurrent.Future

/**
 * `ReplaySubject` emits to any observer all of the items that were emitted
 * by the source, regardless of when the observer subscribes.
 */
final class ReplaySubject[T] private (initial: Buffer[T])
  extends Subject[T,T] { self =>

  @volatile private[this] var subscribers = Vector.empty[Subscriber[T]]
  @volatile private[this] var isDone = false

  private[this] val queue = initial
  private[this] var errorThrown: Throwable = null

  private[this] def onDone(subscriber: Subscriber[T]): Unit = {
    import subscriber.scheduler
    val f = subscriber.feed(queue)
    if (errorThrown != null)
      f.onContinueSignalError(subscriber, errorThrown)
    else
      f.onContinueSignalComplete(subscriber)
  }

  def onSubscribe(subscriber: Subscriber[T]): Unit =
    if (isDone) {
      // fast path
      onDone(subscriber)
    }
    else self.synchronized {
      if (isDone) onDone(subscriber) else {
        import subscriber.scheduler
        val newSubscriber = new FreezeOnFirstOnNextSubscriber(subscriber)
        subscribers = subscribers :+ newSubscriber
        newSubscriber.firstTimeOnNext.onComplete { _ =>
          newSubscriber.continue(subscriber.feed(queue))
        }
      }
    }

  def onNext(elem: T): Future[Ack] = {
    if (isDone) Cancel else {
      queue.offer(elem)

      val iterator = subscribers.iterator
      var result: PromiseCounter[Continue.type] = null

      while (iterator.hasNext) {
        val subscriber = iterator.next()
        import subscriber.scheduler

        val ack = subscriber.onNext(elem)
        if (ack.isCompleted) {
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            unsubscribe(subscriber)
        }
        else {
          if (result == null) result = PromiseCounter(Continue, 1)
          result.acquire()

          ack.onComplete {
            case Continue.IsSuccess =>
              result.countdown()
            case _ =>
              unsubscribe(subscriber)
              result.countdown()
          }
        }
      }

      if (result == null) Continue else {
        result.countdown()
        result.future
      }
    }
  }

  def onError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      errorThrown = ex
      isDone = true
      subscribers.foreach(_.onError(ex))
      subscribers = Vector.empty
    }
  }

  def onComplete(): Unit = self.synchronized {
    if (!isDone) {
      isDone = true
      subscribers.foreach(_.onComplete())
      subscribers = Vector.empty
    }
  }

  private[this] def unsubscribe(subscriber: Subscriber[T]): Continue =
    self.synchronized {
      subscribers = subscribers.filterNot(_ == subscriber)
      Continue
    }
}

object ReplaySubject {
  /** Creates an unbounded replay subject. */
  def apply[T](initial: T*): ReplaySubject[T] = {
    val buffer = UnlimitedBuffer[Any]()
    if (initial.nonEmpty) buffer.offerMany(initial: _*)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }

  /** Creates an unbounded replay subject. */
  def create[T](initial: T*): ReplaySubject[T] = {
    val buffer = UnlimitedBuffer[Any]()
    if (initial.nonEmpty) buffer.offerMany(initial: _*)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }

  /**
   * Creates a size-bounded replay subject.
   *
   * In this setting, the ReplaySubject holds at most size items in its
   * internal buffer and discards the oldest item.
   *
   * NOTE: the `capacity` is actually grown to the next power of 2 (minus 1),
   * because buffers sized as powers of two can be more efficient and the
   * underlying implementation is most likely to be a ring buffer. So give it
   * `300` and its capacity is going to be `512 - 1`
   */
  def createWithSize[T](capacity: Int) = {
    val buffer = DropHeadOnOverflowQueue[Any](capacity)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }
}
