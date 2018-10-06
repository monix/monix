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



class Playground {

  // Using cats.effect.IO for evaluating our side effects
  import cats.effect.IO

  sealed trait State[+A] { def count: Int }
  case object Init extends State[Nothing] { def count = 0 }
  case class Current[A](current: Option[A], count: Int)
    extends State[A]

  case class Person(id: Int, name: String)

  // TODO: to implement!
  def requestPersonDetails(id: Int): IO[Option[Person]] =
    IO.raiseError(new NotImplementedError)

  // TODO: to implement
  val source: Observable[Int] =
    Observable.raiseError(new NotImplementedError)

  // Initial state
  val seed = IO.pure(Init : State[Person])

  val scanned = source.scanEvalF(seed) { (state, id) =>
      requestPersonDetails(id).map { person =>
          state match {
            case Init =>
                Current(person, 1)
              case Current(_, count) =>
                Current(person, count + 1)
            }
        }
    }

  val filtered = scanned
    .takeWhile(_.count < 10)
    .collect { case Current(a, _) => a }

  Observable.range(0, 1000).foldWhileLeft((0L, 0)) {
    case ((sum, count), e) =>
      val next = (sum + e, count + 1)
      if (count + 1 < 10) Left(next) else Right(next)
  }
}
