/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive

/**
 * Represents the buffering policy chosen for actions that need buffering,
 * instructing the pipeline what to do when the buffer is full.
 *
 * For the available policies, see:
 *
 * - [[BufferPolicy.Unbounded Unbounded]]
 * - [[BufferPolicy.TriggerError OverflowTriggering]]
 * - [[BufferPolicy.BackPressure BackPressured]]
 *
 * Used in [[monifu.reactive.observers.BufferedSubscriber BufferedSubscriber]]
 * to implement buffering when concurrent actions are needed, such as in
 * [[monifu.reactive.Channel Channels]] or in [[monifu.reactive.Observable.merge Observable.merge]].
 */
sealed trait BufferPolicy[+T]

object BufferPolicy {
  /**
   * A category of [[BufferPolicy]] for buffers that can be used
   * synchronously, without worrying about back-pressure concerns.
   *
   * Needed such that buffer policies can safely be used
   * in combination with [[Channel]] for publishing. For now,
   * that's all policies except [[BackPressure]], a policy
   * that can't work for [[Channel]].
   */
  sealed trait Synchronous[+T] extends BufferPolicy[T]
  
  /**
   * A [[BufferPolicy]] specifying that the buffer is completely unbounded.
   * Using this policy implies that with a fast data source, the system's
   * memory can be exhausted and the process might blow up on lack of memory.
   */
  case object Unbounded extends Synchronous[Nothing]

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should cancel the subscription and send an `onError`
   * to the observer(s) downstream.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   */
  case class TriggerError(bufferSize: Int)
    extends Synchronous[Nothing] {

    require(bufferSize > 1, "bufferSize must be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should try to apply back-pressure (i.e. it should try
   * delaying the data source in producing more elements, until the
   * the consumer has drained the buffer and space is available).
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   */
  case class BackPressure(bufferSize: Int)
    extends BufferPolicy[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should begin dropping incoming events until the buffer
   * has room in it again and is free to process more elements.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   */
  case class DropIncoming(bufferSize: Int)
    extends Synchronous[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should begin dropping incoming events until the buffer
   * has room in it again and is free to process more elements.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   *
   * @param onOverflow is a function that receives the number of events that were
   *                   dropped and is used to construct an overflow event for
   *                   signalling to downstream that events were dropped.
   */
  case class DropIncomingThenSignal[+T](bufferSize: Int, onOverflow: Long => T)
    extends Synchronous[T] {
        
    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the current buffer should be dropped completely to make room for
   * new events.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   */
  case class DropBuffer(bufferSize: Int)
    extends Synchronous[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the current buffer should be dropped completely.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   *
   * @param onOverflow is a function that receives the number of events that were
   *                   dropped and is used to construct an overflow event for
   *                   signalling to downstream that events were dropped.
   */
  case class DropBufferThenSignal[+T](bufferSize: Int, onOverflow: Long => T)
    extends Synchronous[T] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the currently buffered events should start being dropped in a FIFO order,
   * so the oldest events from the buffer will be dropped first.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   */
  case class DropOld(bufferSize: Int)
    extends Synchronous[Nothing] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the currently buffered events should start being dropped in a FIFO order,
   * so the oldest events from the buffer will be dropped first.
   *
   * @param bufferSize specifies how many events our buffer can hold
   *                   before overflowing
   *
   * @param onOverflow is a function that receives the number of events that were
   *                   dropped and is used to construct an overflow event for
   *                   signalling to downstream that events were dropped.
   */
  case class DropOldThenSignal[+T](bufferSize: Int, onOverflow: Long => T)
    extends Synchronous[T] {

    require(bufferSize > 1, "bufferSize should be strictly greater than 1")
  }
  
  /**
   * The default library-wide policy used whenever a default argument
   * value is needed.
   */
  val default: BufferPolicy[Nothing] = BackPressure(bufferSize = 2048)
}

