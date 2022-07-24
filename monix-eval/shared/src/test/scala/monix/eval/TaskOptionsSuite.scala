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

import monix.eval.Task.Options
import monix.execution.Callback
import scala.concurrent.Promise

class TaskOptionsSuite extends BaseTestSuite {
  implicit val opts: Options = Task.defaultOptions.enableLocalContextPropagation

  def extractOptions: Task[Options] =
    Task.Async { (ctx, cb) =>
      cb.onSuccess(ctx.options)
    }

  fixture.test("change options with future") { implicit s =>
    val task = extractOptions.map { r =>
      assertEquals(r, opts)
    }
    task.runToFutureOpt
  }

  fixture.test("change options with callback") { implicit s =>
    val p = Promise[Options]()
    extractOptions.runAsyncOpt(Callback.fromPromise(p))

    for (r <- p.future) yield {
      assertEquals(r, opts)
    }
  }
}
