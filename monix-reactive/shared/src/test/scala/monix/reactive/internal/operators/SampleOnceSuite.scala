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
import monix.execution.FutureUtils.extensions._
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object SampleOnceSuite extends BaseOperatorSuite {
  def waitFirst = 1.second
  def waitNext = 1.second

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.intervalAtFixedRate(500.millis, 1.second)
      .sample(1.second)
      .take(sourceCount)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def sum(sourceCount: Int) = {
    1L * sourceCount * (sourceCount - 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.intervalAtFixedRate(1.second).take(sourceCount+1)
    val o = createObservableEndingInError(source, ex).sample(500.millis)
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None



  test("specified period should be respected if consumer is responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.sample(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCount += 1
        Future.delayedResult(100.millis) {
          received = 1
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

    s.tick(400.millis)
    assert(wasCompleted)
  }

  test("specified period should not be respected if consumer is not responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.sample(500.millis)

    var onNextCount = 0
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        onNextCount += 1
        Future.delayedResult(1000.millis) {
          received = 1
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
    assertEquals(onNextCount, 1)
    assertEquals(received, 1)

    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(1.second)
    assert(wasCompleted)
  }

  override def cancelableObservables() = {
    val o = Observable.intervalAtFixedRate(500.millis, 1.second).sample(1.second)
    Seq(
      Sample(o, 0, 0, 0.seconds, 0.seconds),
      Sample(o, 1, 0, 1.seconds, 0.seconds),
      Sample(o, 2, 1, 2.seconds, 0.seconds),
      Sample(o, 2, 1, 2.seconds + 500.millis, 0.seconds)
    )
  }
}
