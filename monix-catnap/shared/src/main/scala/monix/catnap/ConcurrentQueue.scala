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

package monix.catnap

import cats.effect.{Async, Timer}
import cats.implicits._
import monix.execution.ChannelType
import monix.execution.ChannelType.MPMC
import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.internal.collection.{ConcurrentQueue => LowLevelQueue}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

/**
  * A high-performance, back-pressured, generic concurrent queue implementation.
  *
  * This is the pure and generic version of [[monix.execution.AsyncQueue]].
  *
  * ==Example==
  *
  * {{{
  *   import cats.implicits._
  *   import cats.effect._
  *   import monix.execution.Scheduler.global
  *
  *   // For being able to do IO.start
  *   implicit val cs = global.contextShift[IO]
  *   // We need a `Timer` for this to work
  *   implicit val timer = global.timer[IO]
  *
  *   def consumer(queue: ConcurrentQueue[IO, Int], index: Int): IO[Unit] =
  *     queue.poll.flatMap { a =>
  *       println(s"Worker $$index: $$a")
  *       consumer(queue, index)
  *     }

  *   for {
  *     queue     <- ConcurrentQueue[IO].of[Int](capacity = 32)
  *     consumer1 <- consumer(queue, 1).start
  *     consumer2 <- consumer(queue, 1).start
  *     // Pushing some samples
  *     _         <- queue.offer(1)
  *     _         <- queue.offer(2)
  *     _         <- queue.offer(3)
  *     // Stopping the consumer loops
  *     _         <- consumer1.cancel
  *     _         <- consumer2.cancel
  *   } yield ()
  * }}}
  *
  * ==Back-Pressuring and the Polling Model==
  *
  * The initialized queue is limited to a specified buffer size, a size that
  * could be rounded to a power of 2, so you can't rely on it to be precise.
  *
  * On [[offer]], when the queue is full, the implementation back-pressures
  * until the queue has room again in its internal buffer, the future being
  * completed when the value was pushed successfully. Similarly [[poll]] awaits
  * the queue to have items in it.
  *
  * Currently the implementation is optimized for speed. In a producer-consumer
  * pipeline the best performance is achieved if the producer(s) and the
  * consumer(s) do not contend for the same resources. This is why when
  * doing asynchronous waiting for the queue to be empty or full, the
  * implementation does so by repeatedly retrying the operation, with
  * asynchronous boundaries and delays, until it succeeds.
  *
  * The implementation does ensure fairness. This is the `retryDelay`
  * parameter, available in [[ConcurrentQueue$.configure ConcurrentQueue.configure]]
  * being set by default to `10 millis`, with the algorithm working like so:
  *
  *  - for the first 10 milliseconds, we keep retrying repeatedly, with
  *    inserted async boundaries between requests (to ensure fairness)
  *  - after the first 10 milliseconds, we insert a delay of 10 millis
  *    between retries
  *
  * ==Multi-threading Scenario==
  *
  * This queue support a [[monix.execution.ChannelType ChannelType]]
  * configuration, for fine tuning depending on the needed multi-threading
  * scenario. And this can yield better performance:
  *
  *   - [[monix.execution.ChannelType.MPMC MPMC]]:
  *     multi-producer, multi-consumer
  *   - [[monix.execution.ChannelType.MPSC MPSC]]:
  *     multi-producer, single-consumer
  *   - [[monix.execution.ChannelType.SPMC SPMC]]:
  *     single-producer, multi-consumer
  *   - [[monix.execution.ChannelType.SPSC SPSC]]:
  *     single-producer, single-consumer
  *
  * The default is `MPMC`, because that's the safest scenario.
  *
  * {{{
  *   import monix.execution.ChannelType.MPSC
  *
  *   val queue = ConcurrentQueue[IO].configure(
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
final class ConcurrentQueue[F[_], A] private (
  capacity: Int,
  channelType: ChannelType,
  retryDelay: FiniteDuration)
  (implicit F: Async[F], timer: Timer[F])
  extends Serializable {

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
  def tryOffer(a: A): F[Boolean] = F.delay(queue.offer(a) == 0)

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
  def tryPoll: F[Option[A]] = tryPollRef

  /** Fetches a value from the queue, or if the queue is empty continuously
    * polls the queue until a value is made available.
    *
    * @return a task that when evaluated, will eventually complete
    *         after the value has been successfully pushed in the queue
    */
  def poll: F[A] = pollRef

  /** Pushes a value in the queue, or if the queue is full, then repeats the
    * operation until it succeeds.
    *
    * @return a task that when evaluated, will complete with a value,
    *         or wait until such a value is ready
    */
  def offer(a: A): F[Unit] = F.suspend {
    queue.offer(a) match {
      case 0 => F.unit
      case _ => offerWait(a)
    }
  }

  /** Pushes multiple values in the queue. Back-pressures if the queue is full.
    *
    * @return a task that will eventually complete when the
    *         push has succeeded; it can also be cancelled, interrupting the
    *         waiting
    */
  def offerMany(seq: A*): F[Unit] = {
    // Recursive, async loop
    def loop(cursor: Iterator[A]): F[Unit] = {
      var elem: A = null.asInstanceOf[A]
      var hasCapacity = true
      // Happy path
      while (hasCapacity && cursor.hasNext) {
        elem = cursor.next()
        hasCapacity = queue.offer(elem) == 0
      }
      if (!hasCapacity) {
        offerWait(elem).flatMap(_ => loop(cursor))
      } else {
        F.unit
      }
    }

    F.suspend {
      val cursor = seq.iterator
      loop(cursor)
    }
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
  def drain(minLength: Int, maxLength: Int): F[Seq[A]] =
    F.suspend {
      assert(minLength <= maxLength, s"minSize ($minLength) <= bufferSize ($maxLength")
      val buffer = ArrayBuffer.empty[A]
      // Happy path
      val length = queue.drainToBuffer(buffer, maxLength)
      if (length >= minLength) {
        F.pure(toSeq(buffer))
      } else {
        // Going async
        F.asyncF { cb =>
          polled[Int, Seq[A]](
            () => queue.drainToBuffer(buffer, maxLength - buffer.length),
            _ => buffer.length >= minLength,
            _ => toSeq(buffer),
            cb)
        }
      }
    }

  /** Removes all items from the queue.
    *
    * Called from the consumer thread, subject to the restrictions appropriate
    * to the implementation indicated by
    * [[monix.execution.ChannelType ChannelType]].
    *
    * '''WARNING:''' the `clear` operation should be done on the consumer side,
    * so it must be called from the same thread(s) that call [[poll]].
    */
  def clear: F[Unit] = clearRef

  private def offerWait(a: A): F[Unit] =
    F.asyncF(cb => polled[Int, Unit](() => queue.offer(a), offerTest, offerId, cb))

  private def polled[T, U](f: () => T, test: T => Boolean, map: T => U, cb: Either[Throwable, U] => Unit): F[Unit] =
    timer.clock.monotonic(NANOSECONDS).flatMap { start =>
      var task: F[Unit] = F.unit
      val bind: Unit => F[Unit] = _ => task
      task = F.suspend {
        val value = f()
        if (test(value)) {
          cb(Right(map(value)))
          F.unit
        } else {
          polledLoop(task, bind, start)
        }
      }
      F.flatMap(asyncBoundary)(bind)
    }

  private def polledLoop[T, U](task: F[Unit], bind: Unit => F[Unit], start: Long): F[Unit] =
    timer.clock.monotonic(NANOSECONDS).flatMap { now =>
      val next = if (now - start < retryDelayNanos)
        asyncBoundary
      else
        timer.sleep(retryDelay)

      F.flatMap(next)(bind)
    }

  private[this] val queue: LowLevelQueue[A] =
    LowLevelQueue(capacity, channelType)
  private[this] val retryDelayNanos =
    retryDelay.toNanos

  private[this] val pollQueue: () => A = () => queue.poll()
  private[this] val pollTest: A => Boolean = _ != null
  private[this] val pollId: A => A = a => a
  private[this] val offerTest: Int => Boolean = _ == 0
  private[this] val offerId: Int => Unit = _ => ()

  private[this] val asyncBoundary: F[Unit] =
    timer.sleep(Duration.Zero)

  /** Cached implementation for [[tryPoll]]. */
  private[this] val tryPollRef =
    F.delay(Option(queue.poll()))

  /** Cached implementation for [[poll]]. */
  private[this] val pollRef = F.suspend[A] {
    val happy = queue.poll()
    if (happy != null)
      F.pure(happy)
    else
      F.asyncF { cb =>
        polled[A, A](pollQueue, pollTest, pollId, cb)
      }
  }

  /** Cached implementation for [[clear]]. */
  private[this] val clearRef = F.delay(queue.clear())

  private def toSeq(buffer: ArrayBuffer[A]): Seq[A] =
    buffer.toArray[Any].toSeq.asInstanceOf[Seq[A]]
}

