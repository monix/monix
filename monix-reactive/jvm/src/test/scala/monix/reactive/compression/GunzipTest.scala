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

package monix.reactive.compression

import minitest.api.AssertionException
import monix.reactive.Observable

object GunzipTest extends BaseDecompressionTest with GzipTestsUtils {

  testAsync("long input, no SYNC_FLUSH") {
    jdkGzippedStream(longText, syncFlush = false)
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }

  testAsync("no output on very incomplete stream is not OK") {
    Observable
      .now((1 to 5).map(_.toByte).toArray)
      .transform(gunzip())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }
  testAsync("parses header with FEXTRA") {
    headerWithExtra
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with FCOMMENT") {
    headerWithComment
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with FNAME") {
    headerWithFileName
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with CRC16") {
    headerWithCrc
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with CRC16, FNAME, FCOMMENT, FEXTRA") {
    headerWithAll
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }

  override def jdkCompressedStream(input: Array[Byte], chunkSize: Int): Observable[Array[Byte]] =
    jdkGzippedStream(input, chunkSize = chunkSize)

  override def decompress(bufferSize: Int): Observable[Array[Byte]] => Observable[Array[Byte]] = gunzip(bufferSize)
}
