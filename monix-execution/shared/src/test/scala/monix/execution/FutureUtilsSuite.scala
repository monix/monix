/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution

import minitest.TestSuite
import monix.execution.FutureUtils.extensions._
import monix.execution.schedulers.TestScheduler

import scala.concurrent.duration._
import scala.concurrent.{ Future, TimeoutException }
import scala.util.{ Failure, Success, Try }

object FutureUtilsSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("delayedResult") { implicit s =>
    val f = Future.delayedResult(100.millis)("TICK")

    s.tick(50.millis)
    assert(!f.isCompleted)

    s.tick(100.millis)
    assert(f.value.get.get == "TICK")
  }

  test("timeout should succeed") { implicit s =>
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.timeout(300.millis)

    s.tick(10.seconds)
    assertEquals(t.value, Some(Success("Hello world!")))
  }

  test("timeout should fail") { implicit s =>
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.timeout(30.millis)

    s.tick(10.seconds)
    intercept[TimeoutException] { t.value.get.get: Unit }
    ()
  }

  test("timeoutTo should work") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.timeoutTo(300.millis, Future.failed(dummy))

    s.tick(10.seconds)
    assertEquals(t.value, Some(Success("Hello world!")))
  }

  test("timeoutTo should fail") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.timeoutTo(30.millis, Future.failed(dummy))

    s.tick(10.seconds)
    assertEquals(t.value, Some(Failure(dummy)))
  }

  test("timeoutTo should not evaluate fallback when future finished earlier than timeout") { implicit s =>
    @volatile var called = false
    val expected = 15
    val f = Future
      .delayedResult(50.millis)(expected)
      .timeoutTo(
        100.millis, {
          called = true
          Future.failed(new RuntimeException)
        }
      )

    s.tick(1.second)
    assertEquals(f.value, Some(Success(expected)))
    assertEquals(called, false)
  }

  test("materialize") { implicit s =>
    val f1 = Future(1).materialize; s.tick()
    assertEquals(f1.value, Some(Success(Success(1))))

    val dummy = new RuntimeException("dummy")
    val f2 = Future[Int](throw dummy).materialize; s.tick()
    assertEquals(f2.value, Some(Success(Failure(dummy))))
  }

  test("dematerialize") { implicit s =>
    val f1 = Future(Success(1)).dematerialize; s.tick()
    assertEquals(f1.value, Some(Success(1)))

    val dummy = new RuntimeException("dummy")
    val f2 = Future(Failure(dummy)).dematerialize; s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))

    val f3 = Future[Try[Int]](throw dummy).dematerialize; s.tick()
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  test("transform backport") { implicit s =>
    val source1 = Future(1)
    val result1 = FutureUtils.transform(source1, (x: Try[Int]) => x.map(_.toString))
    s.tick(); assertEquals(result1.value, Some(Success("1")))

    val dummy = new RuntimeException("dummy")
    val source2 = Future[Int](throw dummy)
    val result2 = FutureUtils.transform(source2, (_: Try[Int]) => Success(2))
    s.tick(); assertEquals(result2.value, Some(Success(2)))
  }

  test("transformWith backport") { implicit s =>
    val source1 = Future(1)
    val result1 = FutureUtils.transformWith(source1, (x: Try[Int]) => Future(x.map(_.toString).get))
    s.tick(); assertEquals(result1.value, Some(Success("1")))

    val dummy = new RuntimeException("dummy")
    val source2 = Future[Int](throw dummy)
    val result2 = FutureUtils.transformWith(source2, (_: Try[Int]) => Future.successful(2))
    s.tick(); assertEquals(result2.value, Some(Success(2)))

    val source3 = Future(1)
    val result3 = FutureUtils.transformWith(source3, (x: Try[Int]) => Future(x.map(_.toString).get))
    s.tick(); assertEquals(result3.value, Some(Success("1")))

    val source4 = Future[Int](throw dummy)
    val result4 = FutureUtils.transformWith(source4, (_: Try[Int]) => Future.successful(2))
    s.tick(); assertEquals(result4.value, Some(Success(2)))
  }
}
