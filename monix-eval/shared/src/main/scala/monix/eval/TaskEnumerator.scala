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

import monix.eval.internal.{EnumeratorLike, EnumeratorLikeBuilders}
import monix.execution.{CancelableFuture, Scheduler}
import scala.util.control.NonFatal

/** An `TaskEnumerator` represents a [[Task]] based asynchronous stream.
  *
  * The implementation is practically wrapping
  * a [[ConsStream]] of [[Task]], provided for convenience.
  */
final case class TaskEnumerator[+A](stream: ConsStream[A,Task])
  extends EnumeratorLike[A, Task, TaskEnumerator] {

  protected override
  def transform[B](f: (ConsStream[A, Task]) => ConsStream[B, Task]): TaskEnumerator[B] = {
    val next = try f(stream) catch { case NonFatal(ex) => ConsStream.Error[Task](ex) }
    TaskEnumerator(next)
  }

  /** Consumes the stream and for each element
    * execute the given function.
    */
  def foreach(f: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] =
    foreachL(f).runAsync
}

object TaskEnumerator extends EnumeratorLikeBuilders[Task, TaskEnumerator] {
  def fromStream[A](stream: ConsStream[A, Task]): TaskEnumerator[A] =
    TaskEnumerator(stream)
}