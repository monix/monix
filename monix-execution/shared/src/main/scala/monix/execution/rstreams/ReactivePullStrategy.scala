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

package monix.execution.rstreams


/**
  * Describes a strategy of buffering data when converting a `Publisher` into other
  * data types like `monix.tail.Iterant`.
  *
  * To override a strategy, provide it as an implicit:
  *
  * {{{
  *   implicit val pullStrategy: ReactivePullStrategy = ReactivePullStrategy.Batched(256)
  *
  *   // The call will use strategy defined above
  *   Iterant[Task].fromReactivePublisher(publisher)
  * }}}
  */
sealed abstract class ReactivePullStrategy extends Product with Serializable

object ReactivePullStrategy {
  /**
    * This strategy requests the elements from a `Publisher` one by one.
    */
  val Single : ReactivePullStrategy = Batched(1)

  /**
    * Default buffering strategy used when not overridden by a user-defined implicit.
    */
  val Default: ReactivePullStrategy = Batched(64)

  /**
    * This strategy pre-allocates the buffer of given size and waits for it to fill up
    * before emitting values. Additional values are requested only after the buffer is
    * emitted.
    */
  final case class Batched(size: Int) extends ReactivePullStrategy {
    require(size >= 1, "Batch size for pull strategy must be strictly positive")
  }
}
