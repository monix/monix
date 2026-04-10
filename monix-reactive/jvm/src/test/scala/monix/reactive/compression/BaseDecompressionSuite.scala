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

import minitest.api.AssertionException
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.internal.operators.BaseOperatorSuite

abstract class BaseDecompressionSuite extends BaseOperatorSuite with CompressionTestData {

  def jdkCompressedStream(input: Array[Byte], chunkSize: Int = 32 * 1024): Observable[Array[Byte]]
  def decompress(bufferSize: Int = 32 * 1024): Observable[Array[Byte]] => Observable[Array[Byte]]

  implicit val scheduler: Scheduler =
    Scheduler.computation(parallelism = 4, name = "compression-tests", daemonic = true)

  testAsync("short stream") { _ =>
    jdkCompressedStream(shortText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, shortText.toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs") { _ =>
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs as a single chunk") { _ =>
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .bufferTumbling(2)
      .concatMapIterable(seq => seq.toList)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("long input") { _ =>
    jdkCompressedStream(longText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("long input, buffer smaller than chunks") { _ =>
    jdkCompressedStream(longText, chunkSize = 500)
      .transform(decompress(bufferSize = 1))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("long input, chunks smaller then buffer") { _ =>
    jdkCompressedStream(longText, chunkSize = 1)
      .transform(decompress(bufferSize = 500))
      .toListL
      .map(list => assertEquals(list.flatten, longText.toList))
      .runToFuture
  }
  testAsync("fail early if header is corrupted") { _ =>
    Observable
      .now(Array(1, 2, 3, 4, 5).map(_.toByte))
      .transform(decompress())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }

  testAsync("fail if input stream finished unexpected") { _ =>
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
