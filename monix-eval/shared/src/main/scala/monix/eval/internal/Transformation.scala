/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.eval.Task

import scala.util.{Failure, Success, Try}

private[eval] abstract class Transformation[-A, +R] extends (A => R) { self =>
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
  def fold[A, R](fa: A => R, fe: Throwable => R): Transformation[A, R] =
    new Fold(fa, fe)

  def materialize[A]: Transformation[A, Task[Try[A]]] =
    Materialize.asInstanceOf[Transformation[A, Task[Try[A]]]]

  private final class Fold[A, R](fa: A => R, fe: Throwable => R)
    extends Transformation[A, R] {

    override def success(a: A): R = fa(a)
    override def error(e: Throwable): R = fe(e)
  }

  private object Materialize extends Transformation[Any, Task[Try[Any]]] {
    override def success(a: Any): Task[Try[Any]] =
      Task.now(Success(a))

    override def error(e: Throwable): Task[Try[Nothing]] =
      Task.now(Failure(e))
  }
}
