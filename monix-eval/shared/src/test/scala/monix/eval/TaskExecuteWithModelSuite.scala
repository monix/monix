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

import monix.execution.ExecutionModel
import monix.execution.ExecutionModel.{ AlwaysAsyncExecution, SynchronousExecution }
import scala.util.Success

object TaskExecuteWithModelSuite extends BaseTestSuite {
  def readModel: Task[ExecutionModel] =
    Task.deferAction(s => Task.now(s.properties.getWithDefault[ExecutionModel](ExecutionModel.Default)))

  test("executeWithModel works") { implicit sc =>
    assertEquals(sc.properties.getWithDefault[ExecutionModel](ExecutionModel.Default), ExecutionModel.Default)

    val f1 = readModel.executeWithModel(SynchronousExecution).runToFuture
    assertEquals(f1.value, Some(Success(SynchronousExecution)))

    val f2 = readModel.executeWithModel(AlwaysAsyncExecution).runToFuture
    sc.tick()
    assertEquals(f2.value, Some(Success(AlwaysAsyncExecution)))
  }

  testAsync("local.write.executeWithModel(AlwaysAsyncExecution) works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeWithModel(AlwaysAsyncExecution)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }

  testAsync("local.write.executeWithModel(SynchronousExecution) works") { _ =>
    import monix.execution.Scheduler.Implicits.global
    implicit val opts = Task.defaultOptions.enableLocalContextPropagation

    val task = for {
      l <- TaskLocal(10)
      _ <- l.write(100).executeWithModel(SynchronousExecution)
      _ <- Task.shift
      v <- l.read
    } yield v

    for (v <- task.runToFutureOpt) yield {
      assertEquals(v, 100)
    }
  }

  test("executeWithModel is stack safe in flatMap loops") { implicit sc =>
    def loop(n: Int, acc: Long): Task[Long] =
      Task.unit.executeWithModel(SynchronousExecution).flatMap { _ =>
        if (n > 0)
          loop(n - 1, acc + 1)
        else
          Task.now(acc)
      }

    val f = loop(10000, 0).runToFuture; sc.tick()
    assertEquals(f.value, Some(Success(10000)))
  }
}
