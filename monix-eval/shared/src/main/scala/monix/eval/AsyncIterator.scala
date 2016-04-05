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

import monix.eval.internal.{IteratorLikeBuilders, IteratorLike}
import scala.util.control.NonFatal

/** An `AsyncIterator` represents a [[Task]] based asynchronous iterator.
  *
  * The implementation is practically wrapping
  * a [[ConsStream]] of [[Task]], provided for convenience.
  */
final case class AsyncIterator[+A](stream: ConsStream[A,Task])
  extends IteratorLike[A, Task, AsyncIterator] {

  def transform[B](f: (ConsStream[A, Task]) => ConsStream[B, Task]): AsyncIterator[B] = {
    val next = try f(stream) catch { case NonFatal(ex) => ConsStream.Error[Task](ex) }
    AsyncIterator(next)
  }
}

object AsyncIterator extends IteratorLikeBuilders[Task, AsyncIterator] {
  def fromStream[A](stream: ConsStream[A, Task]): AsyncIterator[A] =
    AsyncIterator(stream)
}