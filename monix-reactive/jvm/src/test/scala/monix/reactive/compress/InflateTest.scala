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

import monix.reactive.Observable

object InflateTest extends BaseDecompressionTest with DeflateTestUtils {
  testAsync("long input, not wrapped in ZLIB header and trailer")(
    noWrapDeflatedStream(longText)
      .transform(inflate(1024, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  )

  testAsync("inflate nowrap: remaining = 0 but not all was pulled") {
    // This case shown error when not all data was pulled out of inflater
    noWrapDeflatedStream(inflateRandomExampleThatFailed, chunkSize = 40)
      .transform(inflate(bufferSize = 11, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, inflateRandomExampleThatFailed.toList))
      .runToFuture
  }

  override def jdkCompressedStream(input: Array[Byte], chunkSize: Int): Observable[Array[Byte]] =
    deflatedStream(input, chunkSize)

  override def decompress(bufferSize: Int): Observable[Array[Byte]] => Observable[Array[Byte]] = inflate(bufferSize)
}
