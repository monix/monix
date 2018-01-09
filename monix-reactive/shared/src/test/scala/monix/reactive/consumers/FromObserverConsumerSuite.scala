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

package monix.reactive.consumers

import cats.laws._
import cats.laws.discipline._

import monix.eval.{Callback, Task}
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Consumer, Observable, Observer}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object FromObserverConsumerSuite extends BaseTestSuite {
  test("convert an observer into a consumer") { implicit s =>
    check1 { (source: Observable[Int]) =>
      val lh = source.sumL
      val rh = Task.create[Int] { (s, cb) =>
        implicit val scheduler = s
        var sum = 0

        val consumer = Consumer.fromObserver(_ =>
          new Observer[Int] {
            def onError(ex: Throwable): Unit = throw ex
            def onComplete(): Unit = ()
            def onNext(elem: Int) = {
              sum += elem; Continue
            }
          })

        val onFinish = Promise[Unit]()
        val (out, _) = consumer.createSubscriber(Callback.fromPromise(onFinish), s)
        onFinish.future.onComplete {
          case Success(()) => cb.onSuccess(sum)
          case Failure(ex) => cb.onError(ex)
        }

        source.unsafeSubscribeFn(out)
      }

      lh <-> rh
    }
  }

  test("report onError") { implicit s =>
    check1 { (source: Observable[Int]) =>
      val ex = DummyException("dummy")
      val lh = Task.raiseError[Unit](ex)

      val rh = Task.create[Unit] { (s, cb) =>
        implicit val scheduler = s

        val consumer = Consumer.fromObserver(_ =>
          new Observer[Int] {
            def onError(ex: Throwable): Unit = throw ex
            def onComplete(): Unit = ()
            def onNext(elem: Int) = Continue
          })

        val (out, _) = consumer.createSubscriber(cb, s)
        source.endWithError(ex).unsafeSubscribeFn(out)
      }

      lh <-> rh
    }
  }
}
