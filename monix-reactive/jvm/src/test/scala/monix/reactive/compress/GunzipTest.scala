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

object GunzipTest extends BaseTestSuite with GzipTestsUtils {
  testAsync("short stream") {
    jdkGzippedStream(shortText)
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("stream of two gzipped inputs") {
    (jdkGzippedStream(shortText) ++ jdkGzippedStream(otherShortText))
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("long input") {
    jdkGzippedStream(longText)
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, no SYNC_FLUSH") {
    jdkGzippedStream(longText, false)
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, buffer smaller than chunks") {
    jdkGzippedStream(longText)
      .transform(gunzip(1, chunkSize = 500))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, chunks smaller then buffer") {
    jdkGzippedStream(longText)
      .transform(gunzip(500, chunkSize = 1))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("fail early if header is corrupted") {
    Observable
      .fromIterable(1 to 10)
      .map(_.toByte)
      .transform(gunzip())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }
  testAsync("fail if input stream finished unexpected") {
    jdkGzippedStream(longText)
      .take(20)
      .transform(gunzip())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }
  testAsync("no output on very incomplete stream is not OK") {
    Observable
      .fromIterable(1 to 5)
      .map(_.toByte)
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
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with FCOMMENT") {
    headerWithComment
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with FNAME") {
    headerWithFileName
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with CRC16") {
    headerWithCrc
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("parses header with CRC16, FNAME, FCOMMENT, FEXTRA") {
    headerWithAll
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
}
