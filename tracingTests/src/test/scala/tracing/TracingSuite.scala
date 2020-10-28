/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package tracing

import monix.eval.tracing.TaskTrace
import monix.eval.{BaseTestSuite, Task}

import scala.util.control.NoStackTrace
import cats.syntax.all._
import monix.execution.Scheduler

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
object TracingSuite extends BaseTestSuite {

  implicit val s: Scheduler = Scheduler.global

  def traced[A](io: Task[A]): Task[TaskTrace] =
    io.flatMap(_ => Task.trace)

  testAsync("traces are preserved across asynchronous boundaries") { _ =>
    val task = for {
      a <- Task.pure(1)
      _ <- Task.shift
      b <- Task.pure(1)
    } yield a + b

    val test =
      for (r <- traced(task).runToFuture) yield {
        assertEquals(r.captured, 4)
      }

    test
  }

  testAsync("enhanced exceptions are not augmented more than once") { _ =>
    val task = for {
      _ <- Task.pure(1)
      _ <- Task.pure(2)
      _ <- Task.pure(3)
      _ <- Task.shift
      _ <- Task.pure(1)
      _ <- Task.pure(2)
      _ <- Task.pure(3)
      e1 <- Task.raiseError(new Throwable("Encountered an error")).attempt
      e2 <- Task.pure(e1).rethrow.attempt
    } yield (e1, e2)

    for (r <- task.runToFuture) yield {
      val (e1, e2) = r
      assertEquals(e1.swap.toOption.get.getStackTrace.length, e2.swap.toOption.get.getStackTrace.length)
    }
  }

  testAsync("enhanced exceptions is not applied when stack trace is empty") { _ =>
    val task = for {
      e1 <- Task.raiseError(new EmptyException).attempt
    } yield e1

    for (r <- task.runToFuture) yield {
      assertEquals(r.swap.toOption.get.getStackTrace.length, 0)
    }

  }

  class EmptyException extends NoStackTrace
}
