/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.execution

import monix.execution.ChannelType.MPMC
import monix.execution.annotations.{ UnsafeBecauseImpure, UnsafeProtocol }
import monix.execution.atomic.AtomicAny
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.cancelables.MultiAssignCancelable
import monix.execution.internal.Constants
import monix.execution.internal.collection.LowLevelConcurrentQueue

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.concurrent.duration._

/**
  * A high-performance, back-pressured, asynchronous queue implementation.
  *
  * This is the impure, future-enabled version of [[monix.catnap.ConcurrentQueue]].
  *
  * ==Example==
  *
  * {{{
  *   import monix.execution.Scheduler.Implicits.global
  *
  *   val queue = AsyncQueue(capacity = 32)
  *
  *   def producer(n: Int): CancelableFuture[Unit] =
  *     queue.offer(n).flatMap { _ =>
  *       if (n >= 0) producer(n - 1)
  *       else CancelableFuture.unit
  *     }
  *
  *   def consumer(index: Int): CancelableFuture[Unit] =
  *     queue.poll().flatMap { a =>
  *       println(s"Worker $$index: $$a")
  *     }
  * }}}
  *
  * ==Back-Pressuring and the Polling Model==
  *
  * The initialized queue can be limited to a maximum buffer size, a size
  * that could be rounded to a power of 2, so you can't rely on it to be
  * precise. Such a bounded queue can be initialized via
  * [[monix.execution.AsyncQueue.bounded AsyncQueue.bounded]].
  * Also see [[BufferCapacity]], the configuration parameter that can be
  * passed in the [[monix.execution.AsyncQueue.withConfig AsyncQueue.withConfig]]
  * builder.
  *
  * On [[offer]], when the queue is full, the implementation back-pressures
  * until the queue has room again in its internal buffer, the future being
  * completed when the value was pushed successfully. Similarly [[poll]] awaits
  * the queue to have items in it. This works for both bounded and unbounded queues.
  *
  * For both `offer` and `poll`, in case awaiting a result happens, the
  * implementation does so asynchronously, without any threads being blocked.
  *
  * Currently the implementation is optimized for speed. In a producer-consumer
  * pipeline the best performance is achieved if the producer(s) and the
  * consumer(s) do not contend for the same resources. This is why when
  * doing asynchronous waiting for the queue to be empty or full, the
  * implementation does so by repeatedly retrying the operation, with
  * asynchronous boundaries and delays, until it succeeds. Fairness is
  * ensured by the implementation.
  *
  * ==Multi-threading Scenario==
  *
  * This queue support a [[ChannelType]] configuration, for fine tuning
  * depending on the needed multi-threading scenario — and this can yield
  * better performance:
  *
  *   - [[ChannelType.MPMC]]: multi-producer, multi-consumer
  *   - [[ChannelType.MPSC]]: multi-producer, single-consumer
  *   - [[ChannelType.SPMC]]: single-producer, multi-consumer
  *   - [[ChannelType.SPSC]]: single-producer, single-consumer
  *
  * The default is `MPMC`, because that's the safest scenario.
  *
  * {{{
  *   import monix.execution.ChannelType.MPSC
  *
  *   val queue = AsyncQueue(
  *     capacity = 64,
  *     channelType = MPSC
  *   )
  * }}}
  *
  * '''WARNING''': default is `MPMC`, however any other scenario implies
  * a relaxation of the internal synchronization between threads.
  *
  * This means that using the wrong scenario can lead to severe
  * concurrency bugs. If you're not sure what multi-threading scenario you
  * have, then just stick with the default `MPMC`.
  */
