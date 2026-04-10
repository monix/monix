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

package monix.execution.misc

import minitest.SimpleTestSuite
import monix.execution.CancelableFuture

import scala.concurrent.Future

object CanBindLocalsSuite extends SimpleTestSuite {
  class MySimpleType
  class MyType[A]

  test("default implicits") {
    val ev1 = implicitly[CanBindLocals[CancelableFuture[Int]]]
    val ev2 = implicitly[CanBindLocals[Future[Int]]]
    val ev3 = implicitly[CanBindLocals[Unit]]

    assert(ev1 != ev2, "ev1 != ev2")
    assert(ev1.asInstanceOf[Any] != ev3.asInstanceOf[Any], "ev1 != ev3")

    assertDoesNotCompile("implicitly[CanBindLocals[MySimpleType]]")
    assertDoesNotCompile("implicitly[CanBindLocals[MyType[String]]]")
    assertDoesNotCompile("implicitly[CanBindLocals[Int]]")
  }

  test("import CanBindLocals.Implicits.synchronousAsDefault") {
    import CanBindLocals.Implicits.synchronousAsDefault

    val ev1 = implicitly[CanBindLocals[MySimpleType]]
    val ev2 = implicitly[CanBindLocals[MyType[String]]]
    val ev3 = implicitly[CanBindLocals[CancelableFuture[Int]]]
    val ev4 = implicitly[CanBindLocals[Future[Int]]]
    val ev5 = implicitly[CanBindLocals[Unit]]
    val ev6 = implicitly[CanBindLocals[Int]]

    assertEquals(ev1, ev2)
    assertEquals(ev1, ev5)
    assertEquals(ev1, ev6)

    assert(ev1.asInstanceOf[Any] != ev3.asInstanceOf[Any], "ev1 != ev3")
    assert(ev1.asInstanceOf[Any] != ev4.asInstanceOf[Any], "ev1 != ev3")
  }
}
