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

import monix.streams.broadcast._

/** Represents a [[Processor]] factory.
  *
  * Needed because Pipes are inherently side-effecting, stateful and have to
  * obey the `Observer` contract (so can't be reused for subscribing to
  * multiple observables).
  */
trait Pipe[I,+O] {
  /** Returns a new instance of a [[Processor]]. */
  def createProcessor(): Processor[I,O]
}

object Pipe {
  /** Processor recipe for building [[PublishProcessor]] instances. */
  def publish[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        PublishProcessor[T]()
    }

  /** Processor recipe for building [[BehaviorProcessor]] instances. */
  def behavior[T](initial: => T): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        BehaviorProcessor[T](initial)
    }

  /** Processor recipe for building [[AsyncProcessor]] instances. */
  def async[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        AsyncProcessor[T]()
    }

  /** Processor recipe for building unbounded [[ReplayProcessor]] instances. */
  def replay[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        ReplayProcessor[T]()
    }

  /** Processor recipe for building unbounded [[ReplayProcessor]] instances.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    */
  def replayPopulated[T](initial: => Seq[T]): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        ReplayProcessor[T](initial:_*)
    }

  /** Processor recipe for building [[ReplayProcessor]] instances
    * with a maximum `capacity` (after which old items start being dropped).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    */
  def replaySized[T](capacity: Int): Pipe[T,T] =
    new Pipe[T,T] {
      def createProcessor(): Processor[T,T] =
        ReplayProcessor.createWithSize[T](capacity)
    }
}