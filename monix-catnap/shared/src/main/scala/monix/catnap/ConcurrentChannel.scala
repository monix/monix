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

import cats.implicits._
import cats.effect.{Async, Resource, Timer}
import monix.execution.BufferCapacity.{Bounded, Unbounded}
import monix.execution.ChannelType.MPMC
import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.atomic.AtomicAny
import monix.execution.{BufferCapacity, ChannelType}
import monix.execution.internal.collection.{ConcurrentQueue => LowLevelQueue}

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

/**
  * The `ConcurrentChannel` can be used to model complex producer-consumer
  * communication channels.
  *
  * It exposes these fundamental operations:
  *
  *  - [[push]] for pushing single events to consumers (producer side)
  *  - [[pushMany]] for pushing event sequences to consumers (producer side)
  *  - [[halt]] for pushing the final completion event to all consumers (producer side)
  *  - [[pull]] for consuming incoming from the channel (consumer side)
  *  - [[pullMany]] for consuming multiple events from the channel (consumer side)
  *  - [[broadcast]] for duplicating the channel, such that an event pushed on
  *    any one channel, either the source or the duplicate, gets published to both
  *
  * Thus compared with a plain [[ConcurrentQueue]], the `ConcurrentChannel` gains
  * these two fundamental capabilities:
  *
  *
  * ==Comparison with ConcurrentQueue==
  *
  * `ConcurrentChannel` is similar with a [[ConcurrentQueue]], but with these
  * added capabilities:
  *
  *  - [[broadcast]]: for creating broadcasting setups
  *  - [[halt]]: for pushing a final completion event, closing the channel
  *    and disallowing other events from being pushed
  *
  * Also while you can see similarities between [[ConcurrentQueue.offer]]
  * and [[push ConcurrentChannel.push]] or between [[ConcurrentQueue.poll]]
  * and [[pull ConcurrentChannel.pull]], note the different signatures:
  *
  * `ConcurrentChannel` has this restrictions: it needs to signal when the
  * channel has been closed for both the producer and the consumer.
  *
  * ==Back-Pressuring and the Polling Model==
  *
  * The initialized channel can be limited to a maximum buffer size, a size
  * that could be rounded to a power of 2, so you can't rely on it to be
  * precise. Such a bounded queue can be initialized via
  * [[monix.catnap.ConcurrentChannel.bounded ConcurrentChannel.bounded]].
  * Also see [[monix.execution.BufferCapacity BufferCapacity]], the
  * configuration parameter that can be passed in the
  * [[monix.catnap.ConcurrentChannel.custom ConcurrentChannel.custom]]
  * builder.
  *
  * On [[push]], when the queue is full, the implementation back-pressures
  * until the channel has room again in its internal buffer(s), the future being
  * completed when the value was pushed successfully. Similarly [[pull]] awaits
  * the channel to have items in it. This works for both bounded and unbounded
  * channels.
  *
  * For both `push` and `pull`, in case awaiting a result happens, the
  * implementation does so asynchronously, without any threads being blocked.
  *
  * ==Multi-threading Scenario==
  *
  * This channel supports a [[monix.execution.ChannelType ChannelType]]
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
  *   import monix.execution.BufferCapacity.Bounded
  *
  *   val queue = ConcurrentChannel[IO].custom[Int](
  *     capacity = Bounded(128),
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
  *
  * ==Credits==
  *
  * Inspired by Haskell's
  * [[https://hackage.haskell.org/package/base/docs/Control-Concurrent-ConcurrentChannel.html Control.Concurrent.ConcurrentChannel]],
  * but note that this isn't a straight port — e.g. the `ConcurrentChannel` is
  * back-pressured and allows for termination (via [[halt]]), which changes
  * its semantics significantly.
  */