final class AsyncQueue[A] private[monix] (
  capacity: BufferCapacity,
  channelType: ChannelType,
  retryDelay: FiniteDuration = 10.millis
)(implicit scheduler: Scheduler) {

  /** Try pushing a value to the queue.
    *
    * The protocol is unsafe because usage of the "try*" methods imply an
    * understanding of concurrency, or otherwise the code can be very
    * fragile and buggy.
    *
    * @param a is the value pushed in the queue
    *
    * @return `true` if the operation succeeded, or `false` if the queue is
    *         full and cannot accept any more elements
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryOffer(a: A): Boolean = tryOfferUnsafe(a)

  /** Try pulling a value out of the queue.
    *
    * The protocol is unsafe because usage of the "try*" methods imply an
    * understanding of concurrency, or otherwise the code can be very
    * fragile and buggy.
    *
    * @return `Some(a)` in case a value was successfully retrieved from the
    *         queue, or `None` in case the queue is empty
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def tryPoll(): Option[A] = Option(tryPollUnsafe())

  /** Fetches a value from the queue, or if the queue is empty continuously
    * polls the queue until a value is made available.
    *
    * @return a [[CancelableFuture]] that will eventually complete with a
    *         value; it can also be cancelled, interrupting the waiting
    */
  @UnsafeBecauseImpure
  def poll(): CancelableFuture[A] = {
    val happy = tryPollUnsafe()
    if (happy != null)
      CancelableFuture.successful(happy)
    else {
      val p = Promise[A]()
      val c = MultiAssignCancelable()
      sleepThenRepeat(consumersAwaiting, pollQueue, pollTest, pollMap, p, c)
      CancelableFuture(p.future, c)
    }
  }

  /** Pushes a value in the queue, or if the queue is full, then repeats the
    * operation until it succeeds.
    *
    * @return a [[CancelableFuture]] that will eventually complete when the
    *         push has succeeded; it can also be cancelled, interrupting the
    *         waiting
    */
  @UnsafeBecauseImpure
  def offer(a: A): CancelableFuture[Unit] = {
    val happy = tryOfferUnsafe(a)
    if (happy)
      CancelableFuture.unit
    else
      offerWait(a, MultiAssignCancelable())
  }

  /** Pushes multiple values in the queue. Back-pressures if the queue is full.
    *
    * @return a [[CancelableFuture]] that will eventually complete when the
    *         push has succeeded; it can also be cancelled, interrupting the
    *         waiting
    */
  @UnsafeBecauseImpure
  def offerMany(seq: Iterable[A]): CancelableFuture[Unit] = {
    // recursive loop
    def loop(cursor: Iterator[A], c: MultiAssignCancelable): CancelableFuture[Unit] = {
      var elem: A = null.asInstanceOf[A]
      var hasCapacity = true
      // Happy path
      while (hasCapacity && cursor.hasNext) {
        elem = cursor.next()
        hasCapacity = queue.offer(elem) == 0
      }
      // Awaken sleeping consumers
      notifyConsumers()
      // Do we need to await on consumers?
      if (!hasCapacity) {
        val c2 = if (c != null) c else MultiAssignCancelable()
        offerWait(elem, c2).flatMap(_ => loop(cursor, c2))
      } else {
        CancelableFuture.unit
      }
    }

    loop(seq.iterator, null)
  }

  /** Fetches multiple elements from the queue, if available.
    *
    * This operation back-pressures until the `minLength` requirement is
    * achieved.
    *
    * @param minLength specifies the minimum length of the returned sequence;
    *        the operation back-pressures until this length is satisfied
    *
    * @param maxLength is the capacity of the used buffer, being the max
    *        length of the returned sequence
    *
    * @return a future with a sequence of length between minLength and maxLength;
    *         it can also be cancelled, interrupting the wait
    */
  @UnsafeBecauseImpure
  def drain(minLength: Int, maxLength: Int): CancelableFuture[Seq[A]] = {
    assert(minLength <= maxLength, s"minLength ($minLength) <= maxLength ($maxLength")
    val buffer = ArrayBuffer.empty[A]

    val length = tryDrainUnsafe(buffer, maxLength)
    if (length >= minLength) {
      CancelableFuture.successful(toSeq(buffer))
    } else {
      val promise = Promise[Seq[A]]()
      val conn = MultiAssignCancelable()

      sleepThenRepeat[Int, Seq[A]](
        consumersAwaiting,
        () => tryDrainUnsafe(buffer, maxLength - buffer.length),
        _ => buffer.length >= minLength,
        _ => toSeq(buffer),
        promise,
        conn
      )

      CancelableFuture(promise.future, conn)
    }
  }

  /** Removes all items from the queue.
    *
    * Called from the consumer thread, subject to the restrictions appropriate
    * to the implementation indicated by [[ChannelType]].
    *
    * '''WARNING:''' the `clear` operation should be done on the consumer side,
    * so it must be called from the same thread(s) that call [[poll]].
    */
  @UnsafeBecauseImpure
  def clear(): Unit = {
    queue.clear()
    notifyProducers()
  }

  /** Checks if the queue is empty.
    *
    * '''UNSAFE PROTOCOL:'''
    * Concurrent shared state changes very frequently, therefore this function might yield nondeterministic results.
    * Should be used carefully since some usecases might require a deeper insight into concurrent programming.
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def isEmpty: Boolean =
    queue.isEmpty

  private[this] val queue: LowLevelConcurrentQueue[A] =
    LowLevelConcurrentQueue(capacity, channelType, fenced = true)

  private[this] val consumersAwaiting =
    AtomicAny.withPadding[CancelablePromise[Unit]](null, LeftRight128)

  private[this] val producersAwaiting =
    if (capacity.isBounded)
      AtomicAny.withPadding[CancelablePromise[Unit]](null, LeftRight128)
    else
      null

  private def tryOfferUnsafe(a: A): Boolean = {
    if (queue.offer(a) == 0) {
      notifyConsumers()
      true
    } else {
      false
    }
  }

  private def tryPollUnsafe(): A = {
    val a = queue.poll()
    notifyProducers()
    a
  }

  private def tryDrainUnsafe(buffer: ArrayBuffer[A], maxLength: Int): Int = {
    val length = queue.drainToBuffer(buffer, maxLength)
    if (length > 0) notifyProducers()
    length
  }

  @tailrec
  private def notifyConsumers(): Unit = {
    // N.B. in case the queue is single-producer, this is a full memory fence
    // meant to prevent the re-ordering of `queue.offer` with `consumersAwait.get`
    queue.fenceOffer()

    val ref = consumersAwaiting.get()
    if (ref ne null) {
      if (consumersAwaiting.compareAndSet(ref, null)) {
        ref.complete(Constants.successOfUnit)
        ()
      } else {
        notifyConsumers()
      }
    }
  }

  @tailrec
  private def notifyProducers(): Unit =
    if (producersAwaiting ne null) {
      // N.B. in case this isn't a multi-consumer queue, this generates a
      // full memory fence in order to prevent the re-ordering of queue.poll()
      // with `producersAwait.get`
      queue.fencePoll()

      val ref = producersAwaiting.get()
      if (ref ne null) {
        if (producersAwaiting.compareAndSet(ref, null)) {
          ref.complete(Constants.successOfUnit)
          ()
        } else {
          notifyProducers()
        }
      }
    }

  private def offerWait(a: A, c: MultiAssignCancelable): CancelableFuture[Unit] = {
    val p = Promise[Unit]()
    sleepThenRepeat[Boolean, Unit](producersAwaiting, () => tryOfferUnsafe(a), offerTest, offerMap, p, c)
    CancelableFuture(p.future, c)
  }

  private def toSeq(buffer: ArrayBuffer[A]): Seq[A] =
    buffer.toArray[Any].toSeq.asInstanceOf[Seq[A]]

  private[this] val pollQueue: () => A = () => tryPollUnsafe()
  private[this] val pollTest: A => Boolean = _ != null
  private[this] val pollMap: A => A = a => a
  private[this] val offerTest: Boolean => Boolean = x => x
  private[this] val offerMap: Boolean => Unit = _ => ()

  @tailrec
  private def sleepThenRepeat[T, U](
    state: AtomicAny[CancelablePromise[Unit]],
    f: () => T,
    filter: T => Boolean,
    map: T => U,
    cb: Promise[U],
    token: MultiAssignCancelable
  ): Unit = {

    // Registering intention to sleep via promise
    state.get() match {
      case null =>
        val ref = CancelablePromise[Unit]()
        if (!state.compareAndSet(null, ref))
          sleepThenRepeat(state, f, filter, map, cb, token)
        else
          sleepThenRepeat_Step2TryAgainThenSleep(state, f, filter, map, cb, token)(ref)

      case ref =>
        sleepThenRepeat_Step2TryAgainThenSleep(state, f, filter, map, cb, token)(ref)
    }
  }

  private def sleepThenRepeat_Step2TryAgainThenSleep[T, U](
    state: AtomicAny[CancelablePromise[Unit]],
    f: () => T,
    filter: T => Boolean,
    map: T => U,
    cb: Promise[U],
    token: MultiAssignCancelable
  )(p: CancelablePromise[Unit]): Unit = {

    // Async boundary, for fairness reasons; also creates a full
    // memory barrier between the promise registration and what follows
    scheduler.execute { () =>
      // Trying to read one more time
      val value = f()
      if (filter(value)) {
        cb.success(map(value))
        ()
      } else {
        // Awaits on promise, then repeats
        token := p.subscribe { _ =>
          sleepThenRepeat_Step3Awaken(state, f, filter, map, cb, token)
        }
        ()
      }
    }
  }

  private def sleepThenRepeat_Step3Awaken[T, U](
    state: AtomicAny[CancelablePromise[Unit]],
    f: () => T,
    filter: T => Boolean,
    map: T => U,
    cb: Promise[U],
    token: MultiAssignCancelable
  ): Unit = {

    // Trying to read
    val value = f()
    if (filter(value)) {
      cb.success(map(value))
      ()
    } else {
      // Go to sleep again
      sleepThenRepeat(state, f, filter, map, cb, token)
    }
  }
}

object AsyncQueue {
  /**
    * Builds a limited capacity and back-pressured [[AsyncQueue]].
    *
    * @see [[unbounded]] for building an unbounded queue that can use the
    *      entire memory available to the process.
    *
    * @param capacity is the maximum capacity of the internal buffer; note
    *        that due to performance optimizations, the actual capacity gets
    *        rounded to a power of 2, so the actual capacity may be slightly
    *        different than the one specified
    *
    * @param s is a [[Scheduler]], needed for asynchronous waiting on `poll`
    *        when the queue is empty or for back-pressuring `offer` when the
    *        queue is full
    */
  @UnsafeBecauseImpure
  def bounded[A](capacity: Int)(implicit s: Scheduler): AsyncQueue[A] =
    withConfig(BufferCapacity.Bounded(capacity), MPMC)

  /**
    * Builds an unlimited [[AsyncQueue]] that can use the entire memory
    * available to the process.
    *
    * @see [[bounded]] for building a limited capacity queue.
    *
    * @param chunkSizeHint is an optimization parameter — the underlying
    *        implementation may use an internal buffer that uses linked
    *        arrays, in which case the "chunk size" represents the size
    *        of a chunk; providing it is just a hint, it may or may not be
    *        used
    *
    * @param s is a [[Scheduler]], needed for asynchronous waiting on `poll`
    *        when the queue is empty or for back-pressuring `offer` when the
    *        queue is full
    */
  @UnsafeBecauseImpure
  def unbounded[A](chunkSizeHint: Option[Int] = None)(implicit s: Scheduler): AsyncQueue[A] =
    withConfig(BufferCapacity.Unbounded(chunkSizeHint), MPMC)

  /**
    * Builds an [[AsyncQueue]] with fine-tuned config parameters.
    *
    * This is unsafe due to problems that can happen via selecting the
    * wrong [[ChannelType]], so use with care.
    *
    * @param capacity specifies the [[BufferCapacity]], which can be either
    *        "bounded" (with a maximum capacity), or "unbounded"
    *
    * @param channelType (UNSAFE) specifies the concurrency scenario, for
    *        fine tuning the performance
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def withConfig[A](capacity: BufferCapacity, channelType: ChannelType)(implicit
    scheduler: Scheduler
  ): AsyncQueue[A] = {

    new AsyncQueue[A](capacity, channelType)
  }
}
