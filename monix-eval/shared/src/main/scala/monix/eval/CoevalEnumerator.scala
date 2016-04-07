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

package monix.eval

import monix.eval.ConsStream._
import monix.eval.internal.{EnumeratorLike, EnumeratorLikeBuilders}
import scala.util.control.NonFatal

/** An `CoevalEnumerator` represents a [[Coeval]] based synchronous
  * iterator.
  *
  * The implementation is practically wrapping
  * a [[ConsStream]] of [[Coeval]], provided for convenience.
  */
final case class CoevalEnumerator[+A](stream: ConsStream[A,Coeval])
  extends EnumeratorLike[A, Coeval, CoevalEnumerator] {

  def transform[B](f: (ConsStream[A, Coeval]) => ConsStream[B, Coeval]): CoevalEnumerator[B] = {
    val next = try f(stream) catch { case NonFatal(ex) => ConsStream.Error[Coeval](ex) }
    CoevalEnumerator(next)
  }

  /** Converts this lazy iterator into an async iterator. */
  def toAsyncIterator: TaskEnumerator[A] = {
    def convert(stream: ConsStream[A, Coeval]): ConsStream[A, Task] =
      stream match {
        case Next(elem, rest) =>
          Next(elem, rest.task.map(convert))

        case NextSeq(elems, rest) =>
          NextSeq(elems, rest.task.map(convert))

        case Wait(rest) => Wait(rest.task.map(convert))
        case Empty() => Empty[Task]()
        case Error(ex) => Error[Task](ex)
      }

    TaskEnumerator(convert(stream))
  }

  /** Consumes the stream and for each element execute the given function. */
  def foreach(f: A => Unit): Unit =
    foreachL(f).value
}

object CoevalEnumerator extends EnumeratorLikeBuilders[Coeval, CoevalEnumerator] {
  def fromStream[A](stream: ConsStream[A, Coeval]): CoevalEnumerator[A] =
    CoevalEnumerator(stream)
}