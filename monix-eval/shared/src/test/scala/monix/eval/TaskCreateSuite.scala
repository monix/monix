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

import monix.execution.Cancelable
import monix.execution.atomic.Atomic
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.internal.Platform

import scala.util.{Failure, Success}

object TaskCreateSuite extends BaseTestSuite {
  test("Task.create should be stack safe, take 1") { implicit s =>
    // Describing basically mapBoth
    def sum(t1: Task[Int], t2: Task[Int]): Task[Int] =
      Task.create { (s, cb) =>
        implicit val scheduler = s
        val state = Atomic(null : Either[Int,Int])
        val composite = CompositeCancelable()

        composite += t1.runAsync(new Callback[Int] {
          def onSuccess(v1: Int): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Left(v1))) onSuccess(v1)
              case Right(v2) =>
                cb.onSuccess(v1 + v2)
              case Left(_) =>
                throw new IllegalStateException
            }

          def onError(ex: Throwable): Unit =
            cb.onError(ex)
        })

        composite += t2.runAsync(new Callback[Int] {
          def onSuccess(v2: Int): Unit =
            state.get match {
              case null =>
                if (!state.compareAndSet(null, Right(v2))) onSuccess(v2)
              case Left(v1) =>
                cb.onSuccess(v1 + v2)
              case Right(_) =>
                throw new IllegalStateException
            }

          def onError(ex: Throwable): Unit =
            cb.onError(ex)
        })
      }

    def sumAll(tasks: Seq[Task[Int]]): Task[Int] =
      tasks.foldLeft(Task(0))(sum)

    val count = if (Platform.isJVM) 100000 else 10000
    val receivedT = sumAll((0 until count).map(n => Task(n)))
    val receivedF = receivedT.runAsync

    s.tick()
    assertEquals(receivedF.value, Some(Success(count * (count - 1) / 2)))
  }

  test("Task.create should be stack safe, take 2") { implicit s =>
    // Describing basically mapBoth
    def sum(t1: Task[Int], t2: Task[Int]): Task[Int] =
      Task.create { (s, cb) =>
        implicit val scheduler = s
        val c = MultiAssignmentCancelable()

        c := t1.runAsync(new Callback[Int] {
          def onSuccess(v1: Int): Unit =
            c := t2.runAsync(new Callback[Int] {
              def onSuccess(v2: Int): Unit = cb.onSuccess(v1 + v2)
              def onError(ex: Throwable): Unit = cb.onError(ex)
            })

          def onError(ex: Throwable): Unit =
            cb.onError(ex)
        })
      }

    def sumAll(tasks: Seq[Task[Int]]): Task[Int] =
      tasks.foldLeft(Task(0))(sum)

    val count = if (Platform.isJVM) 100000 else 10000
    val receivedT = sumAll((0 until count).map(n => Task.evalAlways(n)))
    val receivedF = receivedT.runAsync

    s.tick()
    assertEquals(receivedF.value, Some(Success(count * (count - 1) / 2)))
  }

  test("Task.create should work onSuccess") { implicit s =>
    val t = Task.create[Int] { (s,cb) => cb.onSuccess(10); Cancelable.empty }
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Task.create should work onError") { implicit s =>
    val dummy = DummyException("dummy")
    val t = Task.create[Int] { (s,cb) => cb.onError(dummy); Cancelable.empty }
    val f = t.runAsync
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }
}
