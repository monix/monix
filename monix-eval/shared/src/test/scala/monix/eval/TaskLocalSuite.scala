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
import monix.execution.schedulers.TracingScheduler

object TaskLocalSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.traced
  val ec2: Scheduler = TracingScheduler(Scheduler.trampoline())

  testAsync("Local.apply") {
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

    test.runAsync
  }

  testAsync("Local.defaultLazy") {
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

    test.runAsync
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

    test.runAsync
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

    test.runAsync
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

    test.runAsync
  }

  testAsync("Local canceled") {
    import scala.concurrent.duration._

    val test: Task[Unit] = for {
      local <- TaskLocal[String]("Good")
      forked <- Task.sleep(1.second).fork
      _ <- local.bind("Bad!")(forked.cancel).fork
      _ <- Task.sleep(1.second)
      s <- local.read
      _ <- Task.now(assertEquals(s, "Good"))
    } yield ()
    
    test.runAsync
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

    test.runAsync
  }

  testAsync("TaskLocal.apply with different schedulers") {
    val test =
      for {
        local <- TaskLocal(0).asyncBoundary(ec2)
        _ <- local.write(800).asyncBoundary(ec)
        v1 <- local.read.asyncBoundary(ec2)
        v2 <- local.read
        _ <- Task.now(assertEquals(v1, v2))
      } yield ()

    test.runAsync
  }

  testAsync("TaskLocal.apply with different schedulers with onExecute") {
    val test =
      for {
        local <- TaskLocal(0)
        _ <- local.write(1000).executeOn(ec2)
        v1 <- local.read.executeOn(ec)
        v2 <- local.read.executeOn(ec2)
        _ <- Task.now(assertEquals(v1, 1000))
        _ <- Task.now(assertEquals(v2, 1000))
      } yield ()

    test.runAsync
  }
}
