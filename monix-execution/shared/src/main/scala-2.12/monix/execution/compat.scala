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

package monix.execution

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable

object compat {
  type BuildFrom[-From, -A, +C] = CanBuildFrom[From, A, C]

  private[monix] object internal {
    type IterableOnce[+X] = scala.collection.GenTraversableOnce[X]
    def toIterator[X](i: IterableOnce[X]): Iterator[X] = i.toIterator
    def hasDefiniteSize[X](i: IterableOnce[X]): Boolean = i.hasDefiniteSize

    def newBuilder[From, A, C](bf: BuildFrom[From, A, C], from: From): mutable.Builder[A, C] =
      bf.apply(from)

    def toSeq[A](array: Array[AnyRef]): Seq[A] =
      new scala.collection.mutable.WrappedArray.ofRef(array).toSeq.asInstanceOf[Seq[A]]
  }

  private[monix] object Features {
    type Flag <: Long with monix.execution.Features.FlagTag

    type Flags <: Long with monix.execution.Features.FlagsTag
  }
}
