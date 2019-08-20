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

package monix.execution

import java.util.concurrent.CompletableFuture
import java.util.function.{BiFunction, Supplier}

import minitest.SimpleTestSuite
import monix.execution.misc.Local
import monix.execution.schedulers.TracingScheduler

object CompletableFutureLocalSuite extends SimpleTestSuite {
  testAsync("Local.isolate(CompletableFuture) should properly isolate during async boundaries") {
    implicit val s = TracingScheduler(Scheduler.singleThread("local-test"))

    val local = Local(0)

    val cf = CompletableFuture
      .supplyAsync(new Supplier[Any] {
        override def get(): Any = local := 50
      }, s)

    val cf2 =
      Local.isolate {
        cf.handleAsync(new BiFunction[Any, Throwable, Any] {
          def apply(r: Any, error: Throwable): Any = {
            local := 100
          }
        }, s)
      }.handleAsync(new BiFunction[Any, Throwable, Any] {
        def apply(r: Any, error: Throwable): Any = {
          local()
        }
      }, s)

    for (v <- FutureUtils.fromJavaCompletable(cf2)) yield assertEquals(v.asInstanceOf[Int], 50)
  }
}