/**
  * @define capacityDesc is the maximum capacity of the internal buffer; note
  *         that due to performance optimizations, the actual capacity gets
  *         rounded to a power of 2, so the actual capacity may be slightly
  *         different than the one specified
  *
  * @define channelTypeDesc (UNSAFE) specifies the concurrency scenario, for
  *         fine tuning the performance
  *
  * @define retryDelayDesc configures the polling strategy, see the documentation
  *         on [[ConcurrentQueue]].
  */
object ConcurrentQueue {
  /**
    * Builds an [[ConcurrentQueue]] value for `F` data types that are either
    * `Async`.
    *
    * This builder uses the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type]]
    * technique.
    */
  def apply[F[_]](implicit F: Async[F]): ApplyBuilders[F] =
    new ApplyBuilders[F](F)

  /**
    * Builds an [[ConcurrentQueue]].
    *
    * @param capacity $capacityDesc
    */
  def of[F[_], A](capacity: Int)(implicit F: Async[F], timer: Timer[F]): F[ConcurrentQueue[F, A]] =
    configure(capacity)

  /**
    * Builds an [[ConcurrentQueue]] with fined tuned config parameters.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong [[monix.execution.ChannelType ChannelType]],
    * so use with care.
    *
    * @param capacity $capacityDesc
    * @param channelType $channelTypeDesc
    * @param retryDelay $retryDelayDesc
    */
  @UnsafeProtocol
  def configure[F[_], A](
    capacity: Int,
    channelType: ChannelType = MPMC,
    retryDelay: FiniteDuration = 10.millis)
    (implicit F: Async[F], timer: Timer[F]): F[ConcurrentQueue[F, A]] = {

    F.delay(unsafe(capacity, channelType, retryDelay))
  }

