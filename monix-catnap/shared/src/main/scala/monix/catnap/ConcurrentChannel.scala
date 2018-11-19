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

import cats.effect.{Async, Resource, Timer}
import cats.implicits._
import monix.execution.BufferCapacity.Bounded
import monix.execution.ChannelType.{MultiConsumer, MultiProducer}
import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.atomic.AtomicAny
import monix.execution.internal.Platform
import monix.execution.internal.collection.{ConcurrentQueue => LowLevelQueue}
import monix.execution.{BufferCapacity, ChannelType}
import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

/**
  * `ConcurrentChannel` can be used to model complex producer-consumer communication channels.
  *
  * It exposes these fundamental operations:
  *
  *  - [[push]] for pushing single events to consumers (producer side)
  *  - [[pushMany]] for pushing event sequences to consumers (producer side)
  *  - [[halt]] for pushing the final completion event to all consumers (producer side)
  *  - [[consume]] for creating a [[ConsumerF]] value that can consume the
  *    incoming events from the channel
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
  *   // Completion event
  *   sealed trait Complete
  *   object Complete extends Complete
  *
  *   def logLines(consumer: ConsumerF[IO, Complete, String], index: Int): IO[Unit] =
  *     consumer.pull.flatMap {
  *       case Right(message) =>
  *         IO(println("Worker $$index: $$message"))
  *           // continue loop
  *           .flatMap(_ => logLines(consumer, index))
  *       case Left(Complete) =>
  *         IO(println("Worker $$index is done!"))
  *     }
  *
  *   for {
  *     channel <- ConcurrentChannel[IO].of[Complete, String]
  *     // Workers 1 & 2, sharing the load between them
  *     task_1_2 = channel.consume.use { ref =>
  *       (logLines(ref, 1), logLines(ref, 2)).parSequence_
  *     }
  *     consumers_1_2 <- task_1_2.start // fiber
  *     // Workers 3 & 4, receiving the same events as workers 1 & 2,
  *     // but sharing the load between them
  *     task_3_4 = channel.consume.use { ref =>
  *       (logLines(ref, 3), logLines(ref, 4)).parSequence_
  *     }
  *     consumers_3_4 <- task_3_4.start // fiber
  *     // Pushing some samples
  *     _ <- channel.push("Hello, ")
  *     _ <- channel.push("World!")
  *     // Signal there are no more events
  *     _ <- channel.halt(Complete)
  *     // Await for the completion of the consumers
  *     _ <- consumers_1_2.join
  *     _ <- consumers_3_4.join
  *   } yield ()
  * }}}
  *
  * ==Unicasting vs Broadcasting vs Multicasting==
  *
  * ''Unicasting'': A communication channel between one producer and one
  * [[ConsumerF]]. Multiple workers can share the load of processing
  * incoming events. For example in case we want to have 8 workers running in
  * parallel, you can create one [[ConsumerF]], via [[consume]] and then use it
  * for multiple workers. Internally this setup uses a single queue and whatever
  * workers you have will share it.
  *
  * ''Broadcasting:'' the same events can be sent to multiple consumers,
  * thus duplicating the load, as a broadcasting setup can be created
  * by creating and consuming from multiple [[ConsumerF]] via multiple calls
  * to [[consume]]. Internally each `ConsumerF` gets its own queue and hence
  * messages are duplicated.
  *
  * ''Multicasting:'' multiple producers can push events at the same time,
  * provided the channel's type is configured as a
  * [[monix.execution.ChannelType.MultiProducer MultiProducer]].
  *
  * ==Back-Pressuring and the Polling Model==
  *
  * When consumers get created via [[consume]], a buffer gets created and
  * assigned per consumer.
  *
  * Depending on what the [[monix.execution.BufferCapacity BufferCapacity]]
  * is configured to be, the initialized consumer can work with a maximum
  * buffer size, a size that could be rounded to a power of 2, so you can't
  * rely on it to be precise. See [[consumeCustom]] for customizing this
  * buffer on a per-consumer basis, or the
  * [[monix.catnap.ConcurrentChannel.custom ConcurrentChannel.custom]]
  * builder for setting the default used in [[consume]].
  *
  * On [[push]], when the queue is full, the implementation back-pressures
  * until the channel has room again in its internal buffer(s), the task being
  * completed when the value was pushed successfully. Similarly [[ConsumerF.pull]]
  * (returned by [[consume]]) awaits the channel to have items in it. This
  * works for both bounded and unbounded channels.
  *
  * For both `push` and `pull`, in case awaiting a result happens, the
  * implementation does so asynchronously, without any threads being blocked.
  *
  * ==Multi-threading Scenario==
  *
  * This channel supports the fine-tuning of the concurrency scenario via
  * [[monix.execution.ChannelType.ProducerSide ChannelType.ProducerSide]]
  * (see [[monix.catnap.ConcurrentChannel.custom ConcurrentChannel.custom]])
  * and the
  * [[monix.execution.ChannelType.ConsumerSide ChannelType.ConsumerSide]]
  * that can be specified per consumer (see [[consumeCustom]]).
  *
  * The default is set to
  * [[monix.execution.ChannelType.MultiProducer MultiProducer]] and
  * [[monix.execution.ChannelType.MultiConsumer MultiConsumer]], which is always
  * the safe choice, however these can be customized for better performance.
  *
  * These scenarios are available:
  *
  *   - [[monix.execution.ChannelType.MPMC MPMC]]:
  *     multi-producer, multi-consumer, when
  *     [[monix.execution.ChannelType.MultiProducer MultiProducer]]
  *     is selected on the channel's creation and
  *     [[monix.execution.ChannelType.MultiConsumer MultiConsumer]] is
  *     selected when [[consume consuming]]; this is the safe scenario and
  *     should be used as the default, especially when in doubt
  *   - [[monix.execution.ChannelType.MPSC MPSC]]:
  *     multi-producer, single-consumer, when
  *     [[monix.execution.ChannelType.MultiProducer MultiProducer]]
  *     is selected on the channel's creation and
  *     [[monix.execution.ChannelType.SingleConsumer SingleConsumer]] is
  *     selected when [[consume consuming]]; this scenario should be selected
  *     when there are multiple producers, but a single worker that consumes
  *     data sequentially (per [[ConsumerF]]); note that this means a single
  *     worker per [[ConsumerF]] instance, but you can still have multiple
  *     [[ConsumerF]] instances created, , because each [[ConsumerF]] gets its
  *     own buffer anyway
  *   - [[monix.execution.ChannelType.SPMC SPMC]]:
  *     single-producer, multi-consumer, when
  *     [[monix.execution.ChannelType.SingleProducer SingleProducer]]
  *     is selected on the channel's creation and
  *     [[monix.execution.ChannelType.MultiConsumer MultiConsumer]] is
  *     selected when [[consume consuming]]; this scenario should be selected
  *     when there are multiple workers processing data in parallel
  *     (e.g. pulling from the same [[ConsumerF]]), but a single producer that
  *     pushes data on the channel sequentially
  *   - [[monix.execution.ChannelType.SPSC SPSC]]:
  *     single-producer, single-consumer, when
  *     [[monix.execution.ChannelType.SingleProducer SingleProducer]]
  *     is selected on the channel's creation and
  *     [[monix.execution.ChannelType.SingleConsumer SingleConsumer]] is
  *     selected when [[consume consuming]]; this scenario should be selected
  *     when there is a single producer that pushes data on the channel
  *     sequentially and a single worker per [[ConsumerF]] instance that
  *     pulls data from the channel sequentially; note you can still have
  *     multiple [[ConsumerF]] instances running in parallel, because
  *     each [[ConsumerF]] gets its own buffer anyway
  *
  * The default is `MPMC`, because that's the safest scenario.
  *
  * {{{
  *   import cats.implicits._
  *   import cats.effect.IO
  *   import monix.execution.ChannelType.{SingleProducer, SingleConsumer}
  *   import monix.execution.BufferCapacity.Bounded
  *
  *   val channel = ConcurrentChannel[IO].custom[Int, Int](
  *     producerType = SingleProducer
  *   )
  *
  *   for {
  *     producer  <- channel
  *     consumer1 =  producer.consumeCustom(consumerType = SingleConsumer)
  *     consumer2 =  producer.consumeCustom(consumerType = SingleConsumer)
  *     fiber1    <- consumer1.use { ref => ref.pull }.start
  *     fiber2    <- consumer2.use { ref => ref.pull }.start
  *     _         <- producer.push(1)
  *     value1    <- fiber1.join
  *     value2    <- fiber2.join
  *   } yield {
  *     (value1, value2)
  *   }
  * }}}
  *
  * Note that in this example, even if we used `SingleConsumer` as the type
  * passed in [[consumeCustom]], we can still consume from two [[ConsumerF]]
  * instances at the same time, because each one gets its own internal buffer.
  * But you cannot have multiple workers per [[ConsumerF]] in this scenario,
  * because this would break the internal synchronization / visibility
  * guarantees.
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
  * but note that this isn't a straight port — e.g. the Monix implementation has a
  * cleaner, non-leaky interface, is back-pressured and allows for termination
  * (via [[halt]]), which changes its semantics significantly.
  */
