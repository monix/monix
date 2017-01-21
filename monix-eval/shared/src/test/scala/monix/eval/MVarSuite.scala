/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.execution.atomic.PaddingStrategy.LeftRight128
import scala.util.Success

object MVarSuite extends BaseTestSuite {
  test("empty; put; take; put; take") { implicit s =>
    val av = MVar.empty[Int]

    val task = for {
      _ <- av.put(10)
      r1 <- av.take
      _ <- av.put(20)
      r2 <- av.take
    } yield List(r1,r2)

    assertEquals(task.runSyncMaybe, Right(List(10,20)))
  }

  test("empty; take; put; take; put") { implicit s =>
    val av = MVar.empty[Int]

    val task = for {
      r1 <- Task.mapBoth(av.take, av.put(10))((r,u) => r)
      r2 <- Task.mapBoth(av.take, av.put(20))((r,u) => r)
    } yield List(r1,r2)

    assertEquals(task.runSyncMaybe, Right(List(10,20)))
  }

  test("empty; put; put; put; take; take; take") { implicit s =>
    val av = MVar.empty[Int]

    val take3 = Task.zip3(av.take, av.take, av.take)
    val put3 = Task.zip3(av.put(10), av.put(20), av.put(30))

    val task =
      Task.mapBoth(put3,take3) { case (_, (r1,r2,r3)) => List(r1,r2,r3) }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(List(10,20,30))))
  }

  test("empty; take; take; take; put; put; put") { implicit s =>
    val av = MVar.empty[Int]

    val take3 = Task.zip3(av.take, av.take, av.take)
    val put3 = Task.zip3(av.put(10), av.put(20), av.put(30))

    val task =
      Task.mapBoth(take3, put3) { case ((r1,r2,r3), _) => List(r1,r2,r3) }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(List(10,20,30))))
  }

  test("initial; take; put; take") { implicit s =>
    val av = MVar(10)
    val task = for {
      r1 <- av.take
      _ <- av.put(20)
      r2 <- av.take
    } yield List(r1,r2)

    assertEquals(task.runSyncMaybe, Right(List(10,20)))
  }

  test("withPadding; put; take; put; take") { implicit s =>
    val av = MVar.withPadding[Int](LeftRight128)
    val task = for {
      _ <- av.put(10)
      r1 <- av.take
      _ <- av.put(20)
      r2 <- av.take
    } yield List(r1,r2)

    assertEquals(task.runSyncMaybe, Right(List(10,20)))
  }

  test("withPadding(initial); put; take; put; take") { implicit s =>
    val av = MVar.withPadding[Int](10, LeftRight128)
    val task = for {
      r1 <- av.take
      _ <- av.put(20)
      r2 <- av.take
    } yield List(r1,r2)

    assertEquals(task.runSyncMaybe, Right(List(10,20)))
  }

  test("initial; read; take") { implicit s =>
    val av = MVar(10)
    val task = for {
      read <- av.read
      take <- av.take
    } yield read + take

    assertEquals(task.runSyncMaybe, Right(20))
  }

  test("empty; read; put") { implicit s =>
    val av = MVar.empty[Int]
    val task = Task.mapBoth(av.read, av.put(10))((r,_) => r)
    assertEquals(task.runSyncMaybe, Right(10))
  }

  test("put(null) throws NullPointerException") { implicit s =>
    val av = MVar.empty[String]
    val task = av.put(null)

    intercept[NullPointerException] {
      task.runSyncMaybe
    }
  }
}
