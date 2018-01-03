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

import monix.reactive.{BaseConcurrencySuite, Consumer, Observable}

object LoadBalanceConsumerConcurrencySuite extends BaseConcurrencySuite {
  test("aggregate all events") { implicit s =>
    check2 { (source: Observable[Int], rndInt: Int) =>
      // Parallelism value will be between 1 and 16
      val parallelism = {
        val x = math.abs(rndInt)
        val pos = if (x < 0) Int.MaxValue else x
        (pos % 15) + 1
      }

      val consumer = Consumer.loadBalance(parallelism,
        Consumer.foldLeft[Long,Int](0L)(_+_))

      val task1 = source.foldLeftF(0L)(_+_).firstL
      val task2 = source.consumeWith(consumer).map(_.sum)
      task1 <-> task2
    }
  }

  test("aggregate all events with subscribers that stop") { implicit s =>
    check2 { (source: Observable[Int], rndInt: Int) =>
      // Parallelism value will be between 1 and 16
      val parallelism = {
        val x = math.abs(rndInt)
        val pos = if (x < 0) Int.MaxValue else x
        (pos % 15) + 1
      }

      val fold = Consumer.foldLeft[Long,Int](0L)(_+_)
      val justOne = Consumer.headOption[Int].map(_.getOrElse(0).toLong)
      val allConsumers = for (i <- 0 until parallelism) yield
        if (i % 2 == 0) fold else justOne

      val consumer = Consumer.loadBalance(allConsumers:_*)
      val task1 = source.foldLeftF(0L)(_+_).firstL
      val task2 = source.consumeWith(consumer).map(_.sum)
      task1 <-> task2
    }
  }
}
