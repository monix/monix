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
import monifu.reactive.internals.collection._
import monifu.reactive.observers.ConnectableSubscriber

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * `ReplaySubject` emits to any observer all of the items that were emitted
  * by the source, regardless of when the observer subscribes.
  */
final class ReplaySubject[T] private (initial: Buffer[T])
  extends Subject[T,T] { self =>

  @volatile private[this] var isDone = false
  private[this] var errorThrown: Throwable = null
  private[this] val buffer = initial
  private[this] val subscribers = mutable.LinkedHashSet.empty[ConnectableSubscriber[T]]

  def onSubscribe(subscriber: Subscriber[T]): Unit = {
    def streamOnDone(): Unit = {
      implicit val s = subscriber.scheduler

      Observable.fromIterable(buffer).onSubscribe(new Observer[T] {
        def onNext(elem: T) =
          subscriber.onNext(elem)
        def onError(ex: Throwable) =
          subscriber.onError(ex)

        def onComplete() =
          if (errorThrown != null)
            subscriber.onError(errorThrown)
          else
            subscriber.onComplete()
      })
    }

    // trying to take the fast path
    if (isDone) streamOnDone() else
      self.synchronized {
        if (isDone) streamOnDone() else {
          val c = ConnectableSubscriber(subscriber)
          subscribers += c
          c.pushBuffer(buffer)
          c.connect()
        }
      }
  }

  def onNext(elem: T): Future[Ack] = self.synchronized {
    if (isDone) Cancel else {
      // caching element
      buffer.offer(elem)

      val iterator = subscribers.iterator
      // counter that's only used when we go async, hence the null
      var result: PromiseCounter[Continue.type] = null

      while (iterator.hasNext) {
        val subscriber = iterator.next()
        // using the scheduler defined by each subscriber
        import subscriber.scheduler

        val ack = try subscriber.onNext(elem) catch {
          case NonFatal(ex) => Future.failed(ex)
        }

        // if execution is synchronous, takes the fast-path
        if (ack.isCompleted) {
          // subscriber canceled or triggered an error? then remove
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            subscribers -= subscriber
        }
        else {
          // going async, so we've got to count active futures for final Ack
          // the counter starts from 1 because zero implies isCompleted
          if (result == null) result = PromiseCounter(Continue, 1)
          result.acquire()

          ack.onComplete {
            case Continue.IsSuccess =>
              result.countdown()

            case _ =>
              // subscriber canceled or triggered an error? then remove
              subscribers -= subscriber
              result.countdown()
          }
        }
      }

      // has fast-path for completely synchronous invocation
      if (result == null) Continue else {
        result.countdown()
        result.future
      }
    }
  }

  override def onError(ex: Throwable): Unit =
    onCompleteOrError(ex)

  override def onComplete(): Unit =
    onCompleteOrError(null)

  private def onCompleteOrError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      errorThrown = ex
      isDone = true

      val iterator = subscribers.iterator
      while (iterator.hasNext) {
        val ref = iterator.next()

        if (ex != null)
          ref.onError(ex)
        else
          ref.onComplete()
      }

      subscribers.clear()
    }
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
