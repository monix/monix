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

import monix.eval.tracing.{TaskEvent, TaskTrace}
import monix.eval.{BaseTestSuite, Task}

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
object CachedStackTracingSuite extends BaseTestSuite {

  def traced[A](io: Task[A]): Task[TaskTrace] =
    io.flatMap(_ => Task.trace)

  testAsync("captures map frames") { implicit s =>
    val task = Task.pure(0).map(_ + 1).map(_ + 1)

    val test =
      for (r <- traced(task)) yield {
        assertEquals(r.captured, 4)
        assertEquals(
          r.events.collect { case e: TaskEvent.StackTrace => e }.count(_.stackTrace.exists(_.getMethodName == "map")),
          3)
      }

    test.runToFuture
  }

  testAsync("captures bind frames") { implicit s =>
    val task = Task.pure(0).flatMap(a => Task(a + 1)).flatMap(a => Task(a + 1))

    val test =
      for (r <- traced(task)) yield {
        assertEquals(r.captured, 4)
        assertEquals(
          r.events.collect { case e: TaskEvent.StackTrace => e }
            .count(_.stackTrace.exists(_.getMethodName == "flatMap")),
          3
        ) // the extra one is used to capture the trace
      }

    test.runToFuture
  }

  testAsync("captures async frames") { implicit s =>
    val task = Task.async[Int](_(Right(0))).flatMap(a => Task(a + 1)).flatMap(a => Task(a + 1))

    val test =
      for (r <- traced(task)) yield {
        assertEquals(r.captured, 5)
        assertEquals(
          r.events.collect { case e: TaskEvent.StackTrace => e }.count(_.stackTrace.exists(_.getMethodName == "async")),
          1)
      }

    test.runToFuture
  }

  testAsync("captures bracket frames") { implicit s =>
    val task = Task.unit.bracket(_ => Task.pure(10))(_ => Task.unit).flatMap(a => Task(a + 1)).flatMap(a => Task(a + 1))

    val test =
      for (r <- traced(task)) yield {
        assertEquals(r.captured, 6)
        assertEquals(
          r.events.collect { case e: TaskEvent.StackTrace => e }
            .count(_.stackTrace.exists(_.getMethodName == "bracket")),
          1)
      }

    test.runToFuture
  }

  testAsync("captures bracketCase frames") { implicit s =>
    val task =
      Task.unit.bracketCase(_ => Task.pure(10))((_, _) => Task.unit).flatMap(a => Task(a + 1)).flatMap(a => Task(a + 1))

    val test =
      for (r <- traced(task)) yield {
        assertEquals(r.captured, 6)
        assertEquals(
          r.events.collect { case e: TaskEvent.StackTrace => e }
            .count(_.stackTrace.exists(_.getMethodName == "bracketCase")),
          1)
      }

    test.runToFuture
  }
}
