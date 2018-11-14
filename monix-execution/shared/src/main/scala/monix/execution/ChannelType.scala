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

package monix.execution

/**
  * An enumeration of all types
  */
sealed abstract class ChannelType(val value: String)
  extends Serializable {

  def isMultiProducer: Boolean
  def isMultiConsumer: Boolean
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
    * Multi-producer, multi-consumer
    */
  case object MPMC extends ChannelType("MPMC") {
    def isMultiProducer = true
    def isMultiConsumer = true
  }

  /**
    * Single-producer, single-consumer
    */
  case object MPSC extends ChannelType("MPSC") {
    def isMultiProducer = true
    def isMultiConsumer = false
  }

  /**
    * Single-producer, multi-consumer
    */
  case object SPMC extends ChannelType("SPMC") {
    def isMultiProducer = false
    def isMultiConsumer = true
  }

  /**
    * Single-producer, single-consumer
    */
  case object SPSC extends ChannelType("SPSC") {
    def isMultiProducer = false
    def isMultiConsumer = false
  }
}
