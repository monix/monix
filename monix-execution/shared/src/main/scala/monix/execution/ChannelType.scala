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

package monix.execution

/**
  * An enumeration of all types
  */
sealed abstract class ChannelType(val value: String) extends Serializable {

  def producerType: ChannelType.ProducerSide
  def consumerType: ChannelType.ConsumerSide
}

object ChannelType {
  /** Converts a string value into a [[ChannelType]]. */
  def apply(id: String): Option[ChannelType] =
    id match {
      case MPMC.value => Some(MPMC)
      case MPSC.value => Some(MPSC)
      case SPMC.value => Some(SPMC)
      case SPSC.value => Some(SPSC)
      case _ => None
    }

  /**
    * Given a [[ProducerSide]] value and a [[ConsumerSide]] value,
    * assemble a [[ChannelType]] value out of them.
    */
  def assemble(p: ProducerSide, c: ConsumerSide): ChannelType =
    p match {
      case MultiProducer =>
        c match {
          case MultiConsumer => MPMC
          case SingleConsumer => MPSC
        }
      case SingleProducer =>
        c match {
          case MultiConsumer => SPMC
          case SingleConsumer => SPSC
        }
    }

  /**
    * Multi-producer, multi-consumer
    */
  case object MPMC extends ChannelType("MPMC") {
    def producerType: ProducerSide = MultiProducer
    def consumerType: ConsumerSide = MultiConsumer
  }

  /**
    * Single-producer, single-consumer
    */
  case object MPSC extends ChannelType("MPSC") {
    def producerType: ProducerSide = MultiProducer
    def consumerType: ConsumerSide = SingleConsumer
  }

  /**
    * Single-producer, multi-consumer
    */
  case object SPMC extends ChannelType("SPMC") {
    def producerType: ProducerSide = SingleProducer
    def consumerType: ConsumerSide = MultiConsumer
  }

  /**
    * Single-producer, single-consumer
    */
  case object SPSC extends ChannelType("SPSC") {
    def producerType: ProducerSide = SingleProducer
    def consumerType: ConsumerSide = SingleConsumer
  }

  /**
    * Enumeration for describing the type of the producer, with two
    * possible values:
    *
    *  - [[MultiProducer]] (default)
    *  - [[SingleProducer]]
    *
    * This is often used to optimize the underlying buffer used.
    * The multi-producer option is the safe default and specifies
    * that multiple producers (threads, actors, etc) can push events
    * concurrently, whereas the single-producer option specifies that
    * a single producer can (sequentially) push events and can be used
    * as an (unsafe) optimization.
    */
  sealed abstract class ProducerSide(val value: String) extends Serializable

  /**
    * Multi-producer channel side, meaning that multiple actors can
    * push messages from multiple threads, concurrently.
    */
  case object MultiProducer extends ProducerSide("MP")

  /**
    * Single-producer channel side, meaning that only a single actor
    * can push messages on the channel.
    *
    * It can do so from multiple threads, but not concurrently, so
    * it needs clear happens-before relationships between subsequent
    * push operations.
    *
    * '''WARNING:''' This is often selected as an optimization. Use with
    * care and prefer [[MultiProducer]] when in doubt.
    */
  case object SingleProducer extends ProducerSide("SP")

  /**
    * Enumeration for describing the type of the producer, with two
    * possible values:
    *
    *  - [[MultiConsumer]]
    *  - [[SingleConsumer]]
    */
  sealed abstract class ConsumerSide(val value: String) extends Serializable

  /**
    * Multi-consumer channel side, meaning that multiple actors can
    * pull data from the channel, from multiple threads, concurrently.
    */
  case object MultiConsumer extends ConsumerSide("MC")

  /**
    * Single-consumer channel side, meaning that a single actor can
    * pull data from the channel.
    *
    * It can do so from multiple threads, but not concurrently, so it
    * needs clear happens-before relationships between subsequent
    * pull operations.
    *
    * '''WARNING:''' This is often selected as an optimization. Use with
    * care and prefer [[MultiConsumer]] when in doubt.
    */
  case object SingleConsumer extends ConsumerSide("SC")
}
