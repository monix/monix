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

import scala.util.Success
import cats.implicits._

object TaskExecuteWithLocalContextSuite extends BaseTestSuite {
  test("cats' parSequence with LCP is stack safe") { implicit sc =>
    val f = List
      .fill(2000)(Task.unit)
      .parSequence_
      .executeWithOptions(_.enableLocalContextPropagation)
      .runToFuture

    sc.tick()

    assertEquals(f.value, Some(Success(())))
  }
}
