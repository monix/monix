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

import java.util.zip.Deflater

import monix.reactive.Observable

object DeflateTest extends BaseTestSuite with DeflateTestUtils {

  testAsync("deflate empty bytes") {
    Observable
      .fromIterable(List.empty)
      .transform(deflate(bufferSize = 100))
      .toListL
      .map(list =>
        assertEquals(
          list.flatten,
          jdkDeflate(
            Array.empty,
            new Deflater(-1, false)
          ).toList
        ))
      .runToFuture
  }
  testAsync("deflates same as JDK") {
    Observable
      .now(longText)
      .transform(deflate(256))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, false)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, nowrap") {
    Observable
      .now(longText)
      .transform(deflate(256, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, true)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, small buffer") {
    Observable
      .now(longText)
      .transform(deflate(1))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, false)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, nowrap, small buffer ") {
    Observable
      .now(longText)
      .transform(deflate(1, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, true)).toList))
      .runToFuture
  }
}
