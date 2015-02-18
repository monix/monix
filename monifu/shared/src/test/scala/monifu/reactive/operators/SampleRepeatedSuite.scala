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
import monifu.reactive.subjects.PublishSubject
import monifu.reactive.{Observer, Observable}
import concurrent.duration._
import scala.concurrent.Future

object SampleRepeatedSuite extends BaseOperatorSuite {
  def waitForNext = 500.millis
  def waitForFirst = 500.millis

  def observable(sourceCount: Int) = Some {
    Observable.create[Long](_.observer.onNext(1L))
      .sampleRepeated(500.millis)
      .take(sourceCount)
      .scan(0L)((acc, _) => acc + 1)
  }

  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount + 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }


  def observableInError(sourceCount: Int, ex: Throwable) =
    Some {
      val source = Observable.intervalAtFixedRate(1.second)
        .take(sourceCount)

      createObservableEndingInError(source, ex)
        .sampleRepeated(500.millis)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("specified period should be respected if consumer is responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.sampleRepeated(500.millis)

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

    sub.onNext(1)

    s.tick()
    assertEquals(onNextCount, 0)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    s.tick(100.millis)
    assertEquals(onNextCount, 1)
    assertEquals(received, 1)

    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(500.millis)
    assert(wasCompleted)
    assertEquals(onNextCount, 2)
    assertEquals(received, 2)
  }

  test("specified period should not be respected if consumer is not responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.sampleRepeated(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCount += 1
        Future.delayedResult(1000.millis) {
          received += 1
          Continue
        }
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    sub.onNext(1)

    s.tick()
    assertEquals(onNextCount, 0)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 2)
    assertEquals(received, 1)

    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(1.second)
    assert(wasCompleted)
    assertEquals(onNextCount, 2)
    assertEquals(received, 2)
  }
}
