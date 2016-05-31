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

package monix.cats

import cats.{Bimonad, Group, Monad, MonadError, Monoid, Semigroup}
import minitest.SimpleTestSuite
import monix.cats.implicits._
import monix.eval.Coeval

object CoevalCatsSanitySuite extends SimpleTestSuite {
  test("Coeval is Monad") {
    val ref = implicitly[Monad[Coeval]]
    assert(ref != null)
  }

  test("Coeval has Monad syntax") {
    val coeval = Coeval(1)
    val product = coeval.product(Coeval(2))
    assertEquals(product.value, (1,2))
  }

  test("Coeval is MonadError") {
    val ref = implicitly[MonadError[Coeval, Throwable]]
    assert(ref != null)
  }

  test("Coeval is Bimonad") {
    val ref = implicitly[Bimonad[Coeval]]
    assert(ref != null)
  }

  test("Coeval is Semigroup") {
    val ref = implicitly[Semigroup[Coeval[Int]]]
    assert(ref != null)
  }

  test("Coeval is Monoid") {
    val ref = implicitly[Monoid[Coeval[Int]]]
    assert(ref != null)
  }

  test("Coeval is Group") {
    val ref = implicitly[Group[Coeval[Int]]]
    assert(ref != null)
  }
}
