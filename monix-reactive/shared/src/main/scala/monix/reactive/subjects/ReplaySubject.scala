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

package monix.reactive.subjects

import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.internal.util.PromiseCounter
import monix.reactive.observers.{ConnectableSubscriber, Subscriber}
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.Future

/** `ReplaySubject` emits to any observer all of the items that were emitted
  * by the source, regardless of when the observer subscribes.
  */
final class ReplaySubject[T] private (initialState: ReplaySubject.State[T])
  extends Subject[T,T] { self =>

  private[this] val stateRef = Atomic(initialState)

  def size: Int =
    stateRef.get.subscribers.size

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    def streamOnDone(buffer: Iterable[T], errorThrown: Throwable): Cancelable = {
      implicit val s = subscriber.scheduler

      Observable.fromIterable(buffer).unsafeSubscribeFn(new Subscriber[T] {
        implicit val scheduler = subscriber.scheduler

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
        c.pushFirstAll(buffer)

        import subscriber.scheduler
        val connecting = c.connect()
        connecting.syncOnStopOrFailure(_ => removeSubscriber(c))

        Cancelable { () =>
          try removeSubscriber(c)
          finally connecting.cancel()
        }
      } else {
        // retry
        unsafeSubscribeFn(subscriber)
      }
    }
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get

    if (state.isDone) Stop else {
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
            if (ack != Continue && ack.value.get != Continue.AsSuccess)
              removeSubscriber(subscriber)
          }
          else {
            // going async, so we've got to count active futures for final Ack
            // the counter starts from 1 because zero implies isCompleted
            if (result == null) result = PromiseCounter(Continue, 1)
            result.acquire()

            ack.onComplete {
              case Continue.AsSuccess =>
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
  def apply[T](initial: T*): ReplaySubject[T] =
    create(initial)

  /** Creates an unbounded replay subject. */
  def create[T](initial: Seq[T]): ReplaySubject[T] =
    new ReplaySubject[T](State[T](initial.toVector, 0))

  /** Creates a size-bounded replay subject.
    *
    * In this setting, the ReplaySubject holds at most size items in its
    * internal buffer and discards the oldest item.
    *
    * @param capacity is the maximum size of the internal buffer
    */
  def createLimited[T](capacity: Int): ReplaySubject[T] = {
    require(capacity > 0, "capacity must be strictly positive")
    new ReplaySubject[T](State[T](Queue.empty, capacity))
  }

  /** Creates a size-bounded replay subject, prepopulated.
    *
    * In this setting, the ReplaySubject holds at most size items in its
    * internal buffer and discards the oldest item.
    *
    * @param capacity is the maximum size of the internal buffer
    * @param initial is an initial sequence of elements to prepopulate the buffer
    */
  def createLimited[T](capacity: Int, initial: Seq[T]): ReplaySubject[T] = {
    require(capacity > 0, "capacity must be strictly positive")
    val elems = initial.takeRight(capacity)
    new ReplaySubject[T](State[T](Queue(elems:_*), capacity))
  }

  /** Internal state for [[monix.reactive.subjects.ReplaySubject]] */
  private final case class State[T](
    buffer: Seq[T],
    capacity: Int,
    subscribers: Set[ConnectableSubscriber[T]] = Set.empty[ConnectableSubscriber[T]],
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
      copy(subscribers = subscribers + s)

    def removeSubscriber(toRemove: ConnectableSubscriber[T]): State[T] = {
      val newSet = subscribers - toRemove
      copy(subscribers = newSet)
    }

    def markDone(ex: Throwable): State[T] = {
      copy(subscribers = Set.empty, isDone = true, errorThrown = ex)
    }
  }
}