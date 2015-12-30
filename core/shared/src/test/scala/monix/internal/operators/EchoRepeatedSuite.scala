/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.internal.operators

import monix.concurrent.extensions._
import monix.Ack.Continue
import monix.{Ack, Observer, Observable}
import monix.subjects.PublishSubject
import scala.concurrent.Future
import scala.concurrent.duration._

object EchoRepeatedSuite extends BaseOperatorSuite {
  def waitFirst = 1.second
  def waitNext = 1.second

  def createObservable(sourceCount: Int) = Some {
    val source = Observable.unsafeCreate[Long](_.onNext(1L))
    val o = source.echoRepeated(1.second).drop(1).take(sourceCount)
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int) = {
    sourceCount
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("should timeout on inactivity and start emitting") { implicit s =>
    val channel = PublishSubject[Int]()
    var received = 0
    var wasCompleted = false

    channel.echoRepeated(1.second)
      .subscribe(new Observer[Int] {
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
      .subscribe(new Observer[Int] {
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
    assertEquals(ack.value.get, Continue.IsSuccess)

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
      .subscribe(new Observer[Int] {
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

    channel.echoRepeated(1.second)
      .subscribe(new Observer[Int] {
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
  }}
