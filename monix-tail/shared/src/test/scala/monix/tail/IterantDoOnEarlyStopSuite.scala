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

package monix.tail

import monix.eval.{Coeval, Task}
import monix.tail.batches._

object IterantDoOnEarlyStopSuite extends BaseTestSuite {
  test("Next.earlyStop") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextS(1, Task.now(Iterant[Task].empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("Next.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextCursor.earlyStop") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextCursorS(BatchCursor.empty[Int], Task.now(Iterant[Task].empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("NextCursor.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextCursorS(BatchCursor.empty[Int], Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextBatch.earlyStop") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].nextBatchS(Batch.empty[Int], Task.now(Iterant[Task].empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("NextBatch.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextBatchS(Batch.empty[Int], Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Suspend.earlyStop") { _ =>
    val ref = Task.eval(())
    val iterant = Iterant[Task].suspendS(Task.now(Iterant[Task].empty[Int]), ref)
    assertEquals(iterant.earlyStop, ref)
  }

  test("Suspend.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].suspendS(Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnEarlyStop(ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Last.earlyStop") { _ =>
    val stop = Iterant[Task].lastS(1).earlyStop
    assertEquals(stop, Task.unit)
  }

  test("Last.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val iterant = Iterant[Coeval].lastS(1).doOnEarlyStop(ref1)
    iterant.earlyStop.value
    assertEquals(effect, Vector.empty)
  }

  test("Halt.earlyStop") { _ =>
    val stop = Iterant[Task].haltS(None).earlyStop
    assertEquals(stop, Task.unit)
  }

  test("Halt.doOnEarlyStop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val iterant = Iterant[Coeval].empty[Int].doOnEarlyStop(ref1)
    iterant.earlyStop.value
    assertEquals(effect, Vector.empty)
  }
}
