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

package monix.reactive

import monix.execution.internal.Platform

/** Represents the buffering overflowStrategy chosen for actions that
  * need buffering, instructing the pipeline what to do when
  * the buffer is full.
  *
  * For the available policies, see:
  *
  * - [[monix.reactive.OverflowStrategy.Unbounded Unbounded]]
  * - [[monix.reactive.OverflowStrategy.Fail Fail]]
  * - [[monix.reactive.OverflowStrategy.BackPressure BackPressure]]
  *
  * Used in [[monix.reactive.observers.BufferedSubscriber BufferedSubscriber]]
  * to implement buffering when concurrent actions are needed, such as in
  * [[monix.reactive.subjects.ConcurrentSubject Channels]] or in
  * [[monix.reactive.Observable.merge Observable.merge]].
  */
sealed abstract class OverflowStrategy[+A] extends Serializable {
  val isEvicted: Boolean = false
  val isSynchronous: Boolean = false
}

object OverflowStrategy {
  /** A [[OverflowStrategy]] specifying that the buffer is completely unbounded.
    * Using this overflowStrategy implies that with a fast data source, the system's
    * memory can be exhausted and the process might blow up on lack of memory.
    */
  case object Unbounded extends Synchronous[Nothing]

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the pipeline should cancel the subscription and send an `onError`
    * to the observer(s) downstream.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *                   before overflowing
    */
  final case class Fail(bufferSize: Int)
    extends Synchronous[Nothing] {

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
    extends OverflowStrategy[Nothing] {

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
    extends Evicted[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the pipeline should begin dropping incoming events until the buffer
    * has room in it again and is free to process more elements.
    *
    * The given `onOverflow` function get be used for logging the event
    * and for sending a message to the downstream consumers to inform
    * them of dropped messages. The function can return `None` in which
    * case no message is sent and thus you can use it just to log a warning.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *        before overflowing.
    *
    * @param onOverflow is a function that can get called on overflow with
    *        a number of messages that were dropped, a function that builds
    *        a new message that will be sent to downstream. If it returns
    *        `None`, then no message gets sent to downstream.
    */
  final case class DropNewAndSignal[A](bufferSize: Int, onOverflow: Long => Option[A])
    extends Evicted[A] {

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
    extends Evicted[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the currently buffered events should start being dropped in a FIFO order,
    * so the oldest events from the buffer will be dropped first.
    *
    * The given `onOverflow` function get be used for logging the event
    * and for sending a message to the downstream consumers to inform
    * them of dropped messages. The function can return `None` in which
    * case no message is sent and thus you can use it just to log a warning.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *        before overflowing
    *
    * @param onOverflow is a function that can get called on overflow with
    *        a number of messages that were dropped, a function that builds
    *        a new message that will be sent to downstream. If it returns
    *        `None`, then no message gets sent to downstream.
    */
  final case class DropOldAndSignal[A](bufferSize: Int, onOverflow: Long => Option[A])
    extends Evicted[A] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the current buffer should be dropped completely to make room for
    * new events.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *        before overflowing
    */
  final case class ClearBuffer(bufferSize: Int)
    extends Evicted[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A [[OverflowStrategy]] specifying that on reaching the maximum size,
    * the current buffer should be dropped completely to make room for
    * new events.
    *
    * The given `onOverflow` function get be used for logging the event
    * and for sending a message to the downstream consumers to inform
    * them of dropped messages. The function can return `None` in which
    * case no message is sent and thus you can use it just to log a warning.
    *
    * @param bufferSize specifies how many events our buffer can hold
    *        before overflowing
    *
    * @param onOverflow is a function that can get called on overflow with
    *        a number of messages that were dropped, a function that builds
    *        a new message that will be sent to downstream.
    */
  final case class ClearBufferAndSignal[A](bufferSize: Int, onOverflow: Long => Option[A])
    extends Evicted[A] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /** A category of [[OverflowStrategy]] for buffers that can be used
    * synchronously, without worrying about back-pressure concerns.
    */
  sealed abstract class Synchronous[+A] extends OverflowStrategy[A] {
    override val isSynchronous = true
  }

  /** A sub-category of [[OverflowStrategy overflow strategies]]
    * that are [[Synchronous synchronous]] and that represent
    * eviction policies, meaning that on buffer overflows events
    * start being dropped.
    */
  sealed abstract class Evicted[A] extends Synchronous[A] {
    val bufferSize: Int
    override val isEvicted = true
  }

  /** The default library-wide overflowStrategy used whenever
    * a default argument value is needed.
    */
  final def Default[A]: OverflowStrategy[A] =
    defaultInstance

  private[this] val defaultInstance: OverflowStrategy[Nothing] =
    BackPressure(bufferSize = Platform.recommendedBatchSize * 2)
}
