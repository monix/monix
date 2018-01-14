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

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.BaseTestSuite
import scala.concurrent.Future

object SubscriberFeedSuite extends BaseTestSuite {
  test("feed synchronous iterable") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber.Sync[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }
      }

      val ack = downstream.feed(xs)
      s.tick()
      ack.syncTryFlatten(s) == Continue &&
        sum == xs.sum
    }
  }

  test("feed asynchronous iterable") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = Future {
          sum += elem
          Continue
        }
      }

      val ack = downstream.onNextAll(xs)
      s.tick()
      ack.syncTryFlatten(s) == Continue &&
        sum == xs.sum
    }
  }

  test("feed synchronous iterator") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber.Sync[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }
      }

      val ack = downstream.feed(xs.toIterator)
      s.tick()
      ack.syncTryFlatten(s) == Continue &&
        sum == xs.sum
    }
  }

  test("feed asynchronous iterator") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = Future {
          sum += elem
          Continue
        }
      }

      val ack = downstream.feed(xs.toIterator)
      s.tick()
      ack.syncTryFlatten(s) == Continue &&
        sum == xs.sum
    }
  }

  test("stop observable synchronously") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber.Sync[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = {
          sum += elem
          Stop
        }
      }

      val ack = downstream.onNextAll(xs); s.tick()
      xs.isEmpty || (ack.syncTryFlatten(s) == Stop && sum == xs.head)
    }
  }

  test("stop observable asynchronously") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = Future {
          sum += elem
          Stop
        }
      }

      val ack = downstream.onNextAll(xs); s.tick()
      xs.isEmpty || (ack.syncTryFlatten(s) == Stop && sum == xs.head)
    }
  }

  test("should be cancelable") { s =>
    check1 { (xs: List[Int]) =>
      var sum = 0
      val downstream = new Subscriber[Int] {
        implicit val scheduler = s
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = sum += 100
        def onNext(elem: Int) = Future {
          sum += elem
          Continue
        }
      }

      val c = BooleanCancelable()
      val ack = downstream.feed(c, xs)

      c.cancel(); s.tick()
      (xs.length <= 1) || ack.syncTryFlatten(s) == Stop
    }
  }
}
