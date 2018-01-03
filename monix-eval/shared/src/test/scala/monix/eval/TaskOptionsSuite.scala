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
import monix.eval.Task.Options
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.Promise

object TaskOptionsSuite extends SimpleTestSuite {
  implicit val opts = Task.defaultOptions.enableLocalContextPropagation

  def extractOptions[A](fa: Task[A]): Task[Options] =
    Task.unsafeCreate { (ctx, cb) => cb.onSuccess(ctx.options) }

  testAsync("change options with future") {
    val task = extractOptions(Task.now(1)).map { r =>
      assertEquals(r, opts)
    }
    task.runAsyncOpt
  }

  testAsync("change options with callback") {
    val p = Promise[Options]()
    extractOptions(Task.now(1)).runAsyncOpt(Callback.fromPromise(p))

    for (r <- p.future) yield {
      assertEquals(r, opts)
    }
  }
}
