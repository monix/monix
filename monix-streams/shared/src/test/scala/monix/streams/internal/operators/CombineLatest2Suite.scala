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

import monix.execution.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.subjects.PublishSubject
import monix.streams.{Observable, Observer}

import scala.concurrent.duration._

object CombineLatest2Suite extends BaseOperatorSuite {
  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def createObservable(sc: Int) = Some {
    val sourceCount = 10
    val o1 = Observable.now(1)
    val o2 = Observable.range(0, sourceCount)
    val o = Observable.combineLatestWith2(o1, o2) { (x1, x2) => x1 + x2 }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount + 1) / 2

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val unit = Observable.now(1)
    val flawed = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = Observable.combineLatestWith2(unit, flawed) { (x1, x2) => x1 + x2 }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val dummy = DummyException("dummy")
    val o1 = Observable.now(1)
    val o2 = Observable.range(0, sourceCount)
    val o = Observable.combineLatestWith2(o1,o2) { (a1,a2) =>
      if (a2 == sourceCount-1) throw dummy else a1+a2
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = {
      val o1 = Observable.range(0, 10).delayOnNext(1.second)
      val o2 = Observable.range(0, 10).delayOnNext(1.second)
      Observable.combineLatestWith2(o1, o2) { (x1, x2) => x1 + x2 }
    }

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds)
    )
  }

  test("self starts before other and finishes before other") { implicit s =>
    val obs1 = PublishSubject[Int]()
    val obs2 = PublishSubject[Int]()

    var received = (0, 0)
    var wasCompleted = false

    obs1.combineLatestWith(obs2)((o1,o2) => (o1,o2))
      .unsafeSubscribeFn(new Observer[(Int, Int)] {
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

    obs2.combineLatestWith(obs1)((o1,o2) => (o1,o2))
      .unsafeSubscribeFn(new Observer[(Int, Int)] {
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

    obs1.combineLatestWith(obs2.doOnCancel { wasCanceled = true })((o1,o2) => (o1,o2))
      .unsafeSubscribeFn(new Observer[(Int, Int)] {
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

    obs2.doOnCancel { wasCanceled = true }
      .combineLatestWith(obs1)((o1,o2) => (o1,o2))
      .unsafeSubscribeFn(new Observer[(Int, Int)] {
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
}
