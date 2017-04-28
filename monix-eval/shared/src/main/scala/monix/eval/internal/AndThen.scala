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

import java.io.Serializable
import monix.eval.Task
import scala.util.{Failure, Success, Try}

/** A type-aligned seq for representing function composition in
  * constant stack space with amortized linear time application (in the
  * number of constituent functions).
  *
  * CREDITS: Daniel Spiewak and Michael Pilquist, taken from
  * the `cats.effect.IO` type from the `cats-effect` project.
  */
private[eval] sealed abstract class AndThen[-A, +B] extends Product with Serializable {
  import AndThen._

  final def apply(a: A): B = {
    var self: AndThen[Any, Any] = this.asInstanceOf[AndThen[Any, Any]]
    var cur: Any = a.asInstanceOf[Any]
    var continue = true

    while (continue) {
      self match {
        case Single(f) =>
          cur = f(cur)
          continue = false

        case Concat(Single(f), right) =>
          cur = f(cur)
          self = right.asInstanceOf[AndThen[Any, Any]]

        case Concat(left @ Concat(_, _), right) =>
          self = left.rotateAccum(right)

        case Concat(ss, right) =>
          val left = ss.asInstanceOf[ShortCircuit[Any, Any]]

          cur match {
            case Success(value) =>
              self = left.inner.andThen(right).asInstanceOf[AndThen[Any, Any]]
              cur = value.asInstanceOf[Any]

            case Failure(e: Throwable) =>
              cur = Task.now(Failure(e))
              self = right.asInstanceOf[AndThen[Any, Any]]

            case _ => 
              throw new IllegalStateException("Contact support, seek help, see https://monix.io")
          }

        case ss =>
          val ssc = ss.asInstanceOf[ShortCircuit[Any, Any]]

          cur match {
            case Failure(e: Throwable) =>
              cur = Task.now(Failure(e))
              continue = false

            case Success(value) =>
              self = ssc.inner.asInstanceOf[AndThen[Any, Any]]
              cur = value.asInstanceOf[Any]

            case _ =>
              throw new IllegalStateException("Contact support, seek help, see https://monix.io")
          }
      }
    }

    cur.asInstanceOf[B]
  }

  final def andThen[X](right: AndThen[B, X]): AndThen[A, X] = Concat(this, right)
  final def compose[X](right: AndThen[X, A]): AndThen[X, B] = Concat(right, this)

  final def shortCircuit[E](implicit ev: B <:< Task[Try[E]]) = {
    val _ = ev
    ShortCircuit[A, E](this.asInstanceOf[AndThen[A, Task[Try[E]]]])
  }

  // converts left-leaning to right-leaning
  protected final def rotateAccum[E](_right: AndThen[B, E]): AndThen[A, E] = {
    var self: AndThen[Any, Any] = this.asInstanceOf[AndThen[Any, Any]]
    var right: AndThen[Any, Any] = _right.asInstanceOf[AndThen[Any, Any]]
    var continue = true

    while (continue) {
      self match {
        case Concat(left, inner) =>
          self = left.asInstanceOf[AndThen[Any, Any]]
          right = inner.andThen(right)

        // Either Single or ShortCircuit; the latter doesn't type check
        case _ =>
          self = self.andThen(right)
          continue = false
      }
    }

    self.asInstanceOf[AndThen[A, E]]
  }

  override def toString =
    "AndThen$" + System.identityHashCode(this)
}

private[eval] object AndThen {
  def apply[A, B](f: A => B): AndThen[A, B] = Single(f)

  final case class Single[-A, +B](f: A => B) extends AndThen[A, B]
  final case class Concat[-A, E, +B](left: AndThen[A, E], right: AndThen[E, B]) extends AndThen[A, B]
  final case class ShortCircuit[-A, +B](inner: AndThen[A, Task[Try[B]]]) extends AndThen[Try[A], Task[Try[B]]]
}
