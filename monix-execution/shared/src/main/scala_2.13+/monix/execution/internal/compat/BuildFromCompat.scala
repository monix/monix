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

package monix.execution.internal.compat

import scala.annotation.implicitNotFound
import scala.collection.BuildFrom
import scala.collection.mutable

@implicitNotFound(msg = "Cannot construct a collection of type ${C} with elements of type ${A} based on a collection of type ${From}.")
trait BuildFromCompat[-From, -A, +C] {
  def newBuilder(from: From): mutable.Builder[A, C]
}

object BuildFromCompat {

  implicit def fromBuildFrom[From, A, C](implicit bf: BuildFrom[From, A, C]): BuildFromCompat[From, A, C] =
    new BuildFromCompat[From, A, C] {
      def newBuilder(from: From): mutable.Builder[A, C] = bf.newBuilder(from)
    }
}
