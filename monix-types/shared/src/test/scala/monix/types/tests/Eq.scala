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

package monix.types.tests

import scala.concurrent.ExecutionException

/** Type-class for testing equality, used only in tests. */
trait Eq[A] { def apply(x: A, y: A): Boolean }

object Eq {
  implicit val intEq: Eq[Int] = new Eq[Int] {
    def apply(x: Int, y: Int): Boolean = x == y
  }

  implicit val longEq: Eq[Long] = new Eq[Long] {
    def apply(x: Long, y: Long): Boolean = x == y
  }

  implicit val shortEq: Eq[Short] = new Eq[Short] {
    def apply(x: Short, y: Short): Boolean = x == y
  }

  implicit def eitherEq[A : Eq, B : Eq]: Eq[Either[A, B]] =
    new Eq[Either[A, B]] {
      override def apply(x: Either[A, B], y: Either[A, B]): Boolean = {
        x match {
          case Left(a1) =>
            y match {
              case Left(a2) => implicitly[Eq[A]].apply(a1, a2)
              case _ => false
            }
          case Right(b1) =>
            y match {
              case Right(b2) => implicitly[Eq[B]].apply(b1, b2)
              case _ => false
            }
        }
      }
    }

  implicit val exceptionEq: Eq[Throwable] =
    new Eq[Throwable] {
      // Unwraps exceptions that got caught by Future's implementation
      // and that got wrapped in ExecutionException (`Future(throw ex)`)
      def extractEx(ex: Throwable): Throwable =
        ex match {
          case ref: ExecutionException =>
            Option(ref.getCause).getOrElse(ref)
          case _ => ex
        }

      def apply(x: Throwable, y: Throwable): Boolean =
        extractEx(x) == extractEx(y)
    }
}
