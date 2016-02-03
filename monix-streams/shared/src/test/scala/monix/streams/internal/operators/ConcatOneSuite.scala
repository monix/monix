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

import monix.execution.Scheduler
import monix.execution.FutureUtils.ops._
import monix.streams.broadcast.PublishProcessor
import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.Observer
import scala.concurrent.Future
import scala.concurrent.duration._
import Observable.{unit, empty}

object ConcatOneSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.unit(i))

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .flatMap(i => Observable.unit(i))

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).flatMap { i =>
      if (i == sourceCount-1)
        throw ex
      else
        Observable.unit(i)
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def toList[T](o: Observable[T])(implicit s: Scheduler) = {
    o.foldLeft(Vector.empty[T])(_ :+ _).asFuture
      .map(_.getOrElse(Vector.empty))
  }

  test("filter can be expressed in terms of flatMap") { implicit s =>
    val obs1 = Observable.range(0, 100).filter(_ % 2 == 0)
    val obs2 = Observable.range(0, 100).flatMap(x => if (x % 2 == 0) unit(x) else empty)

    val lst1 = toList(obs1)
    val lst2 = toList(obs2)
    s.tick()

    assert(lst1.isCompleted && lst2.isCompleted)
    assertEquals(lst1.value.get, lst2.value.get)
  }

  test("map can be expressed in terms of flatMap") { implicit s =>
    val obs1 = Observable.range(0, 100).map(_ + 10)
    val obs2 = Observable.range(0, 100).flatMap(x => unit(x + 10))

    val lst1 = toList(obs1)
    val lst2 = toList(obs2)
    s.tick()

    assert(lst1.isCompleted && lst2.isCompleted)
    assertEquals(lst1.value.get, lst2.value.get)
  }

  test("should wait the completion of the current, before subscribing to the next") { implicit s =>
    var obs2WasStarted = false
    var received = 0L
    var wasCompleted = false

    val obs1 = PublishProcessor[Long]()
    val obs2 = Observable.range(1, 100).map { x => obs2WasStarted = true; x }

    Observable.from(obs1, obs2).flatten.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        if (elem == 1000)
          Future.delayedResult(1.second)(Continue)
        else
          Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    s.tickOne()
    assertEquals(received, 0)
    obs1.onNext(10)
    assertEquals(received, 10)
    val f = obs1.onNext(1000)
    assertEquals(received, 1010)

    f.onComplete(_ => obs1.onComplete())
    s.tick()
    assert(!obs2WasStarted)

    s.tick(1.second)
    assert(obs2WasStarted)
    assertEquals(received, 1010 + 99 * 50)
    assert(wasCompleted)
  }

  test("should interrupt the streaming on error") { implicit s =>
    var obs1WasStarted = false
    var obs2WasStarted = false
    var wasThrown: Throwable = null

    val sub = PublishProcessor[Long]()
    val obs1 = sub.doOnStart(_ => obs1WasStarted = true)
    val obs2 = Observable.range(1, 100).map { x => obs2WasStarted = true; x }

    Observable.from(obs1, obs2).flatten.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = Continue
      def onError(ex: Throwable) = wasThrown = ex
      def onComplete() = ()
    })

    s.tick()
    sub.onNext(1)
    assert(obs1WasStarted)

    sub.onError(DummyException("dummy"))
    s.tick()

    assertEquals(wasThrown, DummyException("dummy"))
    assert(!obs2WasStarted)
  }
}
