/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval.internal

/** A mapping function type that is also able to handle errors.
  *
  * Used in the `Task` and `Coeval` implementations to specify
  * error handlers in their respective `FlatMap` internal states.
  */
private[eval] abstract class Transformation[-A, +R]
  extends (A => R) { self =>

  final override def apply(a: A): R =
    success(a)

  def success(a: A): R
  def error(e: Throwable): R

  override def andThen[X](g: (R) => X): Transformation[A, X] =
    new Transformation[A, X] {
      def success(a: A): X =
        g(self.success(a))
      def error(e: Throwable): X =
        g(self.error(e))
    }
}

private[eval] object Transformation {
  /** Builds a [[Transformation]] instance. */
  def apply[A, R](fa: A => R, fe: Throwable => R): Transformation[A, R] =
    new Fold(fa, fe)

  /** Builds a [[Transformation]] instance that only handles errors,
    * otherwise mirroring the value on `success`.
    */
  def onError[A, R](fa: A => R, fe: Throwable => R): Transformation[A, R] =
    new OnError(fa, fe)

  /** [[Transformation]] reference that's only handles errors,
    * useful for quick filtering of `onErrorHandleWith` frames.
    */
  final class OnError[-A, +R](fa: A => R, fe: Throwable => R)
    extends Fold[A, R](fa, fe)

  class Fold[-A, +R](fa: A => R, fe: Throwable => R)
    extends Transformation[A, R] {

    final override def success(a: A): R = fa(a)
    final override def error(e: Throwable): R = fe(e)
  }
}
