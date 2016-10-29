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

package monix.types.tests

/** Type-class for testing equality, used only in tests. */
trait Eq[T] { def apply(x: T, y: T): Boolean }

object Eq {
  implicit val intEq: Eq[Int] = new Eq[Int] {
    def apply(x: Int, y: Int): Boolean = x == y
  }

  implicit val longEq: Eq[Long] = new Eq[Long] {
    def apply(x: Long, y: Long): Boolean = x == y
  }

  implicit val shortEq: Eq[Short] = new Eq[Short] {
    def apply(x: Short, y: Short): Boolean = x == y
  }
}
