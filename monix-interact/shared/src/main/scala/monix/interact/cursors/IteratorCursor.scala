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
import monix.interact.exceptions.{CursorIsFinishedException, CursorNotStartedException}

/** [[monix.interact.Cursor Cursor]] type that works over
  * an underlying `Iterator`.
  *
  * NOTE: all transformations are delegated to the underlying
  * `Iterator` and may thus have lazy behavior.
  */
class IteratorCursor[+A](underlying: Iterator[A]) extends Cursor[A] {
  import IteratorCursor.{IS_DONE, AWAITS_FIRST_MOVE, HAS_CURRENT}

  private[this] var state = AWAITS_FIRST_MOVE
  private[this] var currentRef: A = _

  override def current: A = {
    if (state == HAS_CURRENT) currentRef else
      state match {
        case AWAITS_FIRST_MOVE =>
          throw new CursorNotStartedException
        case IS_DONE =>
          throw new CursorIsFinishedException
      }
  }

  override def moveNext(): Boolean = {
    if (underlying.hasNext) {
      if (state == AWAITS_FIRST_MOVE) state = HAS_CURRENT
      currentRef = underlying.next()
      true
    } else {
      state = IS_DONE
      false
    }
  }

  override def hasMore(): Boolean =
    underlying.hasNext

  override def take(n: Int): Cursor[A] =
    new IteratorCursor[A](underlying.take(n))

  override def drop(n: Int): Cursor[A] =
    new IteratorCursor[A](underlying.drop(n))

  override def slice(from: Int, until: Int): Cursor[A] =
    new IteratorCursor[A](underlying.slice(from, until))

  override def map[B](f: (A) => B): Cursor[B] =
    new IteratorCursor(underlying.map(f))

  override def filter(p: (A) => Boolean): Cursor[A] =
    new IteratorCursor(underlying.filter(p))

  override def collect[B](pf: PartialFunction[A, B]): Cursor[B] =
    new IteratorCursor(underlying.collect(pf))

  override def toIterator: Iterator[A] =
    underlying

  override def toJavaIterator[B >: A]: util.Iterator[B] = {
    import scala.collection.JavaConverters._
    underlying.asInstanceOf[Iterator[B]].asJava
  }
}

object IteratorCursor {
  /** Internal state of a cursor, signals that we wait for the first move. */
  private final val AWAITS_FIRST_MOVE = 0
  /** Internal state of a cursor, indicates a current elem is available. */
  private final val HAS_CURRENT = 1
  /** Internal state of a cursor, indicates that iteration should stop. */
  private final val IS_DONE = 2
}