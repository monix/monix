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

package monix.streams.broadcast

import monix.execution.Scheduler
import monix.streams.{OverflowStrategy, Observable}
import monix.streams.OverflowStrategy.{Evicted, Synchronous}
import monix.streams.observers.SyncObserver
import scala.language.reflectiveCalls

/** A subject is meant for imperative style feeding of events.
  *
  * When emitting events, one doesn't need to follow the back-pressure contract.
  * On the other hand the grammar must still be respected:
  *
  *     (onNext)* (onComplete | onError)
  */
trait Subject[I,+O] extends Observable[O] with SyncObserver[I]

object Subject {
  /** Wraps any [[Processor]] into a [[Subject]].
    *
    * @param strategy - the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def fromProcessor[I,O](p: Processor[I,O], strategy: Synchronous)
    (implicit s: Scheduler): Subject[I,O] =
    ProcessorAsSubject(p, strategy)

  /** Wraps any [[Processor]] into a [[Subject]].
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def fromProcessor[I,O](p: Processor[I,O], strategy: Evicted, onOverflow: Long => I)
    (implicit s: Scheduler): Subject[I,O] =
    ProcessorAsSubject(p, strategy, onOverflow)

  /** Subject recipe for building [[PublishProcessor publish]] subjects.
    *
    * @param strategy - the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def publish[T](strategy: Synchronous)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(PublishProcessor[T](), strategy)

  /** Subject recipe for building [[PublishProcessor publish]] subjects. */
  def publish[T](strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(PublishProcessor[T](), strategy, onOverflow)

  /** Subject recipe for building [[BehaviorProcessor behavior]] subjects.
    *
    * @param initial the initial element to emit on subscribe,
    *        before the first `onNext` happens
    *
    * @param strategy the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def behavior[T](initial: T, strategy: Synchronous)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(BehaviorProcessor[T](initial), strategy)

  /** Subject recipe for building [[BehaviorProcessor behavior]] subjects.
    *
    * @param initial the initial element to emit on subscribe,
    *        before the first `onNext` happens
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def behavior[T](initial: T, strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(BehaviorProcessor[T](initial), strategy, onOverflow)

  /** Subject recipe for building [[AsyncProcessor async]] subjects.
    *
    * @param strategy the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def async[T](strategy: Synchronous)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(AsyncProcessor[T](), strategy)

  /** Subject recipe for building [[AsyncProcessor async]] subjects.
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def async[T](strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(AsyncProcessor[T](), strategy, onOverflow)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    *
    * @param strategy the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replay[T](strategy: Synchronous)
   (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor[T](), strategy)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def replay[T](strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor[T](), strategy, onOverflow)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    *
    * @param strategy the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replayPopulated[T](initial: Seq[T], strategy: Synchronous)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor[T](initial:_*), strategy)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def replayPopulated[T](initial: Seq[T], strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor[T](initial:_*), strategy, onOverflow)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    * This variant creates a size-bounded replay subject.
    *
    * In this setting, the replay subject with a maximum capacity for
    * its internal buffer and discards the oldest item. The `capacity`
    * given is a guideline. The underlying implementation may decide
    * to optimize it (e.g. use the next power of 2 greater or equal to
    * the given value).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    *
    * @param strategy the [[monix.streams.OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers.
    */
  def replayLimited[T](capacity: Int, strategy: Synchronous)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor.createWithSize[T](capacity), strategy)

  /** Subject recipe for building [[ReplayProcessor replay]] subjects.
    * This variant creates a size-bounded replay subject.
    *
    * In this setting, the replay subject with a maximum capacity for
    * its internal buffer and discards the oldest item. The `capacity`
    * given is a guideline. The underlying implementation may decide
    * to optimize it (e.g. use the next power of 2 greater or equal to
    * the given value).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    *
    * @param strategy the [[OverflowStrategy overflow strategy]]
    *        used for buffering, which specifies what to do in case
    *        we're dealing with slow consumers: should an unbounded
    *        buffer be used, should back-pressure be applied, should
    *        the pipeline drop newer or older events, should it drop
    *        the whole buffer?  See [[OverflowStrategy]] for more
    *        details.
    *
    * @param onOverflow a function that is used for signaling a special
    *        event used to inform the consumers that an overflow event
    *        happened, function that receives the number of dropped
    *        events as a parameter (see [[OverflowStrategy.Evicted]]).
    */
  def replayLimited[T](capacity: Int, strategy: Evicted, onOverflow: Long => T)
    (implicit s: Scheduler): Subject[T,T] =
    fromProcessor(ReplayProcessor.createWithSize[T](capacity), strategy, onOverflow)
}
