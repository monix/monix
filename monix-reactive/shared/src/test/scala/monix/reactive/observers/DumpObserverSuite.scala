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

package monix.reactive.observers

import java.io.{OutputStream, PrintStream}

import minitest.SimpleTestSuite
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observer
import monix.execution.exceptions.DummyException
import monix.execution.atomic.AtomicInt

object DumpObserverSuite extends SimpleTestSuite {
  def dummyOut(count: AtomicInt = null) = {
    val out = new OutputStream { def write(b: Int) = () }
    new PrintStream(out) {
      override def println(x: String) = {
        super.println(x)
        if (count != null) {
          val c = count.incrementAndGet()
          if (c == 0) throw new DummyException("dummy")
        }
      }
    }
  }

  test("Observer.dump works") {
    val counter = AtomicInt(0)
    val out = Observer.dump[Int]("O", dummyOut(counter))

    assertEquals(out.onNext(1), Continue)
    assertEquals(out.onNext(2), Continue)
    out.onComplete()
    out.onError(DummyException("dummy"))

    assertEquals(counter.get, 4)
  }

  test("Subscriber.dump works") {
    implicit val s = TestScheduler()
    val counter = AtomicInt(0)
    val out = Subscriber.dump[Int]("O", dummyOut(counter))

    assertEquals(out.onNext(1), Continue)
    assertEquals(out.onNext(2), Continue)
    out.onComplete()
    out.onError(DummyException("dummy"))

    assertEquals(counter.get, 4)
  }
}
