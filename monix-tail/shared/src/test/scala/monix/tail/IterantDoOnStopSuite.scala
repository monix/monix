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

package monix.tail

import monix.eval.{Coeval, Task}
import monix.tail.cursors.Generator

object IterantDoOnStopSuite extends BaseTestSuite {
  test("Next.stopHandler") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty), ref)
    assertEquals(iterant.stopHandler, ref)
  }

  test("Next.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty), ref1).doOnStop(ref2)
    iterant.stopHandler.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextSeq.stopHandler") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextSeqS(Cursor.empty[Int], Task.now(Iterant[Task].empty), ref)
    assertEquals(iterant.stopHandler, ref)
  }

  test("NextSeq.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextSeqS(Cursor.empty[Int], Coeval.now(Iterant[Coeval].empty), ref1).doOnStop(ref2)
    iterant.stopHandler.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextGen.stopHandler") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextGenS(Generator.empty[Int], Task.now(Iterant[Task].empty), ref)
    assertEquals(iterant.stopHandler, ref)
  }

  test("NextGen.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextGenS(Generator.empty[Int], Coeval.now(Iterant[Coeval].empty), ref1).doOnStop(ref2)
    iterant.stopHandler.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Suspend.stopHandler") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].suspendS(Task.now(Iterant[Task].empty[Int]), ref)
    assertEquals(iterant.stopHandler, ref)
  }

  test("Suspend.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].suspendS(Coeval.now(Iterant[Coeval].empty), ref1).doOnStop(ref2)
    iterant.stopHandler.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Last.stopHandler") { _ =>
    val stop = Iterant[Task].lastS(1).stopHandler
    assertEquals(stop, Task.unit)
  }

  test("Last.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val iterant = Iterant[Coeval].lastS(1).doOnStop(ref1)
    iterant.stopHandler.value
    assertEquals(effect, Vector.empty)
  }

  test("Halt.stopHandler") { _ =>
    val stop = Iterant[Task].haltS(None).stopHandler
    assertEquals(stop, Task.unit)
  }

  test("Halt.doOnStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val iterant = Iterant[Coeval].empty[Int].doOnStop(ref1)
    iterant.stopHandler.value
    assertEquals(effect, Vector.empty)
  }
}
