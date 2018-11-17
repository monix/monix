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

package monix.tail
package internal

import cats.implicits._
import cats.effect.{Concurrent, Resource, Timer}
import monix.catnap.ConcurrentChannel
import monix.execution.BufferCapacity.Bounded
import monix.execution.{BufferCapacity, ChannelType}
import monix.execution.ChannelType.{MultiConsumer, SingleProducer}
import monix.tail.Iterant.Consumer

private[tail] object IterantConsume {
  /**
    * Implementation for [[Iterant.consume]].
    */
  def apply[F[_], A](
    self: Iterant[F, A],
    capacity: BufferCapacity = Bounded(256),
    consumerType: ChannelType.ConsumerSide = MultiConsumer)
    (implicit F: Concurrent[F], timer: Timer[F]): Resource[F, Consumer[F, A]] = {

    /*_*/
    val res = Resource.apply {
      val channel = ConcurrentChannel.custom[F, Option[Throwable], A](
        producerType = SingleProducer
      )
      F.flatMap(channel) { channel =>
        val produce = self.pushToChannel(channel)
        val latch = channel.awaitConsumers(1)
        val fiber = F.start(F.flatMap(latch)(_ => produce))

        F.map(fiber) { fiber =>
          (channel.consumeCustom(capacity, consumerType), fiber.cancel)
        }
      }
    }
    res.flatten
    /*_*/
  }
}
