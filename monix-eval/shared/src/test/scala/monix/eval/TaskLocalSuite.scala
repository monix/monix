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

package monix.eval

import minitest.SimpleTestSuite
import monix.execution.Scheduler
import monix.execution.exceptions.DummyException
import monix.execution.misc.Local

object TaskLocalSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global
  implicit val opts = Task.defaultOptions.enableLocalContextPropagation

  testAsync("TaskLocal.apply") {
    val test =
      for {
        local <- TaskLocal(0)
        v1 <- local.read
        _ <- Task.now(assertEquals(v1, 0))
        _ <- local.write(100)
        _ <- Task.shift
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
        _ <- local.clear
        _ <- Task.shift
        v3 <- local.read
        _ <- Task.now(assertEquals(v3, 0))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal.wrap") {
    val local = Local(0)
    val test =
      for {
        local <- TaskLocal.wrap(Task(local))
        v1 <- local.read
        _ <- Task.now(assertEquals(v1, 0))
        _ <- local.write(100)
        _ <- Task.shift
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
        _ <- local.clear
        _ <- Task.shift
        v3 <- local.read
        _ <- Task.now(assertEquals(v3, 0))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal.defaultLazy") {
    var i = 0

    val test =
      for {
        local <- TaskLocal.lazyDefault(Coeval { i += 1; i })
        v1 <- local.read
        _ <- Task.now(assertEquals(v1, 1))
        _ <- local.write(100)
        _ <- Task.shift
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
        _ <- local.clear
        _ <- Task.shift
        v3 <- local.read
        _ <- Task.now(assertEquals(v3, 2))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal!.bind") {
    val test =
      for {
        local <- TaskLocal(0)
        _ <- local.write(100)
        _ <- Task.shift
        v1 <- local.bind(200)(local.read.map(_ * 2))
        _ <- Task.now(assertEquals(v1, 400))
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal!.bindL") {
    val test =
      for {
        local <- TaskLocal(0)
        _ <- local.write(100)
        _ <- Task.shift
        v1 <- local.bindL(Task.eval(200))(local.read.map(_ * 2))
        _ <- Task.now(assertEquals(v1, 400))
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal!.bindClear") {
    val test =
      for {
        local <- TaskLocal(200)
        _ <- local.write(100)
        _ <- Task.shift
        v1 <- local.bindClear(local.read.map(_ * 2))
        _ <- Task.now(assertEquals(v1, 400))
        v2 <- local.read
        _ <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal canceled") {
    import scala.concurrent.duration._

    val test: Task[Unit] = for {
      local <- TaskLocal[String]("Good")
      forked <- Task.sleep(1.second).start
      _ <- local.bind("Bad!")(forked.cancel).start
      _ <- Task.sleep(1.second)
      s <- local.read
      _ <- Task.now(assertEquals(s, "Good"))
    } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocal!.local") {
    val test =
      for {
        taskLocal <- TaskLocal(200)
        local <- taskLocal.local
        v1 <- taskLocal.read
        _ <- Task.now(assertEquals(local.get, v1))
        _ <- taskLocal.write(100)
        _ <- Task.now(assertEquals(local.get, 100))
        _ <- Task.now(local.update(200))
        v2 <- taskLocal.read
        _ <- Task.now(assertEquals(v2, 200))
        _ <- Task.shift
        v3 <- taskLocal.bindClear(Task.now(local.get * 2))
        _ <- Task.now(assertEquals(v3, 400))
        v4 <- taskLocal.read
        _ <- Task.now(assertEquals(v4, local.get))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("TaskLocals get restored in Task.create on error") {
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, cb) =>
      ec.execute(new Runnable {
        def run() = cb.onError(dummy)
      })
    }

    val t = for {
      local <- TaskLocal(0)
      _ <- local.write(10)
      i <- task.onErrorRecover { case `dummy` => 10 }
      l <- local.read
      _ <- Task.eval(assertEquals(i, 10))
      _ <- Task.eval(assertEquals(l, 10))
    } yield ()

    t.runAsyncOpt
  }
}