final class ConcurrentChannel[F[_], E, A] private (
  state: AtomicAny[ConcurrentChannel.State[F, E, A]],
  defaultPerBufferCapacity: BufferCapacity,
  producerType: ChannelType.ProducerSide,
  retryDelay: FiniteDuration = 10.millis)
  (implicit F: Async[F], timer: Timer[F])
  extends ProducerF[F, E, A] {

  import ConcurrentChannel._

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
    *   import cats.implicits._
    *   import cats.effect.Sync
    *
    *   sealed trait Complete
    *   object Complete extends Complete
    *
    *   def range[F[_]](from: Int, until: Int, increment: Int = 1)
    *     (channel: ConcurrentChannel[F, Complete, Int])
    *     (implicit F: Sync[F]): F[Unit] = {
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
  def push(a: A): F[Boolean] =
    F.suspend {
      state.get() match {
        case connected @ Connected(_) =>
          // broadcasting to many?
          val arr = connected.array
          (arr.length: @switch) match {
            case 0 => helpers.continueF
            case 1 => arr(0).push(a)
            case _ => triggerBroadcast[F, E, A](helpers, arr, _.push(a))
          }
        case Halt(_) =>
          helpers.stopF
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
    *   import cats.implicits._
    *   import cats.effect.Sync
    *
    *   sealed trait Complete
    *   object Complete extends Complete
    *
    *   def range[F[_]](from: Int, until: Int, increment: Int = 1)
    *     (channel: ConcurrentChannel[F, Complete, Int])
    *     (implicit F: Sync[F]): F[Unit] = {
    *
    *     channel.pushMany(Range(from, until, increment)).flatMap {
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
  def pushMany(seq: Iterable[A]): F[Boolean] =
    F.suspend {
      state.get() match {
        case current @ Connected(_) =>
          val arr = current.array
          (arr.length: @switch) match {
            case 0 => helpers.continueF
            case 1 => arr(0).pushMany(seq)
            case _ => triggerBroadcast[F, E, A](helpers, arr, _.pushMany(seq))
          }
        case Halt(_) =>
          helpers.stopF
      }
    }

  /**
    * Stops the channel and sends a halt event to all current and future
    * consumers.
    *
    * Consumers will receive a `Left(e)` event after [[halt]] is observed.
    */
  def halt(e: E): F[Unit] =
    F.delay {
      state.transform {
        case Connected(_) => Halt(e)
        case halt @ Halt(_) => halt
      }
    }

  /**
    * Create a [[ConsumerF]] value that can be used to consume events from
    * the channel.
    *
    * Note in case multiple consumers are created, all of them will see the
    * events being pushed, so a broadcasting setup is possible. Also multiple
    * workers can consumer from the same `ConsumerF` value, to share the load.
    *
    * The returned value is a
    * [[https://typelevel.org/cats-effect/datatypes/resource.html Resource]],
    * because a consumer can be unsubscribed from the channel, with its
    * internal buffer being garbage collected.
    *
    * @see [[consumeCustom]] for fine tuning the internal buffer of the
    *      created consumer
    */
  def consume: Resource[F, ConsumerF[F, E, A]] = consumeRef
  private[this] val consumeRef = consumeCustom()

  /** Version of [[consume]] that allows for fine tuning the underlying
    * buffer used.
    *
    * There are two parameters that can be configured, per consumer, when
    * consuming from the channel:
    *
    *  - the [[monix.execution.BufferCapacity BufferCapacity]], which can be
    *    [[monix.execution.BufferCapacity.Unbounded Unbounded]], for an
    *    unlimited internal buffer in case the consumer is definitely faster
    *    than the producer, or [[monix.execution.BufferCapacity.Bounded Bounded]]
    *    in case back-pressuring a slow consumer is desirable
    *  - the [[monix.execution.ChannelType.ConsumerSide ChannelType.ConsumerSide]],
    *    which specifies if this consumer will use multiple workers in parallel
    *    or not; this is an optimization, with the safe choice being
    *    [[monix.execution.ChannelType.MultiConsumer MultiConsumer]], which
    *    specifies that multiple workers can use the created consumer in
    *    parallel, pulling data from multiple threads at the same time; whereas
    *    [[monix.execution.ChannelType.SingleConsumer SingleConsumer]] specifies
    *    that the data will be read sequentially by a single worker, not in
    *    parallel; this being a risky optimization
    *
    * @param capacity is the capacity of the internal buffer created for this
    *        consumer; see [[monix.execution.BufferCapacity BufferCapacity]]
    *
    * @param consumerType (UNSAFE) specifies the type of the consumer in a
    *        multi-threaded setting; see
    *        [[monix.execution.ChannelType.ConsumerSide ChannelType.ConsumerSide]]
    */
  @UnsafeProtocol
  def consumeCustom(
    capacity: BufferCapacity = defaultPerBufferCapacity,
    consumerType: ChannelType.ConsumerSide = MultiConsumer): Resource[F, ConsumerF[F, E, A]] = {

    val isFinished = () => state.get() match {
      case Halt(e) => Some(e)
      case _ => None
    }

    Resource.apply[F, ConsumerF[F, E, A]] {
      F.delay {
        val queue = LowLevelQueue[A](capacity, ChannelType.assemble(producerType, consumerType))
        val producer = new ChanProducer[F, E, A](queue, isFinished, helpers)
        val consumer = new ChanConsumer[F, E, A](queue, isFinished, helpers)

        state.transform {
          case connected @ Connected(_) =>
            connected.add(producer)
          case halt @ Halt(_) =>
            halt
        }

        val cancel = F.delay {
          state.transform {
            case connected @ Connected(_) =>
              connected.remove(producer)
            case other =>
              other
          }
        }

        (consumer, cancel)
      }
    }
  }

  /**
    * Awaits for the specified number of consumers to be connected.
    *
    * This is an utility to ensure that a certain number of consumers
    * are connected before we start emitting events.
    *
    * @param n is a number indicating the number of consumers that need
    *          to be connected before the returned task completes
    *
    * @return a task that will complete only after the required number
    *         of consumers are observed as being connected to the channel
    */
  def awaitConsumers(n: Int): F[Boolean] =
    F.suspend {
      testAwaitConsumers(n) match {
        case Repeat =>
          F.asyncF(cb => helpers.polled(
            () => testAwaitConsumers(n),
            pushFilter,
            pushMap,
            cb
          ))
        case other =>
          F.pure(other == Continue)
      }
    }

  private def testAwaitConsumers(n: Int): Ack =
    state.get() match {
      case connected @ Connected(_) =>
        if (connected.array.length >= n) Continue
        else Repeat
      case _ =>
        Stop
    }

  private[this] val helpers =
    new Helpers[F, E, A](retryDelay)
}

/**
  * @define producerTypeDesc (UNSAFE) specifies the concurrency scenario for
  *         the producer's side, for fine tuning that can lead to performance
  *         gains; the safe choice is
  *         [[monix.execution.ChannelType.MultiProducer MultiProducer]] and if
  *         in doubt, use it
  *
  * @define defaultPerBufferCapacityDesc specifies the default, per buffer
  *         [[monix.execution.BufferCapacity BufferCapacity]], which will
  *         be used when creating consumers via [[ConcurrentChannel.consume consume]];
  *         this is just a default and it can be overridden per individual consumer
  *         via the alternative [[ConcurrentChannel.consumeCustom consumeCustom]].
  *
  * @define asyncParam is a `cats.effect.Async` type class restriction; this
  *         queue is built to work with any `Async` data type
  *
  * @define timerParam is a `Timer`, needed for asynchronous waiting on `poll`
  *         when the queue is empty or for back-pressuring `offer` when the
  *         queue is full
  */
object ConcurrentChannel {
  /**
    * Builds an [[ConcurrentChannel]] value for `F` data types that are either
    * `Async`.
    *
    * This builder uses the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type]]
    * technique.
    */
  def apply[F[_]](implicit F: Async[F]): ApplyBuilders[F] =
    new ApplyBuilders[F](F)

  /**
    * Builds a multi-producer channel.
    *
    * This is the safe constructor.
    *
    * @see [[custom]] for fine tuning for the created channel.
    *
    * @param timer $timerParam
    * @param F $asyncParam
    */
  def of[F[_], E, A](implicit F: Async[F], timer: Timer[F]): F[ConcurrentChannel[F, E, A]] =
    custom()

  /**
    * Builds an [[ConcurrentChannel]] with fined tuned config parameters.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong
    * [[monix.execution.ChannelType.ProducerSide ChannelType.ProducerSide]],
    * so use with care.
    *
    * @param defaultPerBufferCapacity $defaultPerBufferCapacityDesc
    * @param producerType $producerTypeDesc
    * @param timer $timerParam
    * @param F $asyncParam
    */
  @UnsafeProtocol
  def custom[F[_], E, A](
    defaultPerBufferCapacity: BufferCapacity = Bounded(Platform.recommendedBatchSize),
    producerType: ChannelType.ProducerSide = MultiProducer)
    (implicit F: Async[F], timer: Timer[F]): F[ConcurrentChannel[F, E, A]] = {

    F.delay(unsafe(defaultPerBufferCapacity, producerType))
  }

  /**
    * The unsafe version of the [[ConcurrentChannel.custom]] builder.
    *
    * '''UNSAFE PROTOCOL:''' This is unsafe due to problems that can happen
    * via selecting the wrong [[monix.execution.ChannelType ChannelType]],
    * so use with care.
    *
    * '''UNSAFE BECAUSE IMPURE:''' this builder violates referential
    * transparency, as the queue keeps internal, shared state. Only use when
    * you know what you're doing, otherwise prefer [[ConcurrentChannel.custom]].
    *
    * @param defaultPerBufferCapacity $defaultPerBufferCapacityDesc
    * @param producerType $producerTypeDesc
    * @param timer $timerParam
    * @param F $asyncParam
    */
  @UnsafeProtocol
  @UnsafeBecauseImpure
  def unsafe[F[_], E, A](
    defaultPerBufferCapacity: BufferCapacity = Bounded(Platform.recommendedBatchSize),
    producerType: ChannelType.ProducerSide = MultiProducer)
    (implicit F: Async[F], timer: Timer[F]): ConcurrentChannel[F, E, A] = {

    new ConcurrentChannel[F, E, A](
      AtomicAny(State.empty),
      defaultPerBufferCapacity,
      producerType)(F, timer)
  }

  /**
    * Returned by the [[apply]] builder.
    */
  final class ApplyBuilders[F[_]](val F: Async[F]) extends AnyVal {
    /**
      * @see documentation for [[ConcurrentChannel.of]]
      */
    def of[E, A](implicit timer: Timer[F]): F[ConcurrentChannel[F, E, A]] =
      ConcurrentChannel.of(F, timer)

    /**
      * @see documentation for [[ConcurrentChannel.custom]]
      */
    def custom[E, A](
      defaultPerBufferCapacity: BufferCapacity = Bounded(Platform.recommendedBatchSize),
      producerType: ChannelType.ProducerSide = MultiProducer)
      (implicit timer: Timer[F]): F[ConcurrentChannel[F, E, A]] =
      ConcurrentChannel.custom(defaultPerBufferCapacity, producerType)(F, timer)

    /**
      * @see documentation for [[ConcurrentChannel.unsafe]]
      */
    def unsafe[E, A](
      defaultPerBufferCapacity: BufferCapacity = Bounded(Platform.recommendedBatchSize),
      producerType: ChannelType.ProducerSide = MultiProducer)
      (implicit timer: Timer[F]): ConcurrentChannel[F, E, A] =
      ConcurrentChannel.unsafe(defaultPerBufferCapacity, producerType)(F, timer)
  }

  private sealed abstract class State[F[_], E, A]

  private final case class Connected[F[_], E, A](
    refs: Set[ChanProducer[F, E, A]])
    extends State[F, E, A] {

    def add(ref: ChanProducer[F, E, A]): Connected[F, E, A] =
      Connected(refs + ref)
    def remove(ref: ChanProducer[F, E, A]): Connected[F, E, A] =
      Connected(refs - ref)

    val array = refs.toArray
  }

  private final case class Halt[F[_], E, A](e: E)
    extends State[F, E, A]

  private object State {
    def empty[F[_], E, A]: State[F, E, A] =
      emptyRef.asInstanceOf[State[F, E, A]]
    private[this] val emptyRef =
      Connected[cats.Id, Any, Any](Set.empty)
  }

  private type Ack = Int
  private final val Continue = 0
  private final val Repeat = 1
  private final val Stop = 2

  // Internal, reusable references
  private val pullFilter = (x: Either[Any, Any]) => x ne null
  private val pullMap = (x: Any) => x
  private val pushFilter = (x: Ack) => x != Repeat
  private val pushMap = (x: Ack) => x != Stop
  private val pushManyMap = (x: Ack) => x

  private def toSeq[A](buffer: ArrayBuffer[A]): Seq[A] =
    buffer.toArray[Any].toSeq.asInstanceOf[Seq[A]]

  private def triggerBroadcast[F[_], E, A](
    helpers: Helpers[F, E, A],
    refs: Array[ChanProducer[F, E, A]],
    f: ChanProducer[F, E, A] => F[Boolean])
    (implicit F: Async[F]): F[Boolean] = {

    def loop(cursor: Iterator[ChanProducer[F, E, A]], bind: Any => F[Boolean]): F[Boolean] = {
      val task = f(cursor.next())
      if (cursor.hasNext) {
        val bindRef = if (bind ne null) bind else {
          var bindVar: Any => F[Boolean] = null
          bindVar = {
            case true => loop(cursor, bindVar)
            case false => helpers.stopF
          }
          bindVar
        }
        F.flatMap(task)(bindRef)
      } else {
        task
      }
    }

    val cursor = refs.iterator
    if (cursor.hasNext)
      loop(refs.iterator, null)
    else
      helpers.continueF
  }

  private final class ChanProducer[F[_], E, A](
    queue: LowLevelQueue[A],
    isFinished: () => Option[E],
    helpers: Helpers[F, E, A])
    (implicit F: Async[F], timer: Timer[F]) {

    def push(a: A): F[Boolean] =
      F.suspend {
        (tryPushToOurQueue(a) : @switch) match {
          case Repeat =>
            F.asyncF(cb => helpers.polled(() => tryPushToOurQueue(a), pushFilter, pushMap, cb))
          case Continue =>
            helpers.continueF
          case Stop =>
            helpers.stopF
        }
      }

    private def tryPushToOurQueue(a: A): Ack = {
      if (queue.offer(a) == 0)
        Continue
      else isFinished() match {
        case None => Repeat
        case _ => Stop
      }
    }

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
          val offerWait = F.asyncF[Ack](cb => helpers.polled(() => tryPushToOurQueue(elem), pushFilter, pushManyMap, cb))
          offerWait.flatMap {
            case Continue => loop(cursor)
            case Stop => helpers.stopF
          }
        } else {
          helpers.continueF
        }
      }

      F.suspend(loop(seq.iterator))
    }
  }

  private final class ChanConsumer[F[_], E, A](
    queue: LowLevelQueue[A],
    isFinished: () => Option[E],
    helpers: Helpers[F, E, A])
    (implicit F: Async[F], timer: Timer[F])
    extends ConsumerF[F, E, A] {

    def pull: F[Either[E, A]] = pullRef
    private[this] val pullRef: F[Either[E, A]] = {
      def end(e: E): Either[E, A] =
        queue.poll() match {
          case null => Left(e)
          case a => Right(a)
        }

      val task: () => Either[E, A] = () => queue.poll() match {
        case null =>
          isFinished() match {
            case Some(e) => end(e)
            case _ => null.asInstanceOf[Either[E, A]]
          }
        case a =>
          Right(a)
      }

      F.suspend {
        task() match {
          case null =>
            F.asyncF(cb => helpers.polled(
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

    def pullMany(minLength: Int, maxLength: Int): F[Either[E, Seq[A]]] = {
      def end(buffer: ArrayBuffer[A], maxLength: Int, e: E): Either[E, Seq[A]] = {
        queue.drainToBuffer(buffer, maxLength - buffer.length)
        if (buffer.isEmpty)
          Left(e)
        else
          Right(toSeq(buffer))
      }

      def task(buffer: ArrayBuffer[A], minLength: Int, maxLength: Int): Either[E, Seq[A]] = {
        queue.drainToBuffer(buffer, maxLength - buffer.length)

        if (buffer.length >= minLength)
          Right(toSeq(buffer))
        else isFinished() match {
          case Some(e) => end(buffer, maxLength, e)
          case _ => null.asInstanceOf[Either[E, Seq[A]]]
        }
      }

      F.suspend[Either[E, Seq[A]]] {
        assert(minLength > 0, "minLength > 0")
        assert(minLength <= maxLength, "minLength <= maxLength")

        val buffer = ArrayBuffer.empty[A]
        val length = queue.drainToBuffer(buffer, maxLength)

        if (length > 1 && length >= minLength)
          F.pure(Right(toSeq(buffer)))
        else isFinished() match {
          case Some(e) =>
            F.pure(end(buffer, maxLength, e))
          case _ =>
            F.asyncF(cb => helpers.polled(
              () => task(buffer, minLength, maxLength),
              pullFilter,
              pullMap.asInstanceOf[Either[E, Seq[A]] => Either[E, Seq[A]]],
              cb
            ))
        }
      }
    }
  }

  private final class Helpers[F[_], E, A](retryDelay: FiniteDuration)
    (implicit F: Async[F], timer: Timer[F]) {

    // Internal, reusable values
    private[this] val retryDelayNanos = retryDelay.toNanos
    private[this] val asyncBoundary: F[Unit] = timer.sleep(Duration.Zero)

    val continueF = F.pure(true)
    val stopF = F.pure(false)

    def polled[T, U](f: () => T, filter: T => Boolean, map: T => U,  cb: Either[Throwable, U] => Unit): F[Unit] =
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

    def polledLoop[T, U](task: F[Unit], bind: Unit => F[Unit], start: Long): F[Unit] =
      timer.clock.monotonic(NANOSECONDS).flatMap { now =>
        val next = if (now - start < retryDelayNanos)
          asyncBoundary
        else
          timer.sleep(retryDelay)

        F.flatMap(next)(bind)
      }
  }
}
