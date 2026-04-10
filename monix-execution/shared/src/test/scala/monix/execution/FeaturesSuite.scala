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

package monix.execution

import cats.implicits._
import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.execution.Features.{ Flag, Flags }
import org.scalacheck.Arbitrary

object FeaturesSuite extends SimpleTestSuite with Checkers {
  implicit val arbFeatures: Arbitrary[Features] =
    Arbitrary(implicitly[Arbitrary[Long]].arbitrary.map(l => new Features(l.asInstanceOf[Flags])))
  val allFlags =
    (0 until 64).map(i => (1L << i).asInstanceOf[Flag])

  test("Features.intersect") {
    check2 { (f1: Features, f2: Features) =>
      val r = f1.intersect(f2)
      allFlags.forall { flag =>
        (f1.contains(flag) && f2.contains(flag)) === r.contains(flag)
      }
    }
  }

  test("Features.union") {
    check2 { (f1: Features, f2: Features) =>
      val r = f1.union(f2)
      allFlags.forall { flag =>
        (f1.contains(flag) || f2.contains(flag)) === r.contains(flag)
      }
    }
  }

  test("Features.diff") {
    check2 { (f1: Features, f2: Features) =>
      val r = f1.diff(f2)
      allFlags.forall { flag =>
        if (f2.contains(flag))
          !r.contains(flag)
        else
          f1.contains(flag) === r.contains(flag)
      }
    }
  }

  test("Features.+") {
    check2 { (f: Features, i: Int) =>
      val flag = Features.flag((1 << Math.floorMod(i, 64)).toLong)
      val f2 = f + flag
      f2.contains(flag)
    }
  }
}
