/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantZipMapSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters =
    Test.Parameters.default.withMaxSize(256)

  test("Iterant.zipMap equivalence with List.zip") { implicit s =>
    check3 { (stream1: Iterant[Int], stream2: Iterant[Int], f: (Int, Int) => Long) =>
      val received = stream1.zipMap(stream2)(f).toListL
      val expected = Task.zipMap2(stream1.toListL, stream2.toListL)((l1, l2) => l1.zip(l2).map { case (a,b) => f(a,b) })
      received === expected
    }
  }
}
