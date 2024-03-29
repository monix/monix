/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.tail.batches.{ BatchCursor, GenericBatch }

/** Batch that throws exception on access. */
final class ThrowExceptionBatch[A](ex: Throwable) extends GenericBatch[A] {

  private[this] val triggered = Atomic(false)
  def isTriggered: Boolean = triggered.get()

  override def cursor(): BatchCursor[A] = {
    triggered.set(true)
    throw ex
  }
}

object ThrowExceptionBatch {
  def apply[A](ex: Throwable): ThrowExceptionBatch[A] =
    new ThrowExceptionBatch[A](ex)
}