final class ConcurrentChannel[F[_], E, A] private (
  state: AtomicAny[ConcurrentChannel.State[F, E, A]],
  capacity: BufferCapacity,
  channelType: ChannelType,
  retryDelay: FiniteDuration = 10.millis)
  (implicit F: Async[F], timer: Timer[F])
  extends ProducerF[F, E, A] {

  import ConcurrentChannel._

  // Internal queue of the `ConcurrentChannel`
  private[this] val queue: LowLevelQueue[A] = LowLevelQueue(capacity, channelType)

  /**
    * Publishes an event on the channel.
    *
    * If the internal buffer is full, it asynchronously waits until the
    * operation succeeds, or until the channel is halted.
    *
    * If the channel has been halted (via [[halt]]), then nothing gets
    * published, the function eventually returning a `false` value, which
    * signals that no more values can be published on the channel.
    *
    * ==Example==
    *
    * {{{
    *   sealed trait Complete
    *   object Complete extends Complete
    *
    *   def range(from: Int, until: Int, increment: Int = 1)
    *     (channel: ConcurrentChannel[F, Complete, Int]): F[Unit] = {
    *
    *     if (from != until)
    *       channel.push(from).flatMap {
    *         case true =>
    *           range(from + increment, until, increment)(channel)
    *         case false =>
    *           F.unit // we need to stop
    *       }
    *     else // we're done, close the channel
    *       channel.halt(Complete)
    *   }
    * }}}
    *
    * @return a boolean that is `true` if the value was pushed on the internal
    *         queue and the producer can push more values, or `false` if the
    *         channel is halted and cannot receive any more events
    */
  def push(a: A): F[Boolean] = {
    val task = pushToOurQueue(a)
    state.get() match {
      case null => // happy path
        task
      case Connected(refs) => // broadcasting?
        triggerBroadcast[F, E, A](task, refs, _.push(a))
      case Halt(_) =>
        F.pure(false)
    }
  }

  /** Internal API — part of the [[push]] implementation. */
  private def pushToOurQueue(a: A): F[Boolean] =
    F.suspend {
      (tryPushToOurQueue(a) : @switch) match {
        case Repeat =>
          F.asyncF(cb => polled(() => tryPushToOurQueue(a), pushFilter, pushMap, cb))
        case Continue =>
          F.pure(true)
        case Stop =>
          F.pure(false)
      }
    }

  /** Internal API — Part of the [[push]] implementation. */
  private def tryPushToOurQueue(a: A): Ack = {
    if (queue.offer(a) == 0)
      Continue
    else state.get() match {
      case Halt(_) => Stop
      case _ => Repeat
    }
  }

  /**
    * Publishes multiple events on the channel.
    *
    * If the channel has been halted (via [[halt]]), then the publishing is
    * interrupted, the function returning a `false` value signalling that
    * the channel was halted and can no longer receive any more events.
    *
    * ==Example==
    *
    * {{{
    *   sealed trait Complete
    *   object Complete extends Complete
    *
    *   def range(from: Int, until: Int, increment: Int = 1)
    *     (channel: ConcurrentChannel[F, Complete, Int]): F[Unit] = {
    *
    *     channel.pushMany0(Range(from, until, increment)).flatMap {
    *       case true =>
    *         channel.halt(Complete)
    *       case false =>
    *         F.unit // was already halted, do nothing else
    *     }
    *   }
    * }}}
    *
    * @return a boolean that is `true` if all the values were pushed on the
    *         internal queue and the producer can push more values, or `false`
    *         if the channel is halted and cannot receive any more events
    */
  def pushMany(seq: Iterable[A]): F[Boolean] = {
    def loop(cursor: Iterator[A]): F[Boolean] = {
      var elem: A = null.asInstanceOf[A]
      var hasCapacity = true
      // Happy path
      while (hasCapacity && cursor.hasNext) {
        elem = cursor.next()
        hasCapacity = queue.offer(elem) == 0
      }
      if (!hasCapacity) {
        val offerWait = F.asyncF[Ack](cb => polled(() => tryPushToOurQueue(elem), pushFilter, pushManyMap, cb))
        offerWait.flatMap {
          case Continue => loop(cursor)
          case Stop => F.pure(false)
        }
      } else {
        F.pure(true)
      }
    }

    F.suspend {
      state.get() match {
        case null => loop(seq.iterator)
        case Connected(refs) =>
          triggerBroadcast[F, E, A](loop(seq.iterator), refs, _.pushMany(seq))
        case Halt(_) =>
          F.pure(false)
      }
    }
  }

  /**
    * Stops the channel and sends a halt event to all current and future
    * consumers.
    *
    * Consumers (via [[pull]] or [[pullMany]]) will receive a `Left(e)`
    * event after [[halt]].
    */
  def halt(e: E): F[Unit] =
    F.delay {
      state.transform {
        case null | Connected(_) => Halt(e)
        case halt @ Halt(_) => halt
      }
    }

  /**
    * Pulls
    */
  def pull: F[Either[E, A]] = pullRef

  /** Actual implementation for [[pull]]. */
  private[this] val pullRef: F[Either[E, A]] = {
    def end(e: E): Either[E, A] =
      queue.poll() match {
        case null => Left(e)
        case a => Right(a)
      }

    val task: () => Either[E, A] = () => queue.poll() match {
      case null =>
        state.get() match {
          case Halt(e) => end(e)
          case _ => null.asInstanceOf[Either[E, A]]
        }
      case a =>
        Right(a)
    }

    F.suspend {
      task() match {
        case null =>
          F.asyncF(cb => polled(
            task,
            pullFilter,
            pullMap.asInstanceOf[Either[E, A] => Either[E, A]],
            cb
          ))
        case value =>
          F.pure(value)
      }
    }
  }

  def pullMany(maxLength: Int): F[Either[E, Seq[A]]] = {
    def end(buffer: ArrayBuffer[A], maxLength: Int, e: E): Either[E, Seq[A]] = {
      queue.drainToBuffer(buffer, maxLength)
      if (buffer.isEmpty)
        Left(e)
      else
        Right(toSeq(buffer))
    }

    def task(buffer: ArrayBuffer[A], maxLength: Int): Either[E, Seq[A]] =
      if (queue.drainToBuffer(buffer, maxLength) > 0)
        Right(toSeq(buffer))
      else state.get() match {
        case Halt(e) => end(buffer, maxLength, e)
        case _ => null.asInstanceOf[Either[E, Seq[A]]]
      }

    F.suspend[Either[E, Seq[A]]] {
      val buffer = ArrayBuffer.empty[A]
      val length = queue.drainToBuffer(buffer, maxLength)

      if (length > 0)
        F.pure(Right(toSeq(buffer)))
      else state.get() match {
        case Halt(e) => F.pure(end(buffer, maxLength, e))
        case _ =>
          F.asyncF(cb => polled(
            () => task(buffer, maxLength),
            pullFilter,
            pullMap.asInstanceOf[Either[E, Seq[A]] => Either[E, Seq[A]]],
            cb
          ))
      }
    }
  }

  /**
    * Duplicate a [[ConcurrentChannel]]: the duplicate channel begins empty, but data
    * written to either channel from then on will be available from both.
    *
    * Hence this creates a kind of broadcast channel, where data written by
    * anyone is seen by everyone else.
    */
  def broadcast: Resource[F, ConcurrentChannel[F, E, A]] = duplicateRef

  /** Actual implementation for [[broadcast]]. */
  private[this] val duplicateRef: Resource[F, ConcurrentChannel[F, E, A]] =
    Resource.make {
      F.delay {
        val ref: ConcurrentChannel[F, E, A] = new ConcurrentChannel(state, capacity, channelType)
        state.transform {
          case null => Connected(Set(ref))
          case Connected(refs) => Connected(refs + ref)
          case halt @ Halt(_) => halt
        }
        ref
      }
    } { ref =>
      F.delay {
        state.transform {
          case Connected(refs) => Connected(refs - ref)
          case other => other
        }
      }
    }

  // Internal, reusable values
  private[this] val retryDelayNanos = retryDelay.toNanos
  private[this] val asyncBoundary: F[Unit] = timer.sleep(Duration.Zero)

  private def polled[T, U](f: () => T, filter: T => Boolean, map: T => U,  cb: Either[Throwable, U] => Unit): F[Unit] =
    timer.clock.monotonic(NANOSECONDS).flatMap { start =>
      var task: F[Unit] = F.unit
      val bind: Unit => F[Unit] = _ => task
      task = F.suspend {
        val value = f()
        if (filter(value)) {
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
}

object ConcurrentChannel {
  /**
    * Builds a limited capacity and back-pressured [[ConcurrentChannel]].
    *
    * @see [[unbounded]] for building an unbounded channel that can use the
    *      entire memory available to the process.
    *
    * @param capacity is the maximum capacity of the internal buffer; note
    *        that due to performance optimizations, the capacity of the internal
    *        buffer can get rounded to a power of 2, so the actual capacity may
    *        be slightly different than the one specified
    *
    * @param timer $timerParam
    * @param F $asyncParam
    */
  def bounded[F[_], E, A](capacity: Int)(implicit F: Async[F], timer: Timer[F]): F[ConcurrentChannel[F, E, A]] =
    custom(Bounded(capacity), MPMC)

  /**
    * Builds an unlimited [[ConcurrentChannel]] that can use the entire memory
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
    * @param timer $timerParam
    * @param F $asyncParam
    */
  def unbounded[F[_], E, A](chunkSizeHint: Option[Int] = None)
    (implicit F: Async[F], timer: Timer[F]): F[ConcurrentChannel[F, E, A]] =
    custom(Unbounded(chunkSizeHint), MPMC)

  /**
    * Builds an [[ConcurrentChannel]] with fined tuned config parameters.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong [[monix.execution.ChannelType ChannelType]],
    * so use with care.
    *
    * @param capacity $bufferCapacityParam
    * @param channelType $channelTypeDesc
    * @param timer $timerParam
    * @param F $asyncParam
    */
  @UnsafeProtocol
  def custom[F[_], E, A](
    capacity: BufferCapacity,
    channelType: ChannelType)
    (implicit F: Async[F], timer: Timer[F]): F[ConcurrentChannel[F, E, A]] = {

    F.delay(unsafe(capacity, channelType))
  }

  /**
    * The unsafe version of the [[ConcurrentChannel.bounded]] builder.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong [[monix.execution.ChannelType ChannelType]],
    * so use with care.
    *
    * '''UNSAFE BECAUSE IMPURE:''' this builder violates referential
    * transparency, as the queue keeps internal, shared state. Only use when
    * you know what you're doing, otherwise prefer [[ConcurrentChannel.custom]]
    * or [[ConcurrentChannel.bounded]].
    *
    * @param capacity $bufferCapacityParam
    * @param channelType $channelTypeDesc
    * @param timer $timerParam
    * @param F $asyncParam
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def unsafe[F[_], E, A](
    capacity: BufferCapacity,
    channelType: ChannelType = MPMC)
    (implicit F: Async[F], timer: Timer[F]): ConcurrentChannel[F, E, A] = {

    new ConcurrentChannel[F, E, A](AtomicAny(null), capacity, channelType)(F, timer)
  }

  private sealed abstract class State[F[_], E, A]

  private final case class Connected[F[_], E, A](
    refs: Set[ConcurrentChannel[F, E, A]])
    extends State[F, E, A]

  private final case class Halt[F[_], E, A](e: E)
    extends State[F, E, A]

  private type Ack = Int
  private final val Continue = 0
  private final val Repeat = 1
  private final val Stop = 2

  // Internal, reusable references
  private val pullFilter = (x: Either[Any, Any]) => x != null
  private val pullMap = (x: Any) => x
  private val pushFilter = (x: Ack) => x != Repeat
  private val pushMap = (x: Ack) => x != Stop
  private val pushManyMap = (x: Ack) => x

  private def toSeq[A](buffer: ArrayBuffer[A]): Seq[A] =
    buffer.toArray[Any].toSeq.asInstanceOf[Seq[A]]

  private def triggerBroadcast[F[_], E, A](seed: F[Boolean], refs: Set[ConcurrentChannel[F, E, A]], f: ConcurrentChannel[F, E, A] => F[Boolean])
    (implicit F: Async[F]): F[Boolean] = {

    def loop(cursor: Iterator[ConcurrentChannel[F, E, A]], bind: Any => F[Boolean]): F[Boolean] =
      if (cursor.hasNext) {
        val task = f(cursor.next())
        val bindRef = if (bind ne null) bind else {
          var bindVar: Any => F[Boolean] = null
          bindVar = {
            case true => loop(cursor, bindVar)
            case false => F.pure(false)
          }
          bindVar
        }
        F.flatMap(task)(bindRef)
      } else {
        F.pure(true)
      }

    F.flatMap(seed) { _ =>
      loop(refs.iterator, null)
    }
  }

//  private final class ChanProducer[F[_], E, A] private (
//    queue: LowLevelQueue[A],
//    state: AtomicAny[ConcurrentChannel.State[F, E, A]],
//    retryDelay: FiniteDuration)
//    (implicit F: Async[F], timer: Timer[F])
//    extends Helpers(queue, state, retryDelay)(F, timer)
//    with ProducerF[F, E, A] {
//
//
//  }
//
//
//  private final class ChanConsumer[F[_], E, A] private (
//    queue: LowLevelQueue[A],
//    state: AtomicAny[ConcurrentChannel.State[F, E, A]],
//    retryDelay: FiniteDuration)
//    (implicit F: Async[F], timer: Timer[F])
//    extends Helpers(queue, state, retryDelay)(F, timer)
//    with ConsumerF[F, E, A] {
//
//    def pull: F[Either[E, A]] = pullRef
//    private[this] val pullRef: F[Either[E, A]] = {
//      def end(e: E): Either[E, A] =
//        queue.poll() match {
//          case null => Left(e)
//          case a => Right(a)
//        }
//
//      val task: () => Either[E, A] = () => queue.poll() match {
//        case null =>
//          state.get() match {
//            case Halt(e) => end(e)
//            case _ => null.asInstanceOf[Either[E, A]]
//          }
//        case a =>
//          Right(a)
//      }
//
//      F.suspend {
//        task() match {
//          case null =>
//            F.asyncF(cb => polled(
//              task,
//              pullFilter,
//              pullMap.asInstanceOf[Either[E, A] => Either[E, A]],
//              cb
//            ))
//          case value =>
//            F.pure(value)
//        }
//      }
//    }
//
//    def pullMany(maxLength: Ack): F[Either[E, Seq[A]]] = {
//      def end(buffer: ArrayBuffer[A], maxLength: Int, e: E): Either[E, Seq[A]] = {
//        queue.drainToBuffer(buffer, maxLength)
//        if (buffer.isEmpty)
//          Left(e)
//        else
//          Right(toSeq(buffer))
//      }
//
//      def task(buffer: ArrayBuffer[A], maxLength: Int): Either[E, Seq[A]] =
//        if (queue.drainToBuffer(buffer, maxLength) > 0)
//          Right(toSeq(buffer))
//        else state.get() match {
//          case Halt(e) => end(buffer, maxLength, e)
//          case _ => null.asInstanceOf[Either[E, Seq[A]]]
//        }
//
//      F.suspend[Either[E, Seq[A]]] {
//        val buffer = ArrayBuffer.empty[A]
//        val length = queue.drainToBuffer(buffer, maxLength)
//
//        if (length > 0)
//          F.pure(Right(toSeq(buffer)))
//        else state.get() match {
//          case Halt(e) => F.pure(end(buffer, maxLength, e))
//          case _ =>
//            F.asyncF(cb => polled(
//              () => task(buffer, maxLength),
//              pullFilter,
//              pullMap.asInstanceOf[Either[E, Seq[A]] => Either[E, Seq[A]]],
//              cb
//            ))
//        }
//      }
//    }
//  }
//
//  private abstract class Helpers[F[_], E, A](
//    queue: LowLevelQueue[A],
//    state: AtomicAny[ConcurrentChannel.State[F, E, A]],
//    retryDelay: FiniteDuration)
//    (implicit F: Async[F], timer: Timer[F]) {
//
//    // Internal, reusable values
//    private[this] val retryDelayNanos = retryDelay.toNanos
//    private[this] val asyncBoundary: F[Unit] = timer.sleep(Duration.Zero)
//
//    protected def polled[T, U](f: () => T, filter: T => Boolean, map: T => U,  cb: Either[Throwable, U] => Unit): F[Unit] =
//      timer.clock.monotonic(NANOSECONDS).flatMap { start =>
//        var task: F[Unit] = F.unit
//        val bind: Unit => F[Unit] = _ => task
//        task = F.suspend {
//          val value = f()
//          if (filter(value)) {
//            cb(Right(map(value)))
//            F.unit
//          } else {
//            polledLoop(task, bind, start)
//          }
//        }
//        F.flatMap(asyncBoundary)(bind)
//      }
//
//    protected def polledLoop[T, U](task: F[Unit], bind: Unit => F[Unit], start: Long): F[Unit] =
//      timer.clock.monotonic(NANOSECONDS).flatMap { now =>
//        val next = if (now - start < retryDelayNanos)
//          asyncBoundary
//        else
//          timer.sleep(retryDelay)
//
//        F.flatMap(next)(bind)
//      }
//  }
}
