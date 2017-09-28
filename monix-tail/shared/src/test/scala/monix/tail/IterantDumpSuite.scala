/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import java.io.{OutputStream, PrintStream}

import monix.eval.{Coeval, Task}
import monix.execution.atomic.AtomicInt
import monix.execution.exceptions.DummyException
import monix.tail.batches.{Batch, BatchCursor}

object IterantDumpSuite extends BaseTestSuite {
  def dummyOut(count: AtomicInt = null) = {
    val out = new OutputStream {
      def write(b: Int) = ()
    }
    new PrintStream(out) {
      override def println(x: String) = {
        super.println(x)
        if (count != null) {
          val c = count.incrementAndGet()
          if (c == 0) throw DummyException("dummy")
        }
      }
    }
  }

  def dummyOutException = {
    val out = new OutputStream {
      def write(b: Int) = ()
    }
    new PrintStream(out) {
      override def println(x: String) = {
        throw DummyException("dummy")
      }
    }
  }

  test("Iterant.dump works for Next") { implicit s =>
    check1 { (el: Int) =>
      val counter = AtomicInt(0)
      val out = Iterant[Task].nextS(el, Task.now(Iterant[Task].empty[Int]), Task.unit).dump("O", dummyOut(counter))
      out.completeL.runAsync
      s.tick()

      counter.get <-> 2
    }
  }

  test("Iterant.dump works for NextCursor") { implicit s =>
    check1 { (el: Int) =>
      val counter = AtomicInt(0)
      val out = Iterant[Task].nextCursorS(BatchCursor(el), Task.now(Iterant[Task].empty[Int]), Task.unit).dump("O", dummyOut(counter))
      out.completeL.runAsync
      s.tick()

      counter.get <-> 2
    }
  }

  test("Iterant.dump works for NextBatch") { implicit s =>
    check1 { (el: Int) =>
      val counter = AtomicInt(0)
      val out = Iterant[Task].nextBatchS(Batch(el), Task.now(Iterant[Task].empty[Int]), Task.unit).dump("O", dummyOut(counter))
      out.completeL.runAsync
      s.tick()

      counter.get <-> 2
    }
  }

  test("Iterant.dump works for Suspend") { implicit s =>
    val counter = AtomicInt(0)
    val out = Iterant[Task].suspend(Task.now(Iterant[Task].empty[Int])).dump("O", dummyOut(counter))
    out.completeL.runAsync
    s.tick()

    assertEquals(counter.get, 1)
  }

  test("Iterant.dump works for Last") { implicit s =>
    check1 { (el: Int) =>
      val counter = AtomicInt(0)
      val out = Iterant[Task].lastS(el).dump("O", dummyOut(counter))
      out.completeL.runAsync
      s.tick()

      counter.get <-> 2
    }
  }

  test("Iterant.dump works for Halt") { implicit s =>
    val dummy = DummyException("dummy")
    val counter = AtomicInt(0)
    val out = Iterant[Task].haltS(Some(dummy)).dump("O", dummyOut(counter))
    out.completeL.runAsync
    s.tick()

    assertEquals(counter.get, 1)
  }

  test("Iterant.dump doesn't touch Halt") { _ =>
    val dummy = DummyException("dummy")
    val stream: Iterant[Coeval, Int] = Iterant[Coeval].haltS[Int](Some(dummy))
    val state = stream.dump("O", dummyOut(AtomicInt(0)))

    assertEquals(state, stream)
  }

  test("Iterant.dump preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source = Iterant[Coeval].nextCursorS(BatchCursor(1, 2, 3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source.dump("O", dummyOut(AtomicInt(0)))
    stream.earlyStop.value

    assertEquals(effect, 1)
  }

  test("Iterant[Task].dump can handle errors") { implicit s =>
    val dummy = DummyException("dummy")
    val stream = Iterant[Task].raiseError[Int](dummy)

    assertEquals(stream, stream.dump("O", dummyOut(AtomicInt(0))))
  }

  test("Iterant.dump protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix

      stream.dump("O", dummyOut(AtomicInt(0))) <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.dump protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix

      stream.dump("O", dummyOut(AtomicInt(0))) <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.dump protects against user error") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream.onErrorIgnore ++ Iterant[Task].now(1)).dump("O", dummyOutException)

      received <-> Iterant[Task].raiseError(dummy)
    }
  }
}