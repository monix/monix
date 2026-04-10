/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

/**
  * A simple interface that models the producer side of a producer-consumer
  * communication channel.
  *
  * In a producer-consumer communication channel we've got these concerns
  * to take care of:
  *
  *  - back-pressure, which is handled automatically via this interface
  *  - halting the channel with a final event and informing all current and
  *    future consumers about it, while stopping future producers from pushing
  *    more events
  *
  * The `ProducerF` interface takes care of these concerns via:
  *
  *  - the `F[Boolean]` result, which should return `true` for as long as
  *    the channel wasn't halted, so further events can be pushed; these
  *    tasks also block (asynchronously) when internal buffers are full,
  *    so back-pressure concerns are handled automatically
  *  - [[monix.catnap.ProducerF!.halt halt]], being able to close the channel
  *    with a final event that will be visible to all current and future
  *    consumers
  *
  * Currently implemented by [[ConcurrentChannel]].
  */
trait ProducerF[F[_], E, A] extends Serializable {
  /**
    * Publishes an event on the channel.
    *
    * Contract:
    *
    *  - in case the internal buffers are full, back-pressures until there's
    *    enough space for pushing the message, or until the channel was halted,
    *    whichever comes first
    *  - returns `true` in case the message was pushed in the internal buffer
    *    or `false` in case the channel was halted and cannot receive any
    *    more events, in which case the producer's loop should terminate
    *
    * Example:
    * {{{
    *   import cats.implicits._
    *   import cats.effect.Async
    *
    *   def range[F[_]](channel: ProducerF[F, Int, Int], from: Int, until: Int)
    *     (implicit F: Async[F]): F[Unit] = {
    *
    *     if (from < until) {
    *       if (from + 1 < until)
    *         channel.push(from).flatMap {
    *           case true =>
    *             // keep going
    *             range(channel, from + 1, until)
    *           case false =>
    *             // channel was halted by another producer loop, so stopping
    *             F.unit
    *         }
    *       else // we are done, publish the final event
    *         channel.halt(from + 1).as(())
    *     } else {
    *       F.unit // invalid range
    *     }
    *   }
    * }}}
    *
    * @param a is the message to publish
    *
    * @return `true` in case the message was published successfully, or
    *         `false` in case the channel was halted by another producer
    */
  def push(a: A): F[Boolean]

  /**
    * Publishes multiple events on the channel.
    *
    * Contract:
    *
    *  - in case the internal buffers are full, back-pressures until there's
    *    enough space and the whole sequence was published, or until the
    *    channel was halted, in which case no further messages are allowed
    *    for being pushed
    *  - returns `true` in case the whole sequence was pushed in the internal
    *    b or `false` in case the channel was halted and cannot receive any
    *    more events, in which case the producer's loop should terminate
    *
    * Note: implementations may try to push events one by one. This is not
    * an atomic operation. In case of concurrent producers, there's absolutely
    * no guarantee for the order of messages coming from multiple producers.
    * Also in case the channel is halted (and the resulting task returns `false`),
    * the outcome can be that the sequence gets published partially.
    *
    * {{{
    *   import cats.implicits._
    *   import cats.effect.Async
    *
    *   def range[F[_]](channel: ProducerF[F, Int, Int], from: Int, until: Int)
    *     (implicit F: Async[F]): F[Unit] = {
    *
    *     if (from < until) {
    *       val to = until - 1
    *       channel.pushMany(Range(from, to)).flatMap {
    *         case true =>
    *           channel.halt(to).as(()) // final event
    *         case false =>
    *           // channel was halted by a concurrent producer, so stop
    *           F.unit
    *       }
    *     } else {
    *       F.unit // invalid range
    *     }
    *   }
    * }}}
    *
    * @param seq is the sequence of messages to publish on the channel
    *
    * @return `true` in case the message was published successfully, or
    *         `false` in case the channel was halted by another producer
    */
  def pushMany(seq: Iterable[A]): F[Boolean]

  /**
    * Closes the communication channel with a message that will be visible
    * to all current and future consumers.
    *
    * Note that if multiple `halt` events happen, then only the first one
    * will be taken into account, all other `halt` messages are ignored.
    */
  def halt(e: E): F[Unit]

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
  def awaitConsumers(n: Int): F[Boolean]
}
