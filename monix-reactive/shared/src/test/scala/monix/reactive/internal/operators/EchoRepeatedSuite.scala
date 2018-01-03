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

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object EchoRepeatedSuite extends BaseOperatorSuite {
  def waitFirst = 1.second
  def waitNext = 1.second

  def createObservable(sourceCount: Int) = Some {
    val source = Observable.now(1L).delayOnComplete(sourceCount.seconds * 2)
    val o = source.echoRepeated(1.second).drop(1).take(sourceCount)
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int) = {
    sourceCount
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    if (sourceCount > 1) {
      val source = Observable.now(1L)
        .delayOnComplete((sourceCount - 1).seconds + 500.millis)
        .endWithError(ex)

      val o = source.echoRepeated(1.second)
      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    } else {
      val source = Observable.now(1L).endWithError(ex)
      val o = source.echoRepeated(1.second)
      Sample(o, 1, 1, waitFirst, waitNext)
    }
  }



  test("should timeout on inactivity and start emitting") { implicit s =>
    val channel = PublishSubject[Int]()
    var received = 0
    var wasCompleted = false

    channel.echoRepeated(1.second)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = true
      })

    channel.onNext(1)
    assertEquals(received, 1)
    s.tick(900.millis)
    assertEquals(received, 1)

    channel.onNext(2)
    assertEquals(received, 3)
    s.tick(900.millis)
    assertEquals(received, 3)
    s.tick(100.millis)
    assertEquals(received, 5)

    channel.onComplete()
    assertEquals(wasCompleted, true)
  }

  test("time for processing upstream messages should be ignored") { implicit s =>
    val channel = PublishSubject[Int]()
    var received = 0
    var wasCompleted = false

    channel.echoRepeated(1.second)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          received += 1
          if (received % 2 == 1)
            Future.delayedResult(2.seconds)(Continue)
          else
            Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = true
      })

    val ack = channel.onNext(1)
    assert(ack != Continue)
    assertEquals(received, 1)

    // waiting the delayedResult
    s.tick(2.seconds)
    assertEquals(received, 1)
    assertEquals(ack.value.get, Continue.AsSuccess)

    // should still not trigger
    s.tick(900.millis)
    assertEquals(received, 1)

    // this should trigger, since we waited the original 2 secs + 1
    s.tick(100.millis)
    assertEquals(received, 2)

    channel.onComplete()
    assertEquals(wasCompleted, true)
  }

  test("interval should be at fixed rate") { implicit s =>
    val channel = PublishSubject[Int]()
    var received = 0
    var wasCompleted = false

    channel.echoRepeated(1.second)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] =
          Future.delayedResult(500.millis) {
            received += 1
            Continue
          }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = true
      })

    channel.onNext(1)
    assertEquals(received, 0)
    s.tick(500.millis)
    assertEquals(received, 1)

    s.tick(1.second + 500.millis)
    assertEquals(received, 2)
    s.tick(1.second)
    assertEquals(received, 3)
    s.tick(1.second)
    assertEquals(received, 4)

    channel.onComplete()
    assertEquals(wasCompleted, true)
  }

  test("new item should interrupt the streaming") { implicit s =>
    val channel = PublishSubject[Int]()
    var received = 0
    var wasCompleted = false

    channel.echoRepeated(1.second).unsafeSubscribeFn(
      new Observer[Int] {
        def onNext(elem: Int): Future[Ack] =
          Future.delayedResult(500.millis) {
            received += elem
            Continue
          }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted = true
      })

    channel.onNext(1)
    assertEquals(received, 0)
    s.tick(500.millis)
    assertEquals(received, 1)

    s.tick(1.second + 500.millis)
    assertEquals(received, 2)
    s.tick(1.second)
    assertEquals(received, 3)
    s.tick(1.second)
    assertEquals(received, 4)
    s.tick(400.millis)
    assertEquals(received, 4)

    channel.onNext(10)
    s.tick(500.millis)
    assertEquals(received, 14)
    s.tick(500.millis)
    assertEquals(received, 14)
    s.tick(1.second)
    assertEquals(received, 24)

    channel.onComplete()
    assertEquals(wasCompleted, true)
  }

  /** Optionally return a sequence of observables
    * that can be canceled.
    */
  override def cancelableObservables() = {
    val sample = Observable.now(1L)
      .delayOnNext(1.second)
      .delayOnComplete(10.seconds)
      .echoRepeated(1.second)

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds)
//      Sample(sample, 1, 1, 1.seconds, 0.seconds),
//      Sample(sample, 2, 2, 2.seconds, 0.seconds)
    )
  }
}
