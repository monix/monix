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
final class ReplaySubject[A] private (initialState: ReplaySubject.State[A])
  extends Subject[A,A] { self =>

  private[this] val stateRef = Atomic(initialState)

  def size: Int =
    stateRef.get.subscribers.size

  @tailrec
  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    def streamOnDone(buffer: Iterable[A], errorThrown: Throwable): Cancelable = {
      implicit val s = subscriber.scheduler

      Observable.fromIterable(buffer).unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler = subscriber.scheduler

        def onNext(elem: A) =
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
  def onNext(elem: A): Future[Ack] = {
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
  private def removeSubscriber(s: ConnectableSubscriber[A]): Unit = {
    val state = stateRef.get
    val newState = state.removeSubscriber(s)
    if (!stateRef.compareAndSet(state, newState))
      removeSubscriber(s)
  }
}

object ReplaySubject {
  /** Creates an unbounded replay subject. */
  def apply[A](initial: A*): ReplaySubject[A] =
    create(initial)

  /** Creates an unbounded replay subject. */
  def create[A](initial: Seq[A]): ReplaySubject[A] =
    new ReplaySubject[A](State[A](initial.toVector, 0))

  /** Creates a size-bounded replay subject.
    *
    * In this setting, the ReplaySubject holds at most size items in its
    * internal buffer and discards the oldest item.
    *
    * @param capacity is the maximum size of the internal buffer
    */
  def createLimited[A](capacity: Int): ReplaySubject[A] = {
    require(capacity > 0, "capacity must be strictly positive")
    new ReplaySubject[A](State[A](Queue.empty, capacity))
  }

  /** Creates a size-bounded replay subject, prepopulated.
    *
    * In this setting, the ReplaySubject holds at most size items in its
    * internal buffer and discards the oldest item.
    *
    * @param capacity is the maximum size of the internal buffer
    * @param initial is an initial sequence of elements to prepopulate the buffer
    */
  def createLimited[A](capacity: Int, initial: Seq[A]): ReplaySubject[A] = {
    require(capacity > 0, "capacity must be strictly positive")
    val elems = initial.takeRight(capacity)
    new ReplaySubject[A](State[A](Queue(elems:_*), capacity))
  }

  /** Internal state for [[monix.reactive.subjects.ReplaySubject]] */
  private final case class State[A](
    buffer: Seq[A],
    capacity: Int,
    subscribers: Set[ConnectableSubscriber[A]] = Set.empty[ConnectableSubscriber[A]],
    length: Int = 0,
    isDone: Boolean = false,
    errorThrown: Throwable = null) {

    def appendElem(elem: A): State[A] = {
      if (capacity == 0)
        copy(buffer = buffer :+ elem)
      else if (length >= capacity)
        copy(buffer = buffer.tail :+ elem)
      else
        copy(buffer = buffer :+ elem, length = length + 1)
    }

    def addNewSubscriber(s: ConnectableSubscriber[A]): State[A] =
      copy(subscribers = subscribers + s)

    def removeSubscriber(toRemove: ConnectableSubscriber[A]): State[A] = {
      val newSet = subscribers - toRemove
      copy(subscribers = newSet)
    }

    def markDone(ex: Throwable): State[A] = {
      copy(subscribers = Set.empty, isDone = true, errorThrown = ex)
    }
  }
}