/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.execution.internal

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable

package object compat {

  private[monix] type IterableOnce[+X] = scala.collection.GenTraversableOnce[X]
  private[monix] def toIterator[X](i: IterableOnce[X]): Iterator[X] = i.toIterator
  private[monix] def hasDefiniteSize[X](i: IterableOnce[X]): Boolean = i.hasDefiniteSize

  private[monix] type BuildFromCompat[-From, -A, +C] = CanBuildFrom[From, A, C]
  private[monix] def newBuilder[From, A, C](bf: BuildFromCompat[From, A, C], from: From): mutable.Builder[A, C] = bf.apply(from)

}
