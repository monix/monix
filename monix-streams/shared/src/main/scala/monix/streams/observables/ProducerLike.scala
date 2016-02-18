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

package monix.streams.observables

import monix.streams.internal.operators2._
import monix.streams.observables.ProducerLike.Operator
import monix.streams.observers.Subscriber
import monix.streams.{Pipe, CanObserve, Observable}
import scala.language.higherKinds

/** Defines the available operations for observable-like instances.
  *
  * @define concatMergeDifference The difference between the `concat` operation
  *         and `merge`is that `concat` cares about ordering of emitted items
  *         (e.g. all items emitted by the first observable in the sequence
  *         will come before the elements emitted by the second observable),
  *         whereas `merge` doesn't care about that (elements get
  *         emitted as they come). Because of back-pressure applied to
  *         observables, `concat` is safe to use in all
  *         contexts, whereas `merge` requires buffering.
  * @define concatDescription Concatenates the sequence
  *         of observables emitted by the source into one observable,
  *         without any transformation.
  *
  *         You can combine the items emitted by multiple observables
  *         so that they act like a single sequence by using this
  *         operator.
  *
  *         $concatMergeDifference
  * @define concatReturn an Observable that emits items that are the result of
  *         flattening the items emitted by the Observables emitted by `this`
  */
abstract class ProducerLike[+A, Self[+T]] { self: Self[A] =>
  /** Transforms the source using the given operator. */
  def lift[B](operator: Operator[A,B]): Self[B]

  /** Given a [[monix.streams.Pipe Pipe]], transform
    * the source observable with it.
    */
  def transform[I >: A, B](pipe: Pipe[I,B]): Self[B] =
    self.lift(new OperatorTransform(pipe))

  /** Applies the given function to each item
    * emitted by the source and emits the result.
    *
    * @param f a function to apply to each item emitted by the source
    *
    * @return an new stream that emits the items from the source,
    *         transformed by the given function
    */
  def map[B](f: A => B): Self[B] =
    self.lift(new OperatorMap(f))

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * $concatMergeDifference
    */
  def concatMap[B, F[_] : CanObserve](f: A => F[B]): Self[B] =
    self.lift(new OperatorConcatMap[A,B,F](f, delayErrors = false))

  /** $concatDescription
    *
    * @return $concatReturn
    */
  def concat[B](implicit ev: A <:< Observable[B]): Self[B] =
    concatMap[B,Observable](x => x)

  /** Applies a function that you supply to each item emitted by the
    * source observable, where that function returns sequences that
    * [[CanObserve can be observed]], and then concatenating those
    * resulting sequences and emitting the results of this concatenation.
    *
    * Alias for [[concatMap]].
    *
    * $concatMergeDifference
    */
  def flatMap[B, F[_] : CanObserve](f: A => F[B]): Self[B] =
    self.concatMap(f)

  /** $concatDescription
    *
    * Alias for [[concat]].
    *
    * @return $concatReturn
    */
  def flatten[B](implicit ev: A <:< Observable[B]): Self[B] =
    concat
}

object ProducerLike {
  /** An `Operator` is a function for transforming observers,
    * that can be used for lifting observables.
    */
  type Operator[-I,+O] = Subscriber[O] => Subscriber[I]
}
