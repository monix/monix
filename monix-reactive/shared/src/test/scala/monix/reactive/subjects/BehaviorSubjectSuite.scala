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
import monix.reactive.Observer
import monix.execution.exceptions.DummyException

import scala.concurrent.Future
import scala.util.Success

object BehaviorSubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = BehaviorSubject[Long](-1)
    Sample(s, expectedElems.lastOption.getOrElse(-1))
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = {
    val s = BehaviorSubject[Long](0)
    Some(Sample(s, expectedElems.sum))
  }


  test("should work synchronously for synchronous subscribers") { implicit s =>
    val subject = BehaviorSubject[Int](10)
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

    subject.onNext(1); s.tick()
    assertEquals(subject.onNext(2), Continue)
    assertEquals(subject.onNext(3), Continue)
    subject.onComplete()

    s.tick()
    assertEquals(received, 160)
    assertEquals(wasCompleted, 10)
  }

  test("should work with asynchronous subscribers") { implicit s =>
    val subject = BehaviorSubject[Int](10)
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
      assertEquals(received, (1 to i).sum * 10 + 100)
    }

    subject.onComplete()
    assertEquals(received, 5 * 11 * 10 + 100)
    assertEquals(wasCompleted, 10)
  }

  test("subscribe after complete should complete immediately") { implicit s =>
    val subject = BehaviorSubject[Int](10)
    var received = 0
    subject.onComplete()

    var wasCompleted = false
    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
    assertEquals(received, 10)
  }

  test("onError should terminate current and future subscribers") { implicit s =>
    val subject = BehaviorSubject[Int](10)
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

    subject.onNext(1); s.tick()
    subject.onError(dummy)

    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { elemsReceived += elem; Continue }
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = ex match {
        case `dummy` => errorsReceived += 1
        case _ => ()
      }
    })

    s.tick()
    assertEquals(elemsReceived, 110)
    assertEquals(errorsReceived, 11)
  }

  test("can stop streaming while connecting") { implicit s =>
    val subject = BehaviorSubject[Int](10)

    val future1 = subject.runAsyncGetFirst
    val future2 = subject.drop(1).runAsyncGetFirst

    s.tick()
    assertEquals(future1.value, Some(Success(Some(10))))
    assertEquals(subject.size, 1)

    assertEquals(subject.onNext(20), Continue)
    assertEquals(future2.value, Some(Success(Some(20))))
    assertEquals(subject.size, 0)
  }

  test("unsubscribe after onComplete") { implicit s =>
    var result: Int = 0
    val subject = BehaviorSubject[Int](0)
    val c = subject.subscribe { e => result = e; Continue }

    subject.onNext(1)
    subject.onComplete()

    s.tick()
    c.cancel()
    assertEquals(result, 1)
  }
}
