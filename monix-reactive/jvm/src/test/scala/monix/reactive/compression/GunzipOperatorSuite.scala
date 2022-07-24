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

package monix.reactive.compression

import monix.reactive.Observable
import munit.FailException

import scala.concurrent.duration.Duration.Zero

class GunzipOperatorSuite extends BaseDecompressionSuite with GzipTestsUtils {

  fixture.test("long input, no SYNC_FLUSH") { _ =>
    jdkGzippedStream(longText, syncFlush = false)
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }

  fixture.test("no output on very incomplete stream is not OK") { _ =>
    Observable
      .now((1 to 5).map(_.toByte).toArray)
      .transform(gunzip())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[FailException] => () }
      .runToFuture
  }
  fixture.test("parses header with FEXTRA") { _ =>
    headerWithExtra
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  fixture.test("parses header with FCOMMENT") { _ =>
    headerWithComment
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  fixture.test("parses header with FNAME") { _ =>
    headerWithFileName
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  fixture.test("parses header with CRC16") { _ =>
    headerWithCrc
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  fixture.test("parses header with CRC16, FNAME, FCOMMENT, FEXTRA") { _ =>
    headerWithAll
      .transform(gunzip(64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }

  override def jdkCompressedStream(input: Array[Byte], chunkSize: Int): Observable[Array[Byte]] =
    jdkGzippedStream(input, chunkSize = chunkSize)

  override def decompress(bufferSize: Int): Observable[Array[Byte]] => Observable[Array[Byte]] = gunzip(bufferSize)

  override def createObservable(sourceCount: Int): Option[Sample] =
    Some {
      val o = Observable
        .repeatEval(jdkGzip(longText, syncFlush = false))
        .take(sourceCount.toLong - 1)
        .transform(gunzip())
        .map(_ => 1L)
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Sample] =
    Some {
      val o = (Observable
        .repeatEval(jdkGzip(longText, syncFlush = false))
        .take(sourceCount.toLong)
        .transform(gunzip()) ++ Observable
        .repeatEval(longText) // corrupted payload
        .transform(gunzip()))
        .map(_ => 1L)
        .onErrorFallbackTo(Observable.raiseError(ex))
      Sample(o, sourceCount + 1, sourceCount + 1, Zero, Zero)
    }

  override def observableInError(sourceCount: Int, ex: Throwable): Option[Sample] =
    Some {
      val o = createObservableEndingInError(
        Observable
          .repeatEval(jdkGzip(longText, syncFlush = false))
          .take(sourceCount.toLong - 1)
          .transform(gunzip(64))
          .map(_ => 1L),
        ex
      )
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def cancelableObservables(): Seq[Sample] = Seq.empty
}
