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

import minitest.api.AssertionException
import monix.reactive.Observable

object InflateTest extends BaseTestSuite with DeflateTestUtils {
  testAsync("short stream") {
    deflatedStream(shortText)
      .inflate(64)
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs") {
    (deflatedStream(shortText) ++ deflatedStream(otherShortText))
      .inflate(64, chunkSize = 5)
      .toListL
      .map(list => assertEquals(list, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs as a single chunk") {
    (deflatedStream(shortText) ++ deflatedStream(otherShortText))
      .inflate(64)
      .toListL
      .map(list => assertEquals(list, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("long input") {
    deflatedStream(longText)
      .inflate(64)
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, buffer smaller than chunks") {
    deflatedStream(longText)
      .inflate(1)
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, chunks smaller then buffer") {
    deflatedStream(longText)
      .inflate(bufferSize = 500, chunkSize = 1)
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, not wrapped in ZLIB header and trailer")(
    noWrapDeflatedStream(longText)
      .inflate(1024, noWrap = true)
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  )
  testAsync("fail eartly if header is corrupted") {
    Observable
      .fromIterable(Seq(1, 2, 3, 4, 5).map(_.toByte))
      .inflate()
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }
  testAsync("inflate nowrap: remaining = 0 but not all was pulled") {
    // This case shown error when not all data was pulled out of inflater
    noWrapDeflatedStream(inflateRandomExampleThatFailed)
      .inflate(bufferSize = 11, chunkSize = 40, noWrap = true)
      .toListL
      .map(list => assertEquals(list, inflateRandomExampleThatFailed.toList))
      .runToFuture
  }
  testAsync("fail if input stream finished unexpected") {
    deflatedStream(longText)
      .take(20)
      .inflate()
      .toListL
      .map { e =>
        fail("should have failed")
      }
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }
}
