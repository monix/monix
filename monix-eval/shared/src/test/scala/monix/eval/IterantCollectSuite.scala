/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.execution.exceptions.DummyException
import monix.eval.Iterant.Suspend
import org.scalacheck.Test.Parameters
import scala.util.Failure

object IterantCollectSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters =
    super.checkConfig.withMaxSize(64)

  test("Iterant.collect <=> List.collect") { implicit s =>
    check3 { (stream: Iterant[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf).toListL
      val expected = stream.toListL.map((x: List[Int]) => x.collect(pf))
      received === expected
    }
  }

  test("Iterant.collect protects against user error") { implicit s =>
    check1 { (stream: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream ++ Iterant.now(1)).collect[Int] { case _ => throw dummy }
      received === Iterant.raiseError(dummy)
    }
  }

  test("Iterant.collect flatMap equivalence") { implicit s =>
    check3 { (stream: Iterant[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf)
      val expected = stream.flatMap(x => if (pf.isDefinedAt(x)) Iterant.now(pf(x)) else Iterant.empty[Int])
      received === expected
    }
  }

  test("Iterant.collect suspends the evaluation for NextGen") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterable(dummy)
    val iter = Iterant.nextGenS[Int](items, Task.now(Iterant.empty), Task.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.collect suspends the evaluation for NextSeq") { implicit s =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterator(dummy)
    val iter = Iterant.nextSeqS[Int](items, Task.now(Iterant.empty), Task.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.collect suspends the evaluation for Next") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.nextS(1, Task.now(Iterant.empty), Task.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Int]], "state.isInstanceOf[Suspend[Int]]")
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.collect suspends the evaluation for Last") { implicit s =>
    val dummy = DummyException("dummy")
    val iter = Iterant.lastS(1)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Int]])
    assertEquals(state.toListL.runAsync.value, Some(Failure(dummy)))
  }

  test("Iterant.collect doesn't touch Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Int] = Iterant.haltS(Some(dummy))
    val state1 = iter1.collect { case x => x }
    assertEquals(state1, iter1)

    val iter2: Iterant[Int] = Iterant.haltS(None)
    val state2 = iter2.collect { case x => (throw dummy) : Int }
    assertEquals(state2, iter2)
  }
}
