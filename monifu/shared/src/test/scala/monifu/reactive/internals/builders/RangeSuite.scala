/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.internals.builders

import minitest.TestSuite
import monifu.concurrent.extensions._
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.internals.builders.RepeatOneSuite._
import monifu.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._


object RangeSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty, 
      "TestScheduler should not have pending tasks left")
  }

  test("should do increments and synchronous observers") { implicit s =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(1, 10, 1).onSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(sum, 0)
    assertEquals(wasCompleted, false)

    assert(s.tickOne())
    assertEquals(sum, 45)
    assertEquals(wasCompleted, true)
  }

  test("should do decrements and synchronous observers") { implicit s =>
    var wasCompleted = false
    var sum = 0L

    Observable.range(9, 0, -1).onSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assertEquals(sum, 0)
    assertEquals(wasCompleted, false)

    assert(s.tickOne())
    assertEquals(sum, 45)
    assertEquals(wasCompleted, true)
  }

  test("should do back-pressure") { implicit s =>
    var wasCompleted = false
    var received = 0L
    var sum = 0L

    Observable.range(1, 5).onSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        Future.delayedResult(1.second) {
          sum += elem
          Continue
        }
      }

      def onComplete(): Unit = wasCompleted = true
      def onError(ex: Throwable): Unit = ()
    })

    assert(!wasCompleted)

    s.tick(); assertEquals(sum, 0); assertEquals(received, 1)
    s.tick(1.second); assertEquals(sum, 1); assertEquals(received, 3)
    s.tick(1.second); assertEquals(sum, 3); assertEquals(received, 6)
    s.tick(1.second); assertEquals(sum, 6); assertEquals(received, 10)

    assert(!wasCompleted)
    s.tick(1.second); assertEquals(sum, 10); assertEquals(received, 10)
    assert(wasCompleted)
  }

  test("should throw if step is zero") { implicit s =>
    intercept[IllegalArgumentException] {
      Observable.range(0, 10, 0)
    }
  }

  test("should do synchronous execution in batches") { implicit s =>
    var received = 0
    Observable.range(0, s.env.batchSize * 2).map(_ => 1)
      .subscribe { x => received += 1; Continue }

    s.tickOne()
    assertEquals(received, s.env.batchSize)
    s.tickOne()
    assertEquals(received, s.env.batchSize * 2)
    s.tickOne()
  }
}
