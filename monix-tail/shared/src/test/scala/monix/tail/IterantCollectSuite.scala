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

package monix.tail

import monix.eval.DummyException
import org.scalacheck.Test.Parameters

object IterantCollectSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters =
    super.checkConfig.withMaxSize(64)

  test("Iterant.collect <=> List.collect") { implicit s =>
    check3 { (stream: AsyncStream[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf).toListL
      val expected = stream.toListL.map(_.collect(pf))
      received === expected
    }
  }

  test("Iterant.collect protects against user error") { implicit s =>
    check1 { (stream: AsyncStream[Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream ++ AsyncStream.now(1)).collect[Int] { case _ => throw dummy }
      received === AsyncStream.raiseError(dummy)
    }
  }

  test("Iterant.collect flatMap equivalence") { implicit s =>
    check3 { (stream: AsyncStream[Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf)
      val expected = stream.flatMap(x => if (pf.isDefinedAt(x)) AsyncStream.now(pf(x)) else AsyncStream.empty)
      received === expected
    }
  }
}
