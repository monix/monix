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

object TaskLocalSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global
  implicit val opts = Task.defaultOptions.enableLocalContextPropagation

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

    test.runAsyncOpt
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
}
