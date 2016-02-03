/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams

import monix.streams.broadcast.Subject

/** Represents the buffering overflowStrategy chosen for actions that need buffering,
  * instructing the pipeline what to do when the buffer is full.
  *
  * For the available policies, see:
  *
  * - [[OverflowStrategy.Unbounded Unbounded]]
  * - [[OverflowStrategy.Fail OverflowTriggering]]
  * - [[OverflowStrategy.BackPressure BackPressured]]
  *
  * Used in [[monix.streams.observers.BufferedSubscriber BufferedSubscriber]]
  * to implement buffering when concurrent actions are needed, such as in
  * [[Subject Channels]] or in [[Observable.merge Observable.merge]].
  */
sealed abstract class OverflowStrategy {
  val isEvicted: Boolean = false
  val isSynchronous: Boolean = false
}

object OverflowStrategy {
  /** A [[OverflowStrategy]] specifying that the buffer is completely unbounded.
    * Using this overflowStrategy implies that with a fast data source, the system's
    * memory can be exhausted and the process might blow up on lack of memory.
    */
  case object Unbounded extends Synchronous

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the pipeline should cancel the subscription and send an `onError`
    * to the observer(s) downstream.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class Fail(bufferSize: Int)
    extends Synchronous {

    require(bufferSize > 1, "bufferSize must be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the pipeline should try to apply back-pressure (i.e. it should try
    * delaying the data source in producing more elements, until the
    * the consumer has drained the buffer and space is available).
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class BackPressure(bufferSize: Int)
    extends OverflowStrategy {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the pipeline should begin dropping incoming events until the buffer
    * has room in it again and is free to process more elements.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class DropNew(bufferSize: Int)
    extends Evicted {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the currently buffered events should start being dropped in a FIFO order,
    * so the oldest events from the buffer will be dropped first.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class DropOld(bufferSize: Int)
    extends Evicted {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the current buffer should be dropped completely to make room for
    * new events.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class ClearBuffer(bufferSize: Int)
    extends Evicted {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A category of [[OverflowStrategy]] for buffers that can be used
    * synchronously, without worrying about back-pressure concerns.
    *
    * Needed such that buffer policies can safely be used
    * in combination with [[Subject]] for publishing. For now,
    * that's all policies except [[BackPressure]], a overflowStrategy
    * that can't work for [[Subject]].
    */
  sealed abstract class Synchronous extends OverflowStrategy {
    override val isSynchronous = true
  }

  /** A sub-category of [[OverflowStrategy overflow strategies]]
    * that are [[Synchronous synchronous]] and that represent
    * eviction policies, meaning that on buffer overflows events
    * start being dropped. Using these policies one can also signal
    * a message informing downstream of dropped events.
    */
  sealed abstract class Evicted extends Synchronous {
    override val isEvicted = true
  }

  /** The default library-wide overflowStrategy used whenever a default argument
    * value is needed. Currently set to [[BackPressure]] with a buffer size
    * of 2048.
    */
  val default: OverflowStrategy =
    BackPressure(bufferSize = 2048)
}
