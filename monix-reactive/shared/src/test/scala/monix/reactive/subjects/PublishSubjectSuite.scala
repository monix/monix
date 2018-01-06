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

package monix.reactive.subjects

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future
import scala.util.Success

object PublishSubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = PublishSubject[Long]()
    Sample(s, 0)
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = {
    val s = PublishSubject[Long]()
    Some(Sample(s, expectedElems.sum))
  }

  test("issue #50") { implicit s =>
    val p = PublishSubject[Int]()
    var received = 0

    Observable.merge(p).subscribe(new Observer[Int] {
      def onNext(elem: Int) = {
        received += elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = ()
    })

    s.tick() // merge operation happens async

    val f = p.onNext(1)
    assertEquals(f.value, Some(Success(Continue)))
    assertEquals(received, 0)

    s.tick()
    assertEquals(received, 1)
  }

  test("should emit from the point of subscription forward") { implicit s =>
    val subject = PublishSubject[Int]()
    assertEquals(subject.onNext(1), Continue)
    assertEquals(subject.onNext(2), Continue)
    assertEquals(subject.onNext(3), Continue)

    var received = 0
    var wasCompleted = false
    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        received += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assertEquals(subject.onNext(4), Continue)
    assertEquals(subject.onNext(5), Continue)
    assertEquals(subject.onNext(6), Continue)
    subject.onComplete()

    assertEquals(received, 15)
    assert(wasCompleted)
  }

  test("should work synchronously for synchronous subscribers") { implicit s =>
    val subject = PublishSubject[Int]()
    var received = 0
    var wasCompleted = 0

    for (i <- 0 until 10)
      subject.unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assertEquals(subject.onNext(1), Continue)
    assertEquals(subject.onNext(2), Continue)
    assertEquals(subject.onNext(3), Continue)
    subject.onComplete()

    assertEquals(received, 60)
    assertEquals(wasCompleted, 10)
  }

  test("should work with asynchronous subscribers") { implicit s =>
    val subject = PublishSubject[Int]()
    var received = 0
    var wasCompleted = 0

    for (i <- 0 until 10)
      subject.unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = Future {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    for (i <- 1 to 10) {
      val ack = subject.onNext(i)
      assert(!ack.isCompleted)
      s.tick()
      assert(ack.isCompleted)
      assertEquals(received, (1 to i).sum * 10)
    }

    subject.onComplete()
    assertEquals(received, 5 * 11 * 10)
    assertEquals(wasCompleted, 10)
  }

  test("subscribe after complete should complete immediately") { implicit s =>
    val subject = PublishSubject[Int]()
    subject.onComplete()

    var wasCompleted = false
    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = throw new IllegalStateException("onNext")
      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
  }

  test("onError should terminate current and future subscribers") { implicit s =>
    val subject = PublishSubject[Int]()
    val dummy = DummyException("dummy")
    var elemsReceived = 0
    var errorsReceived = 0

    for (_ <- 0 until 10)
      subject.unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = { elemsReceived += elem; Continue }
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = ex match {
          case `dummy` => errorsReceived += 1
          case _ => ()
        }
      })

    subject.onNext(1)
    subject.onError(dummy)

    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = throw new IllegalStateException("onNext")
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = ex match {
        case `dummy` => errorsReceived += 1
        case _ => ()
      }
    })

    assertEquals(elemsReceived, 10)
    assertEquals(errorsReceived, 11)
  }

  test("unsubscribe after onComplete") { implicit s =>
    var result: Int = 0
    val subject = PublishSubject[Int]()
    val c = subject.subscribe { e => result = e; Continue }

    subject.onNext(1)
    subject.onComplete()

    s.tick()
    c.cancel()
    assertEquals(result, 1)
  }
}