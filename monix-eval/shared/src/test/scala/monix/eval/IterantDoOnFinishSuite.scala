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

import scala.util.Success

object IterantDoOnFinishSuite extends BaseTestSuite {
  test("Next.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextS(1, Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("Next.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextS(1, Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect, Vector(2))
  }

  test("NextSeq.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextSeqS(List(1).iterator, Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("NextSeq.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextSeqS(List(1).iterator, Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect, Vector(2))
  }

  test("NextGen.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextGenS(List(1), Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("NextGen.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextGenS(List(1), Task.now(Iterant.empty[Int]), ref1).doOnFinish(_ => ref2)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect, Vector(2))
  }

  test("Suspend.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val suspended = Iterant.now(1)
    val iterant = Iterant.suspendS(Task.now(suspended), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("Suspend.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val suspended = Iterant.now(1)
    val iterant = Iterant.suspendS(Task.now(suspended), ref1).doOnFinish(_ => ref2)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect, Vector(2))
  }

  test("Last.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }

    val iterant = Iterant.lastS(1).doOnFinish(_ => ref1)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1))
  }

  test("Last.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }

    val iterant = Iterant.lastS(1).doOnFinish(_ => ref1)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(1)))
    assertEquals(effect, Vector(1))
  }

  test("Halt.doOnFinish for early stop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }

    val iterant = Iterant.haltS[Int](None).doOnFinish(_ => ref1)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1))
  }

  test("Halt.doOnFinish for halt") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }

    val iterant = Iterant.haltS[Int](None).doOnFinish(_ => ref1)
    val f = iterant.foldLeftL(0)(_+_).runAsync; s.tick()

    assertEquals(f.value, Some(Success(0)))
    assertEquals(effect, Vector(1))
  }

  test("doOnFinish protects against user error") { implicit s =>
    check1 { (stream: Iterant[Int]) =>
      val dummy = DummyException("dummy")
      val received = stream.doOnFinish(_ => throw dummy)
      received === Iterant.raiseError(dummy)
    }
  }
}
