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

/** The `MulticastStrategy` specifies the strategy for
  * building data-sources that are shared between multiple subscribers.
  *
  * By default observables tend to be cold.
  */
sealed abstract class MulticastStrategy[+A] extends Serializable

/** The [[MulticastStrategy]] enumerated.
  *
  * @define publish The `Publish` strategy is for emitting to a subscriber
  *         only those items that are emitted by the source subsequent
  *         to the time of the subscription.
  *
  *         Corresponds to [[Pipe.publish]].
  *
  * @define behavior The `Behavior` strategy is for building multicast observables that
  *         emit the most recently emitted item by the source before the
  *         source starts being mirrored.
  *
  *         Corresponds to [[Pipe.behavior]].
  *
  * @define async The `Async` strategy is for building multicast observables that
  *         emit the last value (and only the last value) of the source
  *         and only after the source completes.
  *
  *         Corresponds to [[Pipe.async]].
  *
  * @define replay The `Replay` strategy is for building multicast observables
  *         that repeat all the generated items by the source, regardless of
  *         when the source is subscribed.
  *
  *         Corresponds to [[Pipe.replay[A](initial:Seq[A]* Pipe.replay]].
  *
  * @define replayLimited   The `ReplayLimited` strategy is for building multicast
  *         observables that repeat the generated items by the source, but limited by the
  *         maximum size of the underlying buffer.
  *
  *         When maximum size is reached, the underlying buffer starts dropping
  *         older events. Note that the size of the resulting buffer is not necessarily
  *         the given capacity, as the implementation may choose to increase it for optimisation
  *         purposes.
  *
  *         Corresponds to [[Pipe.replayLimited[A](capacity:Int,initial* Pipe.replayLimited]].
  */
object MulticastStrategy {
  /** $publish */
  def publish[A]: MulticastStrategy[A] = Publish

  /** $publish */
  case object Publish extends MulticastStrategy[Nothing]

  /** $behavior */
  def behavior[A](initial: A): MulticastStrategy[A] = Behavior(initial)

  /** $behavior */
  case class Behavior[A](initial: A) extends MulticastStrategy[A]

  /** $async */
  def async[A]: MulticastStrategy[A] = Async

  /** $async */
  case object Async extends MulticastStrategy[Nothing]

  /** $replay */
  def replay[A]: MulticastStrategy[A] = Replay(Seq.empty)

  /** $replay */
  def replay[A](initial: Seq[A]): MulticastStrategy[A] = Replay(initial)

  /** $replay */
  case class Replay[A](initial: Seq[A]) extends MulticastStrategy[A]

  /** $replayLimited */
  def replayLimited[A](capacity: Int): MulticastStrategy[A] =
    ReplayLimited(capacity, Seq.empty)

  /** $replayLimited */
  def replayLimited[A](capacity: Int, initial: Seq[A]): MulticastStrategy[A] =
    ReplayLimited(capacity, initial)

  /** $replayLimited */
  case class ReplayLimited[A](capacity: Int, initial: Seq[A])
    extends MulticastStrategy[A]
}
