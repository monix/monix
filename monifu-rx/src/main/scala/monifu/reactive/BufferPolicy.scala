/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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
 * - [[BufferPolicy.OverflowTriggering OverflowTriggering]]
 * - [[BufferPolicy.BackPressured BackPressured]]
 *
 * Used in [[monifu.reactive.observers.BufferedObserver BufferedObserver]]
 * to implement buffering when concurrent actions are needed, such as in
 * [[monifu.reactive.Channel Channels]] or in [[monifu.reactive.Observable.merge Observable.merge]].
 */
sealed trait BufferPolicy

object BufferPolicy {
  /**
   * A [[BufferPolicy]] specifying that the buffer is completely unbounded.
   * Using this policy implies that with a fast data source, the system's
   * memory can be exhausted and the process might blow up on lack of memory.
   */
  case object Unbounded extends BufferPolicy

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should cancel the subscription and send an `onError`
   * to the observer(s) downstream.
   */
  case class OverflowTriggering(bufferSize: Int) extends BufferPolicy {
    require(bufferSize > 1, "bufferSize must be greater than 1")
  }

  /**
   * A [[BufferPolicy]] specifying that on reaching the maximum size,
   * the pipeline should try to apply back-pressure (i.e. it should try
   * delaying the data source in producing more elements, until the
   * the consumer has drained the buffer and space is available).
   */
  case class BackPressured(bufferSize: Int) extends BufferPolicy {
    require(bufferSize > 1, "bufferSize should be greater than 1")
  }
}

