/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.execution.Scheduler
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

  testAsync("Local.bindCurrentIf should properly restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.bindCurrentAsyncIf(true)(Future {
        local := 100
      })
      v <- Future { local() }
    } yield v

    for (v <- f) yield assertEquals(v, 50)
  }

  testAsync("Local.bindCurrentAsyncIf should properly restore context during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val f = for {
      _ <- Future { local := 50 }
      _ <- Local.bindCurrentAsyncIf(true)(Future {
        local := 100
      })
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
