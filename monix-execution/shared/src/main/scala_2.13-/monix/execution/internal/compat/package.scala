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

import scala.reflect.ClassTag

package object compat {

  type IterableOnce[+X] = scala.collection.GenTraversableOnce[X]
  type TraversableOnce[+X] = scala.collection.TraversableOnce[X]

  def toIterator[X](i: IterableOnce[X]): Iterator[X] = i.toIterator
  def toArray[X : ClassTag](i: IterableOnce[X]): Array[X] = i.toArray
  def hasDefiniteSize[X](i: IterableOnce[X]): Boolean = i.hasDefiniteSize
}
