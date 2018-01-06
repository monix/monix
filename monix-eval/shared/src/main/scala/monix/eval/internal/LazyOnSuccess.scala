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

import monix.eval.Coeval
import scala.util.Success

private[eval] final class LazyOnSuccess[A](f: () => A) extends (() => A) { self =>
  private[this] var cache: Success[A] = _
  private[this] var thunk = f

  override def apply(): A = {
    // Doing double-checked locking, see:
    // https://en.wikipedia.org/wiki/Double-checked_locking
    val result = cache
    if (result != null) result.get
    else compute()
  }

  private[this] def compute(): A =
    self.synchronized {
      cache match {
        case null =>
          // Will throw in case of trouble!
          val result = thunk()
          cache = Success(result)
          thunk = null // GC purposes
          result
        case Success(value) =>
          value
      }
    }
}

private[eval] object LazyOnSuccess {
  def apply[A](f: () => A): (() => A) =
    f match {
      case _: LazyOnSuccess[_] => f
      case _: Coeval.Once[_] => f
      case _ => new LazyOnSuccess[A](f)
    }
}