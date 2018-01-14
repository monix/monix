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

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.reactive.{BaseTestSuite, Consumer, Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure

object TransformInputConsumerSuite extends BaseTestSuite {
  test("Consumer#transformInput works for sync transformations") { implicit s =>
    check1 { (random: Observable[Int]) =>
      val source = random.map(Math.floorMod(_, 10))
      val consumer = Consumer.foldLeft[Long, Long](0L)(_ + _)
      val transformed = consumer.transformInput[Int](_.map(_ + 100))
      source.consumeWith(transformed) <-> source.foldLeftL(0L)(_ + _ + 100)
    }
  }

  test("Consumer#transformInput works for async transformations") { implicit s =>
    check1 { (random: Observable[Int]) =>
      val source = random.map(Math.floorMod(_, 10))
      val consumer = Consumer.foldLeft[Long, Long](0L)(_ + _)
      val transformed = consumer.transformInput[Int](_.mapFuture(x => Future(x + 100)))
      source.consumeWith(transformed) <-> source.foldLeftL(0L)(_ + _ + 100)
    }
  }

  test("Consumer#transformInput protects against user code") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable(1)
      .consumeWith(Consumer.foldLeft[Long,Long](0L)(_+_).transformInput[Int](_ => throw ex))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Consumer#transformInput propagates cancelable assignment") { implicit s =>
    val sum = Atomic(0L)

    val sumEvens = Consumer.create[Int, Long] { (_, cancelable, callback) =>
      new Observer[Int] {
        override def onNext(elem: Int): Future[Ack] = {
          if (elem % 2 != 0) cancelable.cancel()
          sum.increment(elem)
          Continue
        }

        override def onError(ex: Throwable): Unit =
          callback.onError(ex)

        override def onComplete(): Unit =
          callback.onSuccess(sum.get)
      }
    }

    val transformed = sumEvens.transformInput[Int](_.map(_ + 1000))
    val obs = Observable(1, 2, 3).delayOnNext(1.second)

    val result = obs.consumeWith(transformed).runAsync
    assertEquals(result.value, None)

    s.tick()
    assert(s.state.tasks.nonEmpty, "s.state.tasks.nonEmpty")

    s.tick(1.second)
    assert(s.state.tasks.isEmpty, "s.state.tasks.isEmpty")

    assertEquals(sum.get, 1001)
    assertEquals(result.value, None)
  }
}
