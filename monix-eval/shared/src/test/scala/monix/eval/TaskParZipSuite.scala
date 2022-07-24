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

package monix.eval

import monix.execution.exceptions.DummyException
import concurrent.duration._
import scala.util.{ Failure, Random, Success }

class TaskParZipSuite extends BaseTestSuite {
  fixture.test("Task.parZip2 should work if source finishes first") { implicit s =>
    val f = Task.parZip2(Task(1), Task(2).delayExecution(1.second)).runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1, 2))))
  }

  fixture.test("Task.parZip2 should work if other finishes first") { implicit s =>
    val f = Task.parZip2(Task(1).delayExecution(1.second), Task(2)).runToFuture

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1, 2))))
  }

  fixture.test("Task.parZip2 should cancel both") { implicit s =>
    val f = Task.parZip2(Task(1).delayExecution(1.second), Task(2).delayExecution(2.seconds)).runToFuture

    s.tick()
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  fixture.test("Task.parZip2 should cancel just the source") { implicit s =>
    val f = Task.parZip2(Task(1).delayExecution(1.second), Task(2).delayExecution(2.seconds)).runToFuture

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  fixture.test("Task.parZip2 should cancel just the other") { implicit s =>
    val f = Task.parZip2(Task(1).delayExecution(2.second), Task(2).delayExecution(1.seconds)).runToFuture

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  fixture.test("Task.parZip2 should onError from the source before other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.parZip2(Task[Int](throw ex).delayExecution(1.second), Task(2).delayExecution(2.seconds)).runToFuture

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("Task.parZip2 should onError from the source after other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.parZip2(Task[Int](throw ex).delayExecution(2.second), Task(2).delayExecution(1.seconds)).runToFuture

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("Task.parZip2 should onError from the other after the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.parZip2(Task(1).delayExecution(1.second), Task(throw ex).delayExecution(2.seconds)).runToFuture

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("Task.parZip2 should onError from the other before the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task.parZip2(Task(1).delayExecution(2.second), Task(throw ex).delayExecution(1.seconds)).runToFuture

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("Task.parZip2 works") { implicit s =>
    val f1 = Task.parZip2(Task(1), Task(2)).runToFuture
    val f2 = Task.parMap2(Task(1), Task(2))((a, b) => (a, b)).runToFuture
    s.tick()
    assertEquals(f1.value.get, f2.value.get)
  }

  fixture.test("Task.map2 works") { implicit s =>
    val fa = Task.map2(Task(1), Task(2))(_ + _).runToFuture
    s.tick()
    assertEquals(fa.value, Some(Success(3)))
  }

  fixture.test("Task#map2 should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(1.second)
    val task = Task.map2(ta, tb)((_, _) => (throw dummy): Int)

    val f = task.runToFuture
    s.tick(2.seconds)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("Task.map2 runs effects in strict sequence") { implicit s =>
    var effect1 = 0
    var effect2 = 0
    val ta = Task.evalAsync { effect1 += 1 }.delayExecution(1.millisecond)
    val tb = Task.evalAsync { effect2 += 1 }.delayExecution(1.millisecond)
    Task.map2(ta, tb)((_, _) => ()).runToFuture
    s.tick()
    assertEquals(effect1, 0)
    assertEquals(effect2, 0)
    s.tick(1.millisecond)
    assertEquals(effect1, 1)
    assertEquals(effect2, 0)
    s.tick(1.millisecond)
    assertEquals(effect1, 1)
    assertEquals(effect2, 1)
  }

  fixture.test("Task.parMap2 works") { implicit s =>
    val fa = Task.parMap2(Task(1), Task(2))(_ + _).runToFuture
    s.tick()
    assertEquals(fa.value, Some(Success(3)))
  }

  fixture.test("Task#parMap2 should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(1.second)
    val task = Task.parMap2(ta, tb)((_, _) => (throw dummy): Int)

    val f = task.runToFuture
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("Task.parZip23 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parZip3(n(1), n(2), n(3))
    val r = t.runToFuture
    s.tick(3.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3))))
  }

  fixture.test("Task#map3 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(n.seconds)
    val t = Task.map3(n(1), n(2), n(3))((_, _, _))
    val r = t.runToFuture
    s.tick(3.seconds)
    assertEquals(r.value, None)
    s.tick(3.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3))))
  }

  fixture.test("Task#parMap3 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parMap3(n(1), n(2), n(3))((_, _, _))
    val r = t.runToFuture
    s.tick(3.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3))))
  }

  fixture.test("Task.parZip24 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parZip4(n(1), n(2), n(3), n(4))
    val r = t.runToFuture
    s.tick(4.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4))))
  }

  fixture.test("Task#map4 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(n.seconds)
    val t = Task.map4(n(1), n(2), n(3), n(4))((_, _, _, _))
    val r = t.runToFuture
    s.tick(6.seconds)
    assertEquals(r.value, None)
    s.tick(4.second)
    assertEquals(r.value, Some(Success((1, 2, 3, 4))))
  }

  fixture.test("Task#parMap4 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parMap4(n(1), n(2), n(3), n(4))((_, _, _, _))
    val r = t.runToFuture
    s.tick(4.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4))))
  }

  fixture.test("Task.parZip25 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parZip5(n(1), n(2), n(3), n(4), n(5))
    val r = t.runToFuture
    s.tick(5.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5))))
  }

  fixture.test("Task#map5 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(n.seconds)
    val t = Task.map5(n(1), n(2), n(3), n(4), n(5))((_, _, _, _, _))
    val r = t.runToFuture
    s.tick(10.seconds)
    assertEquals(r.value, None)
    s.tick(5.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5))))
  }

  fixture.test("Task#parMap5 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parMap5(n(1), n(2), n(3), n(4), n(5))((_, _, _, _, _))
    val r = t.runToFuture
    s.tick(5.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5))))
  }

  fixture.test("Task.parZip26 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parZip6(n(1), n(2), n(3), n(4), n(5), n(6))
    val r = t.runToFuture
    s.tick(6.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5, 6))))
  }

  fixture.test("Task#map6 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(n.seconds)
    val t = Task.map6(n(1), n(2), n(3), n(4), n(5), n(6))((_, _, _, _, _, _))
    val r = t.runToFuture
    s.tick(20.seconds)
    assertEquals(r.value, None)
    s.tick(1.second)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5, 6))))
  }

  fixture.test("Task#parMap6 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.parMap6(n(1), n(2), n(3), n(4), n(5), n(6))((_, _, _, _, _, _))
    val r = t.runToFuture
    s.tick(6.seconds)
    assertEquals(r.value, Some(Success((1, 2, 3, 4, 5, 6))))
  }
}
