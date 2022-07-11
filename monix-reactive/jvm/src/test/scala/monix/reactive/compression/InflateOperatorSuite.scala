/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import java.util.zip.Deflater

import monix.reactive.Observable

import scala.concurrent.duration.Duration.Zero

object InflateOperatorSuite extends BaseDecompressionSuite with DeflateTestUtils {
  testAsync("long input, not wrapped in ZLIB header and trailer") { _ =>
    noWrapDeflatedStream(longText)
      .transform(inflate(1024, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }

  testAsync("inflate nowrap: remaining = 0 but not all was pulled") { _ =>
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

  /** Returns an observable that emits from its data-source
    * the specified `sourceCount` number of items. The `sourceCount`
    * is not necessarily equal to the number of elements emitted by
    * the resulting observable, being just a way to randomly vary
    * the events being emitted.
    */
  override def createObservable(sourceCount: Int): Option[InflateOperatorSuite.Sample] =
    Some {
      val o = Observable
        .repeatEval(jdkDeflate(longText, new Deflater(-1, true)))
        .take(sourceCount.toLong - 1)
        .transform(inflate(1024, noWrap = true))
        .map(_ => 1L)
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[InflateOperatorSuite.Sample] =
    Some {
      val o = (Observable
        .repeatEval(jdkDeflate(longText, new Deflater(-1, true)))
        .take(sourceCount.toLong)
        .transform(inflate(noWrap = true)) ++ Observable
        .repeatEval(longText) // corrupted payload
        .transform(inflate(noWrap = true)))
        .map(_ => 1L)
        .onErrorFallbackTo(Observable.raiseError(ex))
      Sample(o, sourceCount + 1, sourceCount + 1, Zero, Zero)
    }

  override def observableInError(sourceCount: Int, ex: Throwable): Option[InflateOperatorSuite.Sample] =
    Some {
      val o = createObservableEndingInError(
        Observable
          .repeatEval(jdkDeflate(longText, new Deflater(-1, true)))
          .take(sourceCount.toLong - 1)
          .transform(inflate(1024, noWrap = true))
          .map(_ => 1L),
        ex
      )
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def cancelableObservables(): Seq[InflateOperatorSuite.Sample] = Seq.empty
}
