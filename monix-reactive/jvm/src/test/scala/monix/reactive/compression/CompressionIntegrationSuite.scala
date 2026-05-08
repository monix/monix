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

package monix.reactive.compression

import monix.execution.Scheduler
import org.scalacheck.{ Arbitrary, Prop }

trait CompressionIntegrationSuite extends monix.execution.MUnitFunSuite {

  implicit val scheduler: Scheduler =
    Scheduler.computation(parallelism = 4, name = "compression-tests", daemonic = true)

  def assertArrayEquals[T](a1: Array[T], a2: Array[T]): Unit = {
    assertEquals(a1.toList, a2.toList)
  }

  def check(prop: Prop): Unit = {
    val result = org.scalacheck.Test.check(scalaCheckTestParameters, prop)
    assert(result.passed, clue(result.toString))
  }

  def check1[A: Arbitrary](f: A => Prop): Unit =
    check(Prop.forAll(f))
}
