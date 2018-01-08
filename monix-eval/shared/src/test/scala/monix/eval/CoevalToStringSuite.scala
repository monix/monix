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

package monix.eval

import minitest.SimpleTestSuite
import monix.execution.exceptions.DummyException

object CoevalToStringSuite extends SimpleTestSuite {
  def assertContains[A](ref: Coeval[A], startStr: String): Unit = {
    val str = ref.toString
    assert(str.startsWith(startStr), s""""$str".startsWith("$startStr")""")
  }

  test("Coeval.Now") {
    assertContains(Coeval.now(1), "Coeval.Now")
  }

  test("Coeval.Error") {
    val ref = Coeval.raiseError(DummyException("dummy"))
    assertContains(ref, "Coeval.Error")
  }

  test("Coeval.Always") {
    val ref = Coeval.eval("hello")
    assertContains(ref, "Coeval.Always")
  }

  test("Coeval.FlatMap") {
    val ref = Coeval.now(1).flatMap(Coeval.pure)
    assertContains(ref, "Coeval.FlatMap")
  }

  test("Coeval.Suspend") {
    val ref = Coeval.defer(Coeval.now(1))
    assertContains(ref, "Coeval.Suspend")
  }

  test("Coeval.Once") {
    val ref = Coeval(1).memoize
    assertContains(ref, "Coeval.Once")
  }

  test("Coeval.Map") {
    val ref = Coeval(1).map(_ + 1)
    assertContains(ref, "Coeval.Map")
  }
}
