/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.reactive.compress

import monix.eval.Task
import monix.reactive.Observable
import org.scalacheck.Prop

object DeflateIntegrationTest extends BaseTestSuite with DeflateTestUtils {
  private implicit def a[A]: Task[Boolean] => Prop =
    _.runSyncUnsafe()

  test("inflate(deflate(_)) == identity") {
    check1 { (input: String) =>
      Observable
        .fromIterable(input.getBytes())
        .deflate()
        .inflate()
        .toListL
        .map(l => new String(l.toArray) == input)
    }
  }

  test("inflate(deflate(_)) == identity - nowrap") {
    check1 { (input: String) =>
      Observable
        .fromIterable(input.getBytes())
        .deflate(noWrap = true)
        .inflate(noWrap = true)
        .toListL
        .map(l => new String(l.toArray) == input)
    }
  }

  test("inflate(jDeflate(_)) == identity") {
    check1 { (input: String) =>
      deflatedStream(input.getBytes)
        .inflate()
        .toListL
        .map(compressed => new String(compressed.toArray) == input)
    }
  }

  test("inflate(jDeflate(_)) == identity - nowrap") {
    check1 { (input: String) =>
      noWrapDeflatedStream(input.getBytes)
        .inflate(noWrap = true)
        .toListL
        .map(compressed => new String(compressed.toArray) == input)
    }
  }

  test("jInflate(deflate(_) == identity") {
    check1 { (input: String) =>
      Observable
        .fromIterable(input.getBytes())
        .deflate()
        .toListL
        .map { compressed =>
          val decompressed = jdkInflate(compressed.toArray, noWrap = false)
          new String(decompressed) == input
        }
    }
  }

  test("jInflate(deflate(_) == identity - nowrap") {
    check1 { (input: String) =>
      Observable
        .fromIterable(input.getBytes())
        .deflate(noWrap = true)
        .toListL
        .map { compressed =>
          val decompressed = jdkInflate(compressed.toArray, noWrap = true)
          new String(decompressed) == input
        }
    }
  }
}
