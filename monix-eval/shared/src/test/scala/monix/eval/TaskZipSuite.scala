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

package monix.eval

import concurrent.duration._
import scala.util.{Failure, Random, Success}

object TaskZipSuite extends BaseTestSuite{
  test("Task#zip should work if source finishes first") { implicit s =>
    val f = Task(1).zip(Task(2).delayExecution(1.second)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zip should work if other finishes first") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zip should cancel both") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should cancel just the source") { implicit s =>
    val f = Task(1).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should cancel just the other") { implicit s =>
    val f = Task(1).delayExecution(2.second).zip(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zip should onError from the source before other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(1.second).zip(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the source after other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(2.second).zip(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the other after the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(1.second).zip(Task(throw ex).delayExecution(2.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zip should onError from the other before the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(2.second).zip(Task(throw ex).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zipWith works") { implicit s =>
    val f1 = Task(1).zip(Task(2)).runAsync
    val f2 = Task(1).zipWith(Task(2))((a,b) => (a,b)).runAsync
    s.tick()
    assertEquals(f1.value.get, f2.value.get)
  }

  test("Task#zipWith2 should protect against user code") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(1.second)
    val task = Task.zipWith2(ta, tb)((a,b) => (throw dummy) : Int)

    val f = task.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task#zip3 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zip3(n(1),n(2),n(3))
    val r = t.runAsync
    s.tick(3.seconds)
    assertEquals(r.value, Some(Success((1,2,3))))
  }

  test("Task#zipWith3 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zipWith3(n(1),n(2),n(3))((_,_,_))
    val r = t.runAsync
    s.tick(3.seconds)
    assertEquals(r.value, Some(Success((1,2,3))))
  }

  test("Task#zip4 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zip4(n(1),n(2),n(3),n(4))
    val r = t.runAsync
    s.tick(4.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4))))
  }

  test("Task#zipWith4 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zipWith4(n(1),n(2),n(3),n(4))((_,_,_,_))
    val r = t.runAsync
    s.tick(4.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4))))
  }

  test("Task#zip5 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zip5(n(1),n(2),n(3),n(4),n(5))
    val r = t.runAsync
    s.tick(5.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4,5))))
  }

  test("Task#zipWith5 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zipWith5(n(1),n(2),n(3),n(4),n(5))((_,_,_,_,_))
    val r = t.runAsync
    s.tick(5.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4,5))))
  }

  test("Task#zip6 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zip6(n(1),n(2),n(3),n(4),n(5),n(6))
    val r = t.runAsync
    s.tick(6.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4,5,6))))
  }

  test("Task#zipWith6 works") { implicit s =>
    def n(n: Int) = Task.now(n).delayExecution(Random.nextInt(n).seconds)
    val t = Task.zipWith6(n(1),n(2),n(3),n(4),n(5),n(6))((_,_,_,_,_,_))
    val r = t.runAsync
    s.tick(6.seconds)
    assertEquals(r.value, Some(Success((1,2,3,4,5,6))))
  }
}
