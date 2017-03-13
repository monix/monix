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

object IterantDoOnEarlyStopSuite extends BaseTestSuite {
  test("Next.earlyStop") { implicit s =>
    val ref = Task.eval(())
    val iterant = Iterant.nextS(1, Task.now(Iterant.empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("Next.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextS(1, Task.now(Iterant.empty[Int]), ref1).doOnEarlyStop(ref2)
    val f = iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("NextSeq.earlyStop") { implicit s =>
    val ref = Task.eval(())
    val iterant = Iterant.nextSeqS(List.empty[Int].iterator, Task.now(Iterant.empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("NextSeq.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextSeqS(List.empty[Int].iterator, Task.now(Iterant.empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("NextGen.earlyStop") { implicit s =>
    val ref = Task.eval(())
    val iterant = Iterant.nextGenS(Iterable.empty[Int], Task.now(Iterant.empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("NextGen.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.nextGenS(Iterable.empty[Int], Task.now(Iterant.empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("Suspend.earlyStop") { implicit s =>
    val ref = Task.eval(())
    val iterant = Iterant.suspendS(Task.now(Iterant.empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("Suspend.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val ref2 = Task.eval { effect = effect :+ 2 }

    val iterant = Iterant.suspendS(Task.now(Iterant.empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector(1, 2))
  }

  test("Last.earlyStop") { implicit s =>
    val stop = Iterant.lastS(1).earlyStop
    assertEquals(stop, Task.unit)
  }

  test("Last.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val iterant = Iterant.lastS(1).doOnEarlyStop(ref1)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector.empty)
  }

  test("Halt.earlyStop") { implicit s =>
    val stop = Iterant.haltS(None).earlyStop
    assertEquals(stop, Task.unit)
  }

  test("Halt.doOnEarlyStop") { implicit s =>
    var effect = Vector.empty[Int]
    val ref1 = Task.eval { effect = effect :+ 1 }
    val iterant = Iterant.empty[Int].doOnEarlyStop(ref1)
    iterant.earlyStop.runAsync; s.tick()
    assertEquals(effect, Vector.empty)
  }
}