  /**
    * The unsafe version of the [[ConcurrentQueue.of]] builder.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong [[monix.execution.ChannelType ChannelType]],
    * so use with care.
    *
    * '''UNSAFE BECAUSE IMPURE:''' this builder violates referential
    * transparency, as the queue keeps internal, shared state. Only use when
    * you know what you're doing, otherwise prefer [[ConcurrentQueue.configure]]
    * or [[ConcurrentQueue.of]].
    *
    * @param capacity $capacityDesc
    * @param channelType $channelTypeDesc
    * @param retryDelay $retryDelayDesc
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def unsafe[F[_], A](
    capacity: Int,
    channelType: ChannelType = MPMC,
    retryDelay: FiniteDuration = 10.millis)
    (implicit F: Async[F], timer: Timer[F]): ConcurrentQueue[F, A] = {

    new ConcurrentQueue[F, A](capacity, channelType, retryDelay)(F, timer)
  }

  /**
    * Returned by the [[apply]] builder.
    */
  final class ApplyBuilders[F[_]](val F: Async[F]) extends AnyVal {
    /**
      * @see documentation for [[ConcurrentQueue.of]]
      */
    def of[A](capacity: Int)(implicit timer: Timer[F]): F[ConcurrentQueue[F, A]] =
      ConcurrentQueue.of(capacity)(F, timer)

    /**
      * @see documentation for [[ConcurrentQueue.configure]]
      */
    def configure[A](capacity: Int, channelType: ChannelType = MPMC, retryDelay: FiniteDuration = 10.millis)
      (implicit timer: Timer[F]): F[ConcurrentQueue[F, A]] =
      ConcurrentQueue.configure(capacity, channelType, retryDelay)(F, timer)

    /**
      * @see documentation for [[ConcurrentQueue.unsafe]]
      */
    def unsafe[A](capacity: Int, channelType: ChannelType = MPMC, retryDelay: FiniteDuration = 10.millis)
      (implicit timer: Timer[F]): ConcurrentQueue[F, A] =
      ConcurrentQueue.unsafe(capacity, channelType, retryDelay)(F, timer)
  }
}
