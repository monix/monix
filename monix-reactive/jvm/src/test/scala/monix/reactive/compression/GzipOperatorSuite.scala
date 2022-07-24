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

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.internal.operators.BaseOperatorSuite

import scala.concurrent.duration.Duration.Zero

class GzipOperatorSuite extends BaseOperatorSuite with GzipTestsUtils {

  implicit val scheduler: Scheduler =
    Scheduler.computation(parallelism = 4, name = "compression-tests", daemonic = true)

  fixture.test("gzip empty bytes, small buffer") { _ =>
    Observable
      .empty[Array[Byte]]
      .transform(gzip(1))
      .toListL
      .map(l => assert(jdkGunzip(l.flatten.toArray).isEmpty))
      .runToFuture
  }
  fixture.test("gzip empty bytes") { _ =>
    Observable
      .empty[Array[Byte]]
      .transform(gzip(`1K`))
      .toListL
      .map(l => assert(jdkGunzip(l.flatten.toArray).isEmpty))
      .runToFuture
  }
  fixture.test("gzips, small chunks, small buffer") { _ =>
    Observable
      .fromIterable(longText)
      .bufferTumbling(1)
      .map(_.toArray)
      .transform(gzip(1))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }
  fixture.test("gzips, small chunks, 1k buffer") { _ =>
    Observable
      .fromIterable(longText)
      .bufferTumbling(1)
      .map(_.toArray)
      .transform(gzip(`1K`))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }
  fixture.test("chunks bigger than buffer") { _ =>
    Observable
      .fromIterable(longText)
      .bufferTumbling(`1K`)
      .map(_.toArray)
      .transform(gzip(64))
      .toListL
      .map(l => assertArrayEquals(jdkGunzip(l.flatten.toArray), longText))
      .runToFuture
  }

  private def assertArrayEquals[T](a1: Array[T], a2: Array[T]): Unit = {
    assertEquals(a1.toList, a2.toList)
  }

  override def createObservable(sourceCount: Int): Option[Sample] =
    Some {
      val o = Observable
        .repeatEval(longText)
        .take(sourceCount.toLong - 1)
        .transform(gzip())
        .map(_ => 1L)
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Sample] = None

  override def observableInError(sourceCount: Int, ex: Throwable): Option[Sample] =
    Some {
      val o = createObservableEndingInError(
        Observable
          .repeatEval(longText)
          .take(sourceCount.toLong - 1)
          .transform(gzip())
          .map(_ => 1L),
        ex
      )
      Sample(o, sourceCount, sourceCount, Zero, Zero)
    }

  override def cancelableObservables(): Seq[Sample] = Seq.empty
}
