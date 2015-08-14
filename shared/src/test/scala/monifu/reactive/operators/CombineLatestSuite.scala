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

import monifu.reactive.Ack.Continue
import monifu.reactive.subjects.PublishSubject
import monifu.reactive.{DummyException, Observable, Observer}
import monifu.concurrent.extensions._
import scala.concurrent.Future
import scala.concurrent.duration._

object CombineLatestSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o1 = Observable.unit(1)
    val o2 = Observable.range(0, sourceCount)

    val o = Observable.combineLatest(o1, o2)
      .map { case (x1, x2) => x1 + x2 }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount + 1) / 2

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val unit = Observable.unit(1)
    val flawed = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = Observable.combineLatest(unit, flawed)
      .map { case (x1, x2) => x1 + x2 }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  test("self starts before other and finishes before other") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var received = (0, 0)
    var wasCompleted = false

    obs1.combineLatest(obs2).unsafeSubscribe(new Observer[(Int, Int)] {
      def onNext(elem: (Int, Int)) = {
        received = elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs1.onNext(1)
    assertEquals(received, (0,0))
    obs1.onNext(2)
    assertEquals(received, (0,0))
    obs2.onNext(3)
    assertEquals(received, (2,3))

    obs1.onComplete()
    assert(!wasCompleted)

    obs2.onNext(4)
    assertEquals(received, (2,4))
    obs2.onComplete()
    assert(wasCompleted)
  }

  test("self starts after other and finishes after other") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var received = (0, 0)
    var wasCompleted = false

    obs2.combineLatest(obs1).unsafeSubscribe(new Observer[(Int, Int)] {
      def onNext(elem: (Int, Int)) = {
        received = elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs1.onNext(1)
    assertEquals(received, (0,0))
    obs1.onNext(2)
    assertEquals(received, (0,0))
    obs2.onNext(3)
    assertEquals(received, (3,2))

    obs1.onComplete()
    assert(!wasCompleted)

    obs2.onNext(4)
    assertEquals(received, (4,2))
    obs2.onComplete()
    assert(wasCompleted)
  }

  test("self signals error and interrupts the stream before it starts") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var wasThrown: Throwable = null
    var wasCanceled = false
    var received = (0,0)

    obs1.combineLatest(obs2.doOnCanceled { wasCanceled = true })
      .unsafeSubscribe(new Observer[(Int, Int)] {
        def onNext(elem: (Int, Int)) = { received = elem; Continue }
        def onError(ex: Throwable) = wasThrown = ex
        def onComplete() = ()
      })

    obs1.onNext(1)
    obs1.onError(DummyException("dummy"))
    assertEquals(wasThrown, DummyException("dummy"))

    obs2.onNext(2); s.tickOne()
    assertEquals(received, (0,0))
    assert(wasCanceled)
  }

  test("other signals error and interrupts the stream before it starts") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var wasThrown: Throwable = null
    var wasCanceled = false
    var received = (0,0)

    obs2.doOnCanceled { wasCanceled = true }.combineLatest(obs1)
      .unsafeSubscribe(new Observer[(Int, Int)] {
        def onNext(elem: (Int, Int)) = { received = elem; Continue }
        def onError(ex: Throwable) = wasThrown = ex
        def onComplete() = ()
      })

    obs1.onNext(1)
    obs1.onError(DummyException("dummy"))
    assertEquals(wasThrown, DummyException("dummy"))

    obs2.onNext(2); s.tickOne()
    assertEquals(received, (0,0))
    assert(wasCanceled)
  }

  test("back-pressure self.onError") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var wasThrown: Throwable = null

    obs1.combineLatest(obs2).unsafeSubscribe(new Observer[(Int, Int)] {
      def onNext(elem: (Int, Int)) =
        Future.delayedResult(1.second)(Continue)
      def onComplete() = ()
      def onError(ex: Throwable) =
        wasThrown = ex
    })

    obs1.onNext(1)
    obs2.onNext(2)
    obs1.onError(DummyException("dummy"))

    s.tick()
    assertEquals(wasThrown, null)

    s.tick(1.second)
    assertEquals(wasThrown, DummyException("dummy"))
  }

  test("back-pressure other.onError") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var wasThrown: Throwable = null

    obs1.combineLatest(obs2).unsafeSubscribe(new Observer[(Int, Int)] {
      def onNext(elem: (Int, Int)) =
        Future.delayedResult(1.second)(Continue)
      def onComplete() = ()
      def onError(ex: Throwable) =
        wasThrown = ex
    })

    obs1.onNext(1)
    obs2.onNext(2)
    obs2.onError(DummyException("dummy"))

    s.tick()
    assertEquals(wasThrown, null)

    s.tick(1.second)
    assertEquals(wasThrown, DummyException("dummy"))
  }
}
