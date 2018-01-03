/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.execution.exceptions.{CompositeException, DummyException}
import monix.reactive.{BaseTestSuite, Observable, Observer}
import scala.concurrent.Future

object ConcatDelayErrorsSuite extends BaseTestSuite {
  test("flatMapDelayErrors works for synchronous observers") { implicit s =>
    val obs = Observable.range(0, 100)
      .flatMapDelayErrors(x => Observable(x,x,x)
        .endWithError(DummyException(x.toString)))

    var result = 0L
    var errorThrown: Throwable = null

    obs.unsafeSubscribeFn(new Observer.Sync[Long] {
      var sum = 0L
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable) = {
        result = sum
        errorThrown = ex
      }

      def onComplete() = {
        result = sum
      }
    })

    s.tick()
    assertEquals(result, 99L * 50L * 3L)
    errorThrown match {
      case ref: CompositeException =>
        val list = ref.errors.collect { case DummyException(nr) => nr.toInt }
        assertEquals(list, (0 until 100).reverse.toList)
      case _ =>
        fail(s"Invalid value for thrown exception: $errorThrown")
    }
  }

  test("flatMapDelayErrors works for asynchronous observers") { implicit s =>
    val obs = Observable.range(0, 100)
      .flatMapDelayErrors(x => Observable(x,x,x).endWithError(DummyException(x.toString)))

    var result = 0L
    var errorThrown: Throwable = null

    obs.subscribe(new Observer[Long] {
      var sum = 0L
      def onNext(elem: Long) = Future {
        sum += elem
        Continue
      }

      def onError(ex: Throwable) = {
        result = sum
        errorThrown = ex
      }

      def onComplete() = {
        result = sum
      }
    })

    s.tick()
    assertEquals(result, 99L * 50L * 3L)
    errorThrown match {
      case ref: CompositeException =>
        val list = ref.errors.collect { case DummyException(nr) => nr.toInt }
        assertEquals(list, (0 until 100).reverse.toList)
      case _ =>
        fail(s"Invalid value for thrown exception: $errorThrown")
    }
  }
}
