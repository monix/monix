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

package monix.eval

import cats.effect.concurrent.{MVar => CatsMVar}

/** A mutable location, that is either empty or contains
  * a value of type `A`.
  *
  * It has the following fundamental atomic operations:
  *
  *  - [[put]] which fills the var if empty, or blocks
  *    (asynchronously) until the var is empty again
  *  - [[tryPut]] which fills the var if empty. returns true if successful
  *  - [[take]] which empties the var if full, returning the contained
  *    value, or blocks (asynchronously) otherwise until there is
  *    a value to pull
  *  - [[tryTake]] empties if full, returns None if empty.
  *  - [[read]] which reads the current value without touching it,
  *    assuming there is one, or otherwise it waits until a value
  *    is made available via `put`
  *  - [[isEmpty]] returns true if currently empty
  *
  * The `MVar` is appropriate for building synchronization
  * primitives and performing simple inter-thread communications.
  * If it helps, it's similar with a `BlockingQueue(capacity = 1)`,
  * except that it doesn't block any threads, all waiting being
  * done asynchronously by means of [[Task]].
  *
  * Given its asynchronous, non-blocking nature, it can be used on
  * top of Javascript as well.
  *
  * Inspired by `Control.Concurrent.MVar` from Haskell and
  * by `scalaz.concurrent.MVar`.
  */
abstract class MVar[A] extends CatsMVar[Task,A]{
  def isEmpty: Task[Boolean]

  /** Fills the `MVar` if it is empty, or blocks (asynchronously)
    * if the `MVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    **
    * @return a task that on evaluation will complete when the
    *         `put` operation succeeds in filling the `MVar`,
    *         with the given value being next in line to
    *         be consumed
    */
  def put(a: A): Task[Unit]

  /**
    * Fill the `MVar` if we can do it without blocking,
    *
    * @return whether or not the put succeeded
    */
  def tryPut(a: A): Task[Boolean]

  /** Empties the `MVar` if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will be completed after
    *         a value was retrieved
    */
  def take: Task[A]

  /**
    * empty the `MVar` if full
    *
    * @return an Option holding the current value, None means it was empty
    */
  def tryTake: Task[Option[A]]

  /**
    * Tries reading the current value, or blocks (asynchronously)
    * until there is a value available.
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will be completed after
    *         a value has been read
    */
  def read: Task[A]
}

/** Builders for [[MVar]]
  *
  * @define refTransparent [[Task]] returned by this operation
  *         produces a new [[MVar]] each time it is evaluated.
  *         To share a state between multiple consumers, pass
  *         [[MVar]] as a parameter or use [[Task.memoize]]
  */
object MVar {
  /** Builds an [[MVar]] instance with an `initial` value.
    *
    * $refTransparent
    */
  def apply[A](initial: A): Task[MVar[A]] =
    CatsMVar.of[Task, A](initial).map(mvar => new TaskMVarImpl(mvar))

  /** Returns an empty [[MVar]] instance.
    *
    * $refTransparent
    */
  def empty[A]: Task[MVar[A]] =
    CatsMVar.empty[Task, A].map(mvar => new TaskMVarImpl(mvar))

  /** [[MVar]] implementation based on [[cats.effect.concurrent.MVar]] */
  private final class TaskMVarImpl[A](mvar: CatsMVar[Task, A]) extends MVar[A] {
    override def isEmpty: Task[Boolean] =
      mvar.isEmpty
    override def put(a: A): Task[Unit] =
      mvar.put(a)
    override def tryPut(a: A): Task[Boolean] =
      mvar.tryPut(a)
    override def take: Task[A] =
      mvar.take
    override def tryTake: Task[Option[A]] =
      mvar.tryTake
    override def read: Task[A] =
      mvar.read
  }
}
