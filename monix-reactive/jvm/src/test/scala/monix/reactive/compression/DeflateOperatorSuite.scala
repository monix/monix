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

import java.util.zip.Deflater

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.internal.operators.BaseOperatorSuite

import scala.concurrent.duration.Duration.Zero

object DeflateOperatorSuite extends BaseOperatorSuite with DeflateTestUtils {

  implicit val scheduler: Scheduler =
    Scheduler.computation(parallelism = 4, name = "compression-tests", daemonic = true)

  testAsync("deflate empty bytes") { _ =>
    Observable
      .fromIterable(List.empty)
      .transform(deflate(bufferSize = 100))
      .toListL
      .map(list =>
        assertEquals(
          list.flatten,
          jdkDeflate(Array.empty, new Deflater(-1, false)).toList
        )
      )
      .runToFuture
  }
  testAsync("deflates same as JDK") { _ =>
    Observable
      .now(longText)
      .transform(deflate(256))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, false)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, nowrap") { _ =>
    Observable
      .now(longText)
      .transform(deflate(256, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, true)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, small buffer") { _ =>
    Observable
      .now(longText)
      .transform(deflate(1))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, false)).toList))
      .runToFuture
  }
  testAsync("deflates same as JDK, nowrap, small buffer ") { _ =>
    Observable
      .now(longText)
      .transform(deflate(1, noWrap = true))
      .toListL
      .map(list => assertEquals(list.flatten, jdkDeflate(longText, new Deflater(-1, true)).toList))
      .runToFuture
  }

  override def createObservable(sourceCount: Int): Option[DeflateOperatorSuite.Sample] =
    Some {
      val o = Observable
        .repeatEval(longText)
        .take(sourceCount.toLong - 1)
        .transform(deflate())
        .map(_ => 1L)
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[DeflateOperatorSuite.Sample] = None

  override def observableInError(sourceCount: Int, ex: Throwable): Option[DeflateOperatorSuite.Sample] =
    Some {
      val o = createObservableEndingInError(
        Observable
          .repeatEval(longText)
          .take(sourceCount.toLong - 1)
          .transform(deflate())
          .map(_ => 1L),
        ex
      )
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def cancelableObservables(): Seq[DeflateOperatorSuite.Sample] = Seq.empty
}
