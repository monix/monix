/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import monix.eval.Task
import monix.reactive.exceptions.DummyException
import monix.reactive.{BaseLawsTestSuite, Consumer, Observable}
import scala.util.Failure

object MapAsyncConsumerSuite extends BaseLawsTestSuite {
  test("consumer.mapAsync equivalence with task.map") { implicit s =>
    check1 { (obs: Observable[Int]) =>
      val consumer = Consumer.foldLeft[Long,Int](0L)(_ + _)
      val t1 = obs.runWith(consumer.mapAsync(x => Task(x + 100)))
      val t2 = obs.runWith(consumer).map(_ + 100)
      t1 === t2
    }
  }

  test("consumer.mapAsync streams error") { implicit s =>
    check2 { (obs: Observable[Int], ex: Throwable) =>
      val withError = obs.endWithError(ex)
      val consumer = Consumer.foldLeft[Long,Int](0L)(_ + _)

      val t1 = withError.runWith(consumer.mapAsync(x => Task(x + 100)))
      val t2 = withError.runWith(consumer).map(_+100)
      t1 === t2
    }
  }

  test("consumer.mapAsync handles task errors") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable(1)
      .runWith(Consumer.head[Int].mapAsync(_ => Task(throw ex)))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("consumer.mapAsync protects against user code") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable(1)
      .runWith(Consumer.head[Int].mapAsync(_ => throw ex))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("consumer.mapAsync(sync) equivalence with task.map") { implicit s =>
    check1 { (obs: Observable[Int]) =>
      val consumer = Consumer.foldLeft[Long,Int](0L)(_ + _)
      val t1 = obs.runWith(consumer.mapAsync(x => Task.evalAlways(x + 100)))
      val t2 = obs.runWith(consumer).map(_ + 100)
      t1 === t2
    }
  }

  test("consumer.mapAsync(sync) streams error") { implicit s =>
    check2 { (obs: Observable[Int], ex: Throwable) =>
      val withError = obs.endWithError(ex)
      val consumer = Consumer.foldLeft[Long,Int](0L)(_ + _)

      val t1 = withError.runWith(consumer.mapAsync(x => Task.evalAlways(x + 100)))
      val t2 = withError.runWith(consumer).map(_+100)
      t1 === t2
    }
  }

  test("consumer.mapAsync(sync) protects against user code") { implicit s =>
    val ex = DummyException("dummy")
    val f = Observable(1)
      .runWith(Consumer.head[Int].mapAsync(_ => Task.evalAlways(throw ex)))
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}