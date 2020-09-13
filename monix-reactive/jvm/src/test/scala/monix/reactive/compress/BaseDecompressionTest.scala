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

abstract class BaseDecompressionTest extends BaseTestSuite with CompressionTestData {

  def jdkCompressedStream(input: Array[Byte], chunkSize: Int = 32 * 1024): Observable[Array[Byte]]
  def decompress(bufferSize: Int = 32 * 1024): Observable[Array[Byte]] => Observable[Array[Byte]]

  testAsync("short stream") {
    jdkCompressedStream(shortText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs") {
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs as a single chunk") {
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .bufferTumbling(2)
      .concatMapIterable(identity)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("long input") {
    jdkCompressedStream(longText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("long input, buffer smaller than chunks") {
    jdkCompressedStream(longText, chunkSize = 500)
      .transform(decompress(bufferSize = 1))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("long input, chunks smaller then buffer") {
    jdkCompressedStream(longText, chunkSize = 1)
      .transform(decompress(bufferSize = 500))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("fail early if header is corrupted") {
    Observable
      .now(Array(1, 2, 3, 4, 5).map(_.toByte))
      .transform(decompress())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }

  testAsync("fail if input stream finished unexpected") {
    jdkCompressedStream(longText)
      .map(_.take(20))
      .transform(decompress())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .void
      .runToFuture
  }
}
