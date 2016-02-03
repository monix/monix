/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators

import monix.execution.FutureUtils.ops._
import monix.streams.broadcast.PublishProcessor
import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.Observer
import scala.concurrent.duration._
import scala.concurrent.Future

object SampleRepeatedSuite extends BaseOperatorSuite {
  def waitNext = 500.millis
  def waitFirst = 500.millis

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long](_.onNext(1L))
      .sampleRepeated(500.millis)
      .take(sourceCount)
      .scan(0L)((acc, _) => acc + 1)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
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

      val o = createObservableEndingInError(source, ex).sampleRepeated(500.millis)
      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("specified period should be respected if consumer is responsive") { implicit s =>
    val sub = PublishProcessor[Long]()
    val obs = sub.sampleRepeated(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribeFn(new Observer[Long] {
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
    assertEquals(onNextCount, 1)
    assertEquals(received, 1)
  }

  test("specified period should not be respected if consumer is not responsive") { implicit s =>
    val sub = PublishProcessor[Long]()
    val obs = sub.sampleRepeated(500.millis)

    var onNextCalls = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCalls += 1
        Future.delayedResult(1000.millis) {
          received += 1
          Continue
        }
      }

      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    sub.onNext(1)

    s.tick()
    assertEquals(onNextCalls, 0)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCalls, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCalls, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCalls, 2)
    assertEquals(received, 1)

    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(1.second)
    assertEquals(onNextCalls, 2)
    assertEquals(received, 2)
    assert(wasCompleted)
  }
}
