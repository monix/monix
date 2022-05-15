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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import monix.execution.FutureUtils.extensions._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

object ThrottleLatestSuite extends BaseOperatorSuite {
  def count(sourceCount: Int): Int = {
    sourceCount / 2
  }

  def sum(sourceCount: Int): Int = {
    ((count(sourceCount) - 1) * (count(sourceCount) - 1)) + 1
  }

  def createObservable(sourceCount: Int) = Some {
    val elemsToTake = sourceCount.toLong - 1
    val o: Observable[Long] = {
      Observable(1L).delayOnComplete(100.millisecond) ++
        Observable.intervalAtFixedRate(500.millisecond).take(elemsToTake)
    }

    Sample(o.throttleLatest(1.second, false), count(sourceCount), sum(sourceCount), 0.second, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.intervalAtFixedRate(2.second).throttleLatest(1.second, true)
    Seq(
      Sample(o, 1, 0, 0.seconds, 2.seconds),
      Sample(o, 2, 1, 2.seconds, 2.seconds)
    )
  }

  test("should emit last element onComplete if emitLast is set to true") { implicit s =>
    val source: Observable[Long] =
      Observable.intervalAtFixedRate(110.millisecond).take(30).throttleLatest(1.second, true)
    var wasCompleted = false
    val elements = ArrayBuffer[Long]()
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        elements.append(elem)
        Continue
      }
      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    s.tick(30 * 110.millisecond)
    assertEquals(elements.toSeq, Seq(0, 9, 18, 27, 29))
    assert(wasCompleted)
  }

  test("should not emit last element onComplete if emitLast is set to false") { implicit s =>
    val source: Observable[Long] =
      Observable.intervalAtFixedRate(110.millisecond).take(30).throttleLatest(1.second, false)
    var wasCompleted = false
    val elements = ArrayBuffer[Long]()
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        elements.append(elem)
        Continue
      }
      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    s.tick(30 * 110.millisecond)
    assertEquals(elements.toSeq, Seq(0, 9, 18, 27))
    assert(wasCompleted)
  }

  test("specified period should be respected if consumer is responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.throttleLatest(500.millis, true)

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
    sub.onNext(2)

    s.tick()
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 2)
    assertEquals(received, 1)

    s.tick(100.millis)
    assertEquals(onNextCount, 2)
    assertEquals(received, 2)

    sub.onNext(3)
    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(100.millis)
    assertEquals(onNextCount, 3)
    assertEquals(received, 3)
    assert(wasCompleted)
  }

  test("specified period should not be respected if consumer is not responsive") { implicit s =>
    val sub = PublishSubject[Long]()
    val obs = sub.throttleLatest(500.millis, true)

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
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    s.tick(500.millis)
    assertEquals(onNextCount, 1)
    assertEquals(received, 0)

    sub.onComplete()
    s.tick()
    assert(!wasCompleted)

    s.tick(500.millisecond)
    assertEquals(onNextCount, 1)
    assertEquals(received, 1)
    assert(wasCompleted)
  }

}
