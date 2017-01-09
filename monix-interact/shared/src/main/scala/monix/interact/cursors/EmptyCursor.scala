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

package monix.interact.cursors

import java.util

import monix.interact.Cursor
import monix.interact.exceptions.CursorIsEmptyException

object EmptyCursor extends Cursor[Nothing] {
  override def current: Nothing =
    throw new CursorIsEmptyException()

  override def moveNext(): Boolean = false
  override def hasMore(): Boolean = false

  override def take(n: Int): Cursor[Nothing] = this
  override def drop(n: Int): Cursor[Nothing] = this
  override def map[B](f: (Nothing) => B): Cursor[B] = this
  override def filter(p: (Nothing) => Boolean): Cursor[Nothing] = this
  override def collect[B](pf: PartialFunction[Nothing, B]): Cursor[B] = this
  override def slice(from: Int, until: Int): Cursor[Nothing] = this

  override def toIterator: Iterator[Nothing] =
    Iterator.empty

  override def toJavaIterator[B >: Nothing]: util.Iterator[B] = {
    import scala.collection.JavaConverters._
    toIterator.asJava
  }
}
