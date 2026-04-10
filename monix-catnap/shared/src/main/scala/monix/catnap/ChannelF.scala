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
import cats.effect.Resource
import monix.execution.annotations.UnsafeProtocol

/** `Channel` is a communication channel that can be consumed via
  * [[monix.catnap.ChannelF!.consume consume]].
  *
  * Examples:
  *
  *  - [[monix.catnap.ConcurrentChannel]]
  *  - [[monix.tail.Iterant.toChannel]]
  */
trait ChannelF[F[_], E, A] extends Serializable {
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
    * @see [[consumeWithConfig]] for fine tuning the internal buffer of the
    *      created consumer
    */
  def consume: Resource[F, ConsumerF[F, E, A]]

  /** Version of [[consume]] that allows for fine tuning the underlying
    * buffer used.
    *
    * @param config is configuration for the created buffer, see
    *        [[ConsumerF.Config]] for details
    */
  @UnsafeProtocol
  def consumeWithConfig(config: ConsumerF.Config): Resource[F, ConsumerF[F, E, A]]
}
