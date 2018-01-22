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
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable.{empty, now}
import monix.execution.exceptions.DummyException
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random}

object ConcatOneSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.now(i))

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .flatMap(i => Observable.now(i))

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
        Observable.now(i)
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def toList[A](o: Observable[A])(implicit s: Scheduler) = {
    o.foldLeftF(Vector.empty[A])(_ :+ _).runAsyncGetLast
      .map(_.getOrElse(Vector.empty))
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 =  Observable.range(1, 100)
      .flatMap(x => Observable.now(x).delaySubscription(1.second))
    val sample2 = Observable.range(0, 100).delayOnNext(1.second)
      .flatMap(x => Observable.now(x).delaySubscription(1.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds)
    )
  }

  test("should work synchronously for synchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var total = 0L

    createObservable(sourceCount) match {
      case Some(Sample(obs, count, sum, _, _)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          private[this] var sum = 0L

          def onNext(elem: Long): Ack = {
            received += 1
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = total = sum
        })

        assertEquals(received, count)
        assertEquals(total, sum)
    }
  }

  test("filter can be expressed in terms of flatMap") { implicit s =>
    val obs1 = Observable.range(0, 100).filter(_ % 2 == 0)
    val obs2 = Observable.range(0, 100).flatMap(x => if (x % 2 == 0) now(x) else empty)

    val lst1 = toList(obs1)
    val lst2 = toList(obs2)
    s.tick()

    assert(lst1.isCompleted && lst2.isCompleted)
    assertEquals(lst1.value.get, lst2.value.get)
  }

  test("map can be expressed in terms of flatMap") { implicit s =>
    val obs1 = Observable.range(0, 100).map(_ + 10)
    val obs2 = Observable.range(0, 100).flatMap(x => now(x + 10))

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

    val obs1 = PublishSubject[Long]()
    val obs2 = Observable.range(1, 100).map { x => obs2WasStarted = true; x }

    Observable.fromIterable(Seq(obs1, obs2)).flatten.unsafeSubscribeFn(new Observer[Long] {
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

    val sub = PublishSubject[Long]()
    val obs1 = sub.doOnStart(_ => obs1WasStarted = true)
    val obs2 = Observable.range(1, 100).map { x => obs2WasStarted = true; x }

    Observable.fromIterable(Seq(obs1, obs2)).flatten.unsafeSubscribeFn(new Observer[Long] {
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

  test("should not break the contract on user-level error #2") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val source = Observable.now(1L).endWithError(dummy1)
    val obs: Observable[Long] = source.flatMap { _ => Observable.raiseError(dummy2) }

    var thrownError: Throwable = null
    var received = 0
    var onCompleteReceived = false
    var onErrorReceived = 0

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Ack = {
        received += 1
        Continue
      }

      def onComplete(): Unit =
        onCompleteReceived = true
      def onError(ex: Throwable): Unit = {
        onErrorReceived += 1
        thrownError = ex
      }
    })

    s.tick()
    assertEquals(received, 0)
    assertEquals(thrownError, dummy2)
    assert(!onCompleteReceived, "!onCompleteReceived")
    assertEquals(onErrorReceived, 1)
  }

  test("should not break the contract on user-level error #3") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val source = Observable.now(1L).endWithError(dummy1)
    val obs: Observable[Long] = source.flatMap { _ =>
      Observable.raiseError(dummy2).executeAsync
    }

    var thrownError: Throwable = null
    var received = 0
    var onCompleteReceived = false
    var onErrorReceived = 0

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Ack = {
        received += 1
        Continue
      }

      def onComplete(): Unit =
        onCompleteReceived = true
      def onError(ex: Throwable): Unit = {
        onErrorReceived += 1
        thrownError = ex
      }
    })

    s.tick()
    assertEquals(received, 0)
    assertEquals(thrownError, dummy2)
    assert(!onCompleteReceived, "!onCompleteReceived")
    assertEquals(onErrorReceived, 1)
  }

  test("exceptions can be triggered synchronously by throw") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Observable.now(1L).flatMap(_ => throw dummy)

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }

  test("exceptions can be triggered synchronously through raiseError") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Observable.now(1L).flatMap(_ => Observable.raiseError(dummy))

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }
}
