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

object GzipTest extends BaseTestSuite with GzipTestsUtils {
  testAsync("gzip empty bytes, small buffer") {
    Observable
      .empty[Array[Byte]]
      .transform(gzip(1))
      .toListL
      .map(l => assert(jdkGunzip(l.flatten.toArray).isEmpty))
      .runToFuture
  }
  testAsync("gzip empty bytes") {
    Observable
      .empty[Array[Byte]]
      .transform(gzip(`1K`))
      .toListL
      .map(l => assert(jdkGunzip(l.flatten.toArray).isEmpty))
      .runToFuture
  }
  testAsync("gzips, small chunks, small buffer") {
    Observable
      .fromIterable(longText)
      .bufferTumbling(1)
      .map(_.toArray)
      .transform(gzip(1))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }
  testAsync("gzips, small chunks, 1k buffer") {
    Observable
      .fromIterable(longText)
      .bufferTumbling(1)
      .map(_.toArray)
      .transform(gzip(`1K`))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }
  testAsync("chunks bigger than buffer") {
    Observable
      .fromIterable(longText)
      .bufferTumbling(`1K`)
      .map(_.toArray)
      .transform(gzip(64))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }
}