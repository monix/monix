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

import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.Observer

object AsyncSubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = AsyncSubject[Long]()
    Sample(s, expectedElems.lastOption.getOrElse(0))
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = None

  test("while active, keep adding subscribers, but don't emit anything") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = {
        wereCompleted += 1
      }
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)

    subject.onNext(10)
    subject.onNext(20)
    subject.onNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    subject.onComplete()
    subject.onComplete()

    assertEquals(sum, 30 * 3)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribeFn(createObserver)
    assertEquals(sum, 30 * 4)
    assertEquals(wereCompleted, 4)
  }

  test("should interrupt on error without emitting anything") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete() = ()
      def onError(ex: Throwable) = ex match {
        case DummyException("dummy1") =>
          wereCompleted += 1
        case _ =>
          ()
      }
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)

    subject.onNext(10)
    subject.onNext(20)
    subject.onNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    subject.onError(DummyException("dummy1"))
    subject.onError(DummyException("dummy2"))

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribeFn(createObserver)
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
  }

  test("should interrupt when empty") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete() = wereCompleted += 1
      def onError(ex: Throwable) = ()
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)
    subject.unsafeSubscribeFn(createObserver)

    subject.onComplete()

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribeFn(createObserver)
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
  }


  test("subscribe after complete should complete immediately if empty") { implicit s =>
    val subject = AsyncSubject[Int]()
    subject.onComplete()

    var wasCompleted = false
    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = throw new IllegalStateException("onNext")
      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
  }

  test("subscribe after complete should complete immediately if non-empty") { implicit s =>
    val subject = AsyncSubject[Int]()
    subject.onNext(10)
    subject.onComplete()

    var wasCompleted = false
    var received = 0

    subject.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
    assertEquals(received, 10)
  }

  test("onError should terminate current and future subscribers") { implicit s =>
    val subject = AsyncSubject[Int]()
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

    assertEquals(elemsReceived, 0)
    assertEquals(errorsReceived, 11)
  }

  test("unsubscribe after onComplete") { implicit s =>
    var result: Int = 0
    val subject = AsyncSubject[Int]()
    val c = subject.subscribe { e => result = e; Continue }

    subject.onNext(1)
    subject.onComplete()

    s.tick()
    c.cancel()
    assertEquals(result, 1)
  }
}
