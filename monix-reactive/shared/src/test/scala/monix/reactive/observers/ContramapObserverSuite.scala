/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.exceptions.DummyException
import monix.reactive.{ BaseTestSuite, Observer }

object ContramapObserverSuite extends BaseTestSuite {
  test("Observer.contramap equivalence with plain Observer") { implicit s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val plainObserver: Observer[Int] = new Observer[Int] {
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }
      }

      val contramapObserver: Observer[Long] =
        plainObserver.contramap(_.toInt)

      val plainAck = plainObserver.onNextAll(xs)
      val contraAck = contramapObserver.onNextAll(xs.map(_.toLong))

      s.tick()
      plainAck.syncTryFlatten(s) == Continue &&
      contraAck.syncTryFlatten(s) == Continue &&
      sum == xs.sum * 2
    }
  }

  test("Observer.contramap protects against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val out: Observer[Long] = (Observer.empty[Int]: Observer[Int])
      .contramap(_ => throw dummy)

    s.tick()
    assertEquals(out.onNext(1), Stop)
  }

  test("Observer.contramap works") { implicit s =>
    var isDone = 0
    val intObserver: Observer[Int] = new Observer[Int] {
      def onError(ex: Throwable): Unit = isDone += 1
      def onComplete(): Unit = isDone += 1
      def onNext(elem: Int) = Continue
    }

    val doubleObserver: Observer[Double] = intObserver.contramap(_.toInt)

    assertEquals(doubleObserver.onNext(1.0), Continue)
    doubleObserver.onComplete()
    assertEquals(isDone, 1)
    doubleObserver.onError(DummyException("dummy"))
    assertEquals(isDone, 1)
    assertEquals(doubleObserver.onNext(2.0), Stop)
  }
}
