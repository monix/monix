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

import cats.laws._
import cats.laws.discipline._

import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import monix.tail.batches._

object IterantDoOnFinishSuite extends BaseTestSuite {
  test("Next.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Next.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextS(1, Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnFinish(_ => ref2)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 1)
    assertEquals(effect, Vector(2))
  }

  test("NextCursor.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextCursorS(
      BatchCursor(1), 
      Coeval.now(Iterant[Coeval].empty[Int]), 
      ref1)
      .doOnFinish(_ => ref2)
    
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextCursor.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextCursorS(BatchCursor(1), Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnFinish(_ => ref2)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 1)
    assertEquals(effect, Vector(2))
  }

  test("NextBatch.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextBatchS(Batch(1), Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("NextBatch.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val iterant = Iterant[Coeval].nextBatchS(Batch(1), Coeval.now(Iterant[Coeval].empty[Int]), ref1).doOnFinish(_ => ref2)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 1)
    assertEquals(effect, Vector(2))
  }

  test("Suspend.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val suspended = Iterant[Coeval].now(1)
    val iterant = Iterant[Coeval].suspendS(Coeval.now(suspended), ref1).doOnFinish(_ => ref2)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1, 2))
  }

  test("Suspend.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }
    val ref2 = Coeval.eval { effect = effect :+ 2 }

    val suspended = Iterant[Coeval].now(1)
    val iterant = Iterant[Coeval].suspendS(Coeval.now(suspended), ref1).doOnFinish(_ => ref2)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 1)
    assertEquals(effect, Vector(2))
  }

  test("Last.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }

    val iterant = Iterant[Coeval].lastS(1).doOnFinish(_ => ref1)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1))
  }

  test("Last.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }

    val iterant = Iterant[Coeval].lastS(1).doOnFinish(_ => ref1)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 1)
    assertEquals(effect, Vector(1))
  }

  test("Halt.doOnFinish for early stop") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }

    val iterant = Iterant[Coeval].haltS[Int](None).doOnFinish(_ => ref1)
    iterant.earlyStop.value
    assertEquals(effect, Vector(1))
  }

  test("Halt.doOnFinish for halt") { _ =>
    var effect = Vector.empty[Int]
    val ref1 = Coeval.eval { effect = effect :+ 1 }

    val iterant = Iterant[Coeval].haltS[Int](None).doOnFinish(_ => ref1)
    assertEquals(iterant.foldLeftL(0)(_+_).value, 0)
    assertEquals(effect, Vector(1))
  }

  test("doOnFinish protects against user error") { _ =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val received = stream.doOnFinish(_ => throw dummy)
      received <-> Iterant[Coeval].raiseError(dummy)
    }
  }
}
