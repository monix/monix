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
    * This strategy consumes the elements from a `Publisher`
    * one by one, with acknowledgement required for each event.
    *
    * In this mode the consumer must indicate its readiness to
    * receive data after every event and the consumer must wait
    * on that acknowledgement. Technically what this means is that for
    * each element the consumer needs to do a `request(1)` call.
    *
    * This could be the same as `FixedWindow(1)` (see [[FixedWindow]]),
    * however internally implementations can optimize for stop-and-wait
    * flow control. For example a buffer is not necessarily required.
    *
    * Pros and Cons of stop-and-wait strategy:
    *
    *  - the implementation can be simpler
    *  - versus [[FixedWindow]] it doesn't have to wait for the buffer
    *    to fill up, so it's more fair
    *  - the producer needs to wait for acknowledgement on each
    *    event and this is a source of inefficiency
    */
  case object StopAndWait extends ReactivePullStrategy

  /**
    * This strategy pre-allocates a buffer of the given size and waits
    * for it to fill up before emitting it downstream.
    *
    * Additional events are requested only after the buffer is
    * emitted.
    *
    * This strategy is more efficient than [[StopAndWait]], but
    * less fair. For example if you have a producer that emits
    * a tick every second, with a `bufferSize` of 10 the consumer
    * will only see events every 10 seconds. Therefore it should
    * be used with a busy source, but for slow producers
    * [[StopAndWait]] is a better strategy.
    */
  final case class FixedWindow(bufferSize: Int) extends ReactivePullStrategy {

    require(bufferSize > 0, "Batch size must be strictly positive!")
  }

  /**
    * Default buffering strategy used when not overridden by a
    * user-defined implicit.
    */
  implicit val default: ReactivePullStrategy = StopAndWait
}
