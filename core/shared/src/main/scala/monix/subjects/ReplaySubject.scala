/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.subjects

import monix.concurrent.atomic.padded.Atomic
import monix.internal.math
import monix.Ack.{Cancel, Continue}
import monix._
import monix.internal._
import monix.observers.ConnectableSubscriber
import math._

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * `ReplaySubject` emits to any observer all of the items that were emitted
  * by the source, regardless of when the observer subscribes.
  */
final class ReplaySubject[T] private (initialState: ReplaySubject.State[T])
  extends Subject[T,T] { self =>

  private[this] val stateRef = Atomic(initialState)

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit = {
    def streamOnDone(buffer: Iterable[T], errorThrown: Throwable): Unit = {
      implicit val s = subscriber.scheduler

      Observable.fromIterable(buffer).unsafeSubscribeFn(new Observer[T] {
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

    val state = stateRef.get
    val buffer = state.buffer

    if (state.isDone) {
      // fast path
      streamOnDone(buffer, state.errorThrown)
    }
    else {
      val c = ConnectableSubscriber(subscriber)
      val newState = state.addNewSubscriber(c)
      if (stateRef.compareAndSet(state, newState)) {
        c.pushIterable(buffer)
        c.connect()
      }
      else {
        // retry
        unsafeSubscribeFn(subscriber)
      }
    }
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get

    if (state.isDone) Cancel else {
      val newState = state.appendElem(elem)
      if (!stateRef.compareAndSet(state, newState)) {
        onNext(elem) // retry
      }
      else {
        val iterator = state.subscribers.iterator
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
              removeSubscriber(subscriber)
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
                removeSubscriber(subscriber)
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
  }

  override def onError(ex: Throwable): Unit =
    onCompleteOrError(ex)

  override def onComplete(): Unit =
    onCompleteOrError(null)

  @tailrec
  private def onCompleteOrError(ex: Throwable): Unit = {
    val state = stateRef.get

    if (!state.isDone) {
      if (!stateRef.compareAndSet(state, state.markDone(ex)))
        onCompleteOrError(ex)
      else {
        val iterator = state.subscribers.iterator
        while (iterator.hasNext) {
          val ref = iterator.next()

          if (ex != null)
            ref.onError(ex)
          else
            ref.onComplete()
        }
      }
    }
  }

  @tailrec
  private def removeSubscriber(s: ConnectableSubscriber[T]): Unit = {
    val state = stateRef.get
    val newState = state.removeSubscriber(s)
    if (!stateRef.compareAndSet(state, newState))
      removeSubscriber(s)
  }
}

object ReplaySubject {
  /** Creates an unbounded replay subject. */
  def apply[T](initial: T*): ReplaySubject[T] = {
    create(initial:_*)
  }

  /** Creates an unbounded replay subject. */
  def create[T](initial: T*): ReplaySubject[T] = {
    new ReplaySubject[T](State(Vector(initial:_*), 0))
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
  def createWithSize[T](capacity: Int): ReplaySubject[T] = {
    require(capacity > 0, "capacity must be strictly positive")
    val maxCapacity = nextPowerOf2(capacity + 1)
    new ReplaySubject[T](State(Queue.empty, maxCapacity))
  }

  /** Internal state for [[monix.subjects.ReplaySubject]] */
  private final case class State[T](
    buffer: Seq[T],
    capacity: Int,
    subscribers: Vector[ConnectableSubscriber[T]] = Vector.empty,
    length: Int = 0,
    isDone: Boolean = false,
    errorThrown: Throwable = null) {

    def appendElem(elem: T): State[T] = {
      if (capacity == 0)
        copy(buffer = buffer :+ elem)
      else if (length >= capacity)
        copy(buffer = buffer.tail :+ elem)
      else
        copy(buffer = buffer :+ elem, length = length + 1)
    }

    def addNewSubscriber(s: ConnectableSubscriber[T]): State[T] =
      copy(subscribers = subscribers :+ s)

    def removeSubscriber(toRemove: ConnectableSubscriber[T]): State[T] = {
      val newSet = subscribers.filter(_ != toRemove)
      copy(subscribers = newSet)
    }

    def markDone(ex: Throwable): State[T] = {
      copy(subscribers = Vector.empty, isDone = true, errorThrown = ex)
    }
  }
}
