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

/**
  * A simple interface that models the consumer side of a producer-consumer
  * communication channel.
  *
  * Currently exposed by [[ConcurrentChannel.consume]].
  *
  * @tparam F is effect type used for processing tasks asynchronously
  * @tparam E is the type for the completion event
  * @tparam A is the type for the stream of events being consumed
  */
trait ConsumerF[F[_], E, A] extends Serializable {
  /**
    * Pulls one message from the communication channel, when it becomes available.
    *
    * Example:
    * {{{
    *   import cats.implicits._
    *   import cats.effect.Async
    *
    *   def sum[F[_]](channel: ConsumerF[F, Int, Int], acc: Long = 0)
    *     (implicit F: Async[F]): F[Long] = {
    *
    *     channel.pull.flatMap {
    *       case Left(e) => F.pure(acc + e)
    *       case Right(i) => sum(channel, acc + i)
    *     }
    *   }
    * }}}
    *
    * @return either `Left(e)`, if the channel was closed with a final `e`
    *         completion event, or `Right(a)`, representing a message that
    *         was pulled from the channel
    */
  def pull: F[Either[E, A]]

  /**
    * Pulls a whole batch of messages from the channel, at least one,
    * the returned sequence being no larger than the specified `maxLength`.
    *
    * {{{
    *   import cats.implicits._
    *   import cats.effect.Async
    *
    *   def sum[F[_]](channel: ConsumerF[F, Int, Int], acc: Long = 0)
    *     (implicit F: Async[F]): F[Long] = {
    *
    *     channel.pullMany(1, 16).flatMap {
    *       case Left(e) => F.pure(acc + e)
    *       case Right(seq) => sum(channel, acc + seq.sum)
    *     }
    *   }
    * }}}
    *
    * @param minLength is the minimum size of the returned sequence;
    *        for as long as the channel isn't halted, the returned task will
    *        back-pressure until the required number of events have been
    *        collected
    *
    * @param maxLength is the maximum size of the returned sequence;
    *        for fairness purposes (e.g. multiple workers consuming from
    *        the same `ConsumerF`), a smaller value is recommended,
    *        or otherwise `Int.MaxValue` can be used
    *
    * @return either `Left(e)`, if the channel was closed with a final `e`
    *         completion event, or `Right(seq)`, representing a non-empty
    *         sequence of messages pulled from the channel, but that is
    *         no larger than `maxLength`
    */
  def pullMany(minLength: Int, maxLength: Int): F[Either[E, Seq[A]]]
}
