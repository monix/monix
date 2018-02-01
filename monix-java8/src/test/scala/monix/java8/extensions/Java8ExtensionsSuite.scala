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

package monix.java8.extensions

import scala.concurrent.Future
import scala.util.{Failure, Success}

import monix.eval.{BaseTestSuite, Task}
import cats.syntax.eq._
import monix.execution.exceptions.DummyException

import java.util.concurrent.{CompletableFuture, CompletionException}


object Java8ExtensionsSuite extends BaseTestSuite {
  test("CompletableFuture.asScala works") { implicit s =>
    val cf = CompletableFuture.completedFuture(42)
    assertEquals(cf.asScala.value, Some(Success(42)))
  }

  test("CompletableFuture.asScala is non-terminating on cancelled source") { implicit s =>
    val cf = new CompletableFuture[Int]
    cf.cancel(true)
    assert((cf.asScala: Future[Int]) === (Future.never: Future[Int]))
  }

  test("CompletableFuture.asScala reports errors") { implicit s =>
    val dummy = DummyException("dummy")
    val cf = new CompletableFuture[Int]
    cf.completeExceptionally(dummy)
    assertEquals(cf.asScala.value, Some(Failure(dummy)))
  }

  test("Future.asJava works") { implicit s =>
    val f = Future.successful(42)
    val cf = f.asJava
    s.tickOne()
    assertEquals(cf.getNow(-1), 42)
  }

  test("Future.asJava reports errors") { implicit s =>
    val dummy = DummyException("dummy")
    val ef = Future.failed[Int](dummy)
    val ecf = ef.asJava
    s.tickOne()
    try {
      ecf.getNow(-1)
      fail("Should throw an error")
    } catch {
      case ex: CompletionException =>
        assertEquals(ex.getCause, dummy)
    }
  }

  test("Task.fromCompletableFuture works") { implicit s =>
    val cf = new CompletableFuture[Int]()
    val task = Task.fromCompletableFuture(cf)
    cf.complete(42)
    assert(task === Task(42))
  }

  test("Task.fromCompletableFuture reports errors") { implicit s =>
    val dummy = DummyException("dummy")
    val cf = new CompletableFuture[Int]()
    val task = Task.fromCompletableFuture(cf)
    cf.completeExceptionally(dummy)
    assert(task === Task.raiseError(dummy))
  }

  test("Task.fromCompletableFuture preserves cancelability") { implicit s =>
    val cf = new CompletableFuture[Int]()
    val task = Task.fromCompletableFuture(cf)
    cf.cancel(true)
    assert(task === Task.never[Int])
  }

  test("Task.deferCompletableFutureAction works") { implicit s =>
    var effect = 0
    val task = Task.deferCompletableFutureAction[Int] { executor =>
      CompletableFuture.supplyAsync(() => {
        effect += 1
        42
      }, executor)
    }
    val result = task.runAsync
    assertEquals(effect, 0)
    s.tickOne() // check that CompletableFutures are ran using provided scheduler
    assertEquals(effect, 1)
    assertEquals(result.value, Some(Success(42)))
  }
}
