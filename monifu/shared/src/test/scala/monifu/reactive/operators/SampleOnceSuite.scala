/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import monifu.concurrent.extensions._
import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}
import concurrent.duration._
import scala.concurrent.Future

object SampleOnceSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    Observable.intervalAtFixedRate(1.second)
      .take(sourceCount)
      .sample(500.millis)
  }

  def sum(sourceCount: Int) = {
    1L * sourceCount * (sourceCount - 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def waitForNext = 1.second

  def observableInError(sourceCount: Int, ex: Throwable) =
    observable(sourceCount).map(obs => createObservableEndingInError(obs, ex))

  def waitForFirst = 500.millis

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.intervalAtFixedRate(1.second)
      .take(sourceCount - 1)

    createObservableEndingInError(source, ex)
      .sample(500.millis)
  }

  test("specified period should be respected if consumer is responsive") { implicit s =>
    val obs = Observable.intervalAtFixedRate(1.second)
      .take(3)
      .sample(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCount += 1
        Future.delayedResult(100.millis) {
          received += 1
          Continue
        }
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    // sample
    s.tick(500.millis)
    assertEquals(received, 0)
    assertEquals(onNextCount, 1)
    assert(!wasCompleted)
    // consumer delay
    s.tick(100.millis)
    assertEquals(received, 1)
    assertEquals(onNextCount, 1)
    assert(!wasCompleted)
    // sample
    s.tick(400.millis)
    assertEquals(received, 1)
    assertEquals(onNextCount, 2)
    assert(!wasCompleted)
    // consumer delay
    s.tick(100.millis)
    assertEquals(received, 2)
    assertEquals(onNextCount, 2)
    assert(!wasCompleted)
    // sample
    s.tick(900.millis)
    assertEquals(received, 2)
    assertEquals(onNextCount, 3)
    assert(!wasCompleted)
    // consumer delay
    s.tick(100.millis)
    assertEquals(received, 3)
    assertEquals(onNextCount, 3)
    assert(wasCompleted)
  }

  test("specified period should not be respected if consumer is not responsive") { implicit s =>
    val obs = Observable.intervalAtFixedRate(1.second).take(3)
      .sample(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCount += 1
        Future.delayedResult(600.millis) {
          received += 1
          Continue
        }
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    // sample
    s.tick(500.millis)
    assertEquals(received, 0)
    assertEquals(onNextCount, 1)
    assert(!wasCompleted)
    // consumer delay
    s.tick(600.millis)
    assertEquals(received, 1)
    assertEquals(onNextCount, 2)
    assert(!wasCompleted)
    // sample
    s.tick(600.millis)
    assertEquals(received, 2)
    assertEquals(onNextCount, 2)
    assert(!wasCompleted)
    // sample
    s.tick(600.millis)
    assertEquals(received, 2)
    assertEquals(onNextCount, 3)
    assert(!wasCompleted)
    // consumer delay
    s.tick(600.millis)
    assertEquals(received, 3)
    assertEquals(onNextCount, 3)
    assert(wasCompleted)
  }
}
