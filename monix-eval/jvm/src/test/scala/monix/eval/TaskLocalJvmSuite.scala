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

object TaskLocalJvmSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global
  implicit val opts = Task.defaultOptions.enableLocalContextPropagation

  testAsync("Local.apply with different schedulers") {
    val ec2: Scheduler = monix.execution.Scheduler.io()
    val test =
      for {
        local <- TaskLocal(0).asyncBoundary(ec2)
        _ <- local.write(800).asyncBoundary(ec)
        v1 <- local.read.asyncBoundary(ec2)
        v2 <- local.read
        _ <- Task.now(assertEquals(v1, v2))
      } yield ()

    test.runAsyncOpt
  }

  testAsync("Local.apply with different schedulers with onExecute") {
    val ec2: Scheduler = monix.execution.Scheduler.io()
    val test =
      for {
        local <- TaskLocal(0)
        _ <- local.write(1000).executeOn(ec2)
        v1 <- local.read.executeOn(ec)
        v2 <- local.read.executeOn(ec2)
        _ <- Task.now(assertEquals(v1, 1000))
        _ <- Task.now(assertEquals(v2, 1000))
      } yield ()

    test.runAsyncOpt
  }
}
