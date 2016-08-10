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

import monix.execution.internal.Platform

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object TaskGatherUnorderedSuite extends BaseTestSuite {
  test("Task.gatherUnordered should execute in parallel") { implicit s =>
    val seq = Seq(Task(1).delayExecution(2.seconds), Task(2).delayExecution(1.second), Task(3).delayExecution(3.seconds))
    val f = Task.gatherUnordered(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(List(2, 1, 3))))
  }

  test("Task.gatherUnordered should onError if one of the tasks terminates in error") { implicit s =>
    val ex = DummyException("dummy")
    val seq = Seq(
      Task(3).delayExecution(3.seconds),
      Task(2).delayExecution(1.second),
      Task(throw ex).delayExecution(2.seconds),
      Task(3).delayExecution(1.seconds))

    val f = Task.gatherUnordered(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.gatherUnordered should be canceled") { implicit s =>
    val seq = Seq(Task(1).delayExecution(2.seconds), Task(2).delayExecution(1.second), Task(3).delayExecution(3.seconds))
    val f = Task.gatherUnordered(seq).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(2.seconds)
    assertEquals(f.value, None)

    f.cancel()
    s.tick(1.second)
    assertEquals(f.value, None)
  }

  test("Task.gatherUnordered should run over an iterator") { implicit s =>
    val count = 10
    val seq = 0 until count
    val it = seq.iterator.map(x => Task.evalAlways(x + 1))
    val sum = Task.gatherUnordered(it).map(_.sum)

    val result = sum.runAsync; s.tick()
    assertEquals(result.value.get, Success((count+1) * count / 2))
  }

  test("Task.gatherUnordered should be stack-safe on handling many tasks") { implicit s =>
    val count = 10000
    val tasks = (0 until count).map(x => Task.evalAlways(x))
    val sum = Task.gatherUnordered(tasks).map(_.sum)

    val result = sum.runAsync; s.tick()
    assertEquals(result.value.get, Success(count * (count-1) / 2))
  }

  test("Task.gatherUnordered should be stack safe on success") { implicit s =>
    def fold[A,B](ta: Task[ListBuffer[A]], tb: Task[A]): Task[ListBuffer[A]] =
      Task.gatherUnordered(List(ta, tb)).map {
        case a :: b :: Nil =>
          val (accR, valueR) = if (a.isInstanceOf[ListBuffer[_]]) (a,b) else (b,a)
          val acc = accR.asInstanceOf[ListBuffer[A]]
          val value = valueR.asInstanceOf[A]
          acc += value
        case _ =>
          throw new RuntimeException("Oops!")
      }

    def gatherSpecial[A](in: Seq[Task[A]]): Task[List[A]] = {
      val init = Task.evalAlways(ListBuffer.empty[A])
      val r = in.foldLeft(init)(fold)
      r.map(_.result())
    }

    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(n => Task.evalAlways(n))
    var result = Option.empty[Try[Int]]

    gatherSpecial(tasks).map(_.sum).runAsync(
      new Callback[Int] {
        def onSuccess(value: Int): Unit =
          result = Some(Success(value))
        def onError(ex: Throwable): Unit =
          result = Some(Failure(ex))
      })

    s.tick()
    assertEquals(result, Some(Success(count * (count - 1) / 2)))
  }
}
