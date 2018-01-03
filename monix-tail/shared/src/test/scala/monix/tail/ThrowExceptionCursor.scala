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

package monix.tail

import monix.execution.atomic.Atomic
import monix.tail.batches.BatchCursor

/** BatchCursor that throws exception on access. */
final class ThrowExceptionCursor[A](ex: Throwable) extends BatchCursor[A] { self =>
  private[this] val triggered = Atomic(false)
  def isTriggered: Boolean = triggered.get

  private def triggerError(): Nothing = {
    triggered := true
    throw ex
  }

  override def recommendedBatchSize: Int = 1
  override def toIterator: Iterator[A] =
    new Iterator[A] { def hasNext = self.hasNext(); def next() = self.next() }

  override def hasNext(): Boolean = triggerError()
  override def next(): A = triggerError()

  override def take(n: Int): BatchCursor[A] = triggerError()
  override def drop(n: Int): BatchCursor[A] = triggerError()
  override def slice(from: Int, until: Int): BatchCursor[A] = triggerError()
  override def map[B](f: A => B): BatchCursor[B] = triggerError()
  override def filter(p: A => Boolean): BatchCursor[A] = triggerError()
  override def collect[B](pf: PartialFunction[A, B]): BatchCursor[B] = triggerError()
}

object ThrowExceptionCursor {
  def apply[A](ex: Throwable): ThrowExceptionCursor[A] =
    new ThrowExceptionCursor[A](ex)
}