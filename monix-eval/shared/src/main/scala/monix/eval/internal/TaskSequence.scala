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

package monix.eval.internal

import monix.eval.Task
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable

private[eval] object TaskSequence {
  /** Implementation for `Task.sequence`. */
  def list[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {

    def loop(cursor: Iterator[Task[A]], acc: mutable.Builder[A, M[A]]): Task[M[A]] = {
      if (cursor.hasNext) {
        val next = cursor.next()
        next.flatMap { a => loop(cursor, acc += a) }
      } else {
        Task.now(acc.result())
      }
    }

    Task.defer {
      val cursor: Iterator[Task[A]] = in.toIterator
      loop(cursor, cbf(in))
    }
  }

  /** Implementation for `Task.traverse`. */
  def traverse[A, B, M[X] <: TraversableOnce[X]](in: M[A], f: A => Task[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Task[M[B]] = {

    def loop(cursor: Iterator[A], acc: mutable.Builder[B, M[B]]): Task[M[B]] = {
      if (cursor.hasNext) {
        val next = f(cursor.next())
        next.flatMap { a => loop(cursor, acc += a) }
      } else {
        Task.now(acc.result())
      }
    }

    Task.defer {
      loop(in.toIterator, cbf(in))
    }
  }
}
