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

package monix.execution.misc

import minitest.SimpleTestSuite
import monix.execution.{ Cancelable, CancelableFuture, Scheduler }
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TracingScheduler
import scala.concurrent.Future

object LocalJVMSuite extends SimpleTestSuite {
  testAsync("Local.isolate should properly isolate during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.isolate {
        Future {
          local := 100
        }
      }
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.isolate(CancelableFuture) should properly isolate during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- CancelableFuture(Future { local := 50 }, Cancelable())
      _ <- Local.isolate {
        CancelableFuture(
          Future {
            local := 100
          },
          Cancelable()
        )
      }
      v <- CancelableFuture(Future { local() }, Cancelable())
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.isolate should properly isolate during async boundaries on error") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.isolate {
        Future {
          local := 100
        }.flatMap(_ => Future.failed(DummyException("boom")))
      }.recoverWith { case _ => Future.successful(()) }
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.bindCurrentIf(CancelableFuture) should properly restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.bindCurrentIf(true)(CancelableFuture(
        Future {
          local := 100
        },
        Cancelable.empty
      ))
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.bind(Local.defaultContext()) should restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.bind(Local.newContext()) { Future { local := 100 } }
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.bindClear should restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.bindClear { Future { local := 100 } }
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("local.bind should properly restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- local.bind(100)(Future { () })
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("local.bindClear should properly restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- local.bindClear(Future { () })
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }
}
