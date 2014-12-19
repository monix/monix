/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.{OverflowTriggering, BackPressured, Unbounded}
import monifu.reactive.Notification.OnNext
import monifu.reactive.{Notification, Observable}
import monifu.reactive.subjects.BehaviorSubject
import concurrent.duration._
import scala.concurrent.Future
import scala.scalajs.test.JasmineTest
import monifu.concurrent.Implicits._

/**
 * Tests involving the Observable operators when used on `Observable.unit`.
 */
object ObservableOperatorsOnUnitTest extends JasmineTest {
  beforeEach {
    jasmine.Clock.useMock()
  }

  describe("Observable.unit") {
    it("should map") {
      expectInt(Observable.unit(1).map(_ + 2).asFuture, 3, -1)
    }

    it("should filter") {
      expectInt(Observable.unit(1).filter(_ % 2 == 0).asFuture, -1, -1)

      expectInt(Observable.unit(1).filter(_ % 2 == 1).asFuture, 1, -1)
    }

    it("should flatMap") {
      expectInt(Observable.unit(1).flatMap(x => Observable.unit(x + 2)).asFuture, 3, -1)
    }

    it("should mergeMap") {
      expectInt(Observable.unit(1).mergeMap(x => Observable.unit(x + 2)).asFuture, 3, -1)
    }

    it("should unsafeMerge") {
      expectInt(Observable.unit(1).map(x => Observable.unit(x + 2)).merge(bufferPolicy=Unbounded).asFuture, 3, -1)
    }

    it("should take") {
      expectInt(Observable.unit(1).take(1).asFuture, 1, -1)

      expectInt(Observable.unit(1).take(10).asFuture, 1, -1)

      expectInt(Observable.unit(1).take(0).asFuture, -1, -1)
    }

    it("should takeRight") {
      expectInt(Observable.unit(1).takeRight(1).asFuture, 1, -1)

      expectInt(Observable.unit(1).takeRight(10).asFuture, 1, -1)

      expectInt(Observable.unit(1).takeRight(0).asFuture, -1, -1)
    }

    it("should drop") {
      expectInt(Observable.unit(1).drop(0).asFuture, 1, -1)

      expectInt(Observable.unit(1).drop(1).asFuture, -1, -1)

      expectInt(Observable.unit(1).drop(10).asFuture, -1, -1)
    }

    it("should takeWhile") {
      expectInt(Observable.unit(1).takeWhile(_ % 2 == 1).asFuture, 1, -1)

      expectInt(Observable.unit(1).takeWhile(_ % 2 == 0).asFuture, -1, -1)
    }

    it("should takeUntilOtherEmits") {
      /* Second observable produces a value before the first one. */
      val first = Observable.timerOneTime(100.millis, 1)
      val second = Observable.unit(2)
      val obs1 = first.takeUntilOtherEmits(second).asFuture

      jasmine.Clock.tick(101)
      expectNone(obs1)

      /* First observable produces a value before the second one. */
      val first2 = Observable.timerOneTime(50.millis, 1)
      val second2 = Observable.timerOneTime(100.millis, 2)
      val obs2 = first2.takeUntilOtherEmits(second2).asFuture

      jasmine.Clock.tick(101)
      expectInt(obs2, 1, -1)

      /* Repeatedly emit an element every 1 ms on the first observable.
       * It is supposed to be interrupted after 5 ms as the second
       * observable produces an element.
       */
      val first3 = Observable.timerRepeated(100.millis, 1.milli, 1)
      val second3 = Observable.timerOneTime(105.millis, 2)
      val obs3 = first3.takeUntilOtherEmits(second3).reduce(_ + _).asFuture

      jasmine.Clock.tick(100)
      jasmine.Clock.tick(1)
      jasmine.Clock.tick(1)
      jasmine.Clock.tick(1)
      jasmine.Clock.tick(1)
      jasmine.Clock.tick(1)
      expectInt(obs3, 5, -1)
    }

    it("should dropWhile") {
      expectInt(Observable.unit(1).dropWhile(_ == 2).asFuture, 1, -1)

      expectInt(Observable.unit(1).dropWhile(_ == 1).asFuture, -1, -1)
    }

    it("should buffer(count)") {
      expectSeqOfInt(Observable.unit(1).buffer(1).asFuture, Seq(1), Seq.empty)

      expectSeqOfInt(Observable.unit(1).buffer(2).asFuture, Seq(1), Seq.empty)
    }

    it("should buffer(timespan)") {
      expectSeqOfInt(Observable.unit(1).bufferTimed(100.millis).asFuture, Seq(1), Seq.empty)
    }

    it("should foldLeft") {
      expectInt(Observable.unit(1).foldLeft(1)(_+_).asFuture, 2, -1)
    }

    it("should reduce") {
      expectInt(Observable.unit(1).reduce(_+_).asFuture, -1, -1)
    }

    it("should scan") {
      expectInt(Observable.unit(1).scan(2)(_+_).foldLeft(0)(_+_).asFuture, 3, -1)
    }

    it("should flatScan") {
      expectInt(Observable.unit(1).flatScan(2)((a,b) => Future(a+b)).foldLeft(0)(_+_).asFuture, 3, -1)
    }

    it("should doOnComplete") {
      var completed = false
      Observable.unit(1).doOnComplete{completed = true}.subscribe()
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
    }

    it("should doWork") {
      var seen = 0
      var completed = false
      Observable.unit(1).doWork(x => seen = x).doOnComplete{completed=true}.subscribe()
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(1)
    }

    it("should doOnStart") {
      var seen = 0
      var completed = false
      Observable.unit(1).doOnStart(x => seen = x).doOnComplete{completed=true}.subscribe()
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(1)
    }

    it("should find") {
      expectInt(Observable.unit(1).find(_ == 1).asFuture, 1, -1)

      expectInt(Observable.unit(1).find(_ == 2).asFuture, -1, -1)
    }

    it("should exists") {
      expectBoolean(Observable.unit(1).exists(_ == 1).asFuture, expected = true, default = false)

      expectBoolean(Observable.unit(1).exists(_ == 2).asFuture, expected = false, default = true)
    }

    it("should forAll") {
      expectBoolean(Observable.unit(1).forAll(_ == 1).asFuture, expected = true, default = false)

      expectBoolean(Observable.unit(1).forAll(_ == 2).asFuture, expected = false, default = true)
    }

    it("should complete") {
      expectInt(Observable.unit(1).complete.asFuture, -1, -1)
    }

    it("should error") {
      val f = Observable.unit(1).map(_ => throw new RuntimeException("dummy")).error.asFuture
      jasmine.Clock.tick(1)
      expect(f.value.get.get.get.getMessage).toBe("dummy")
    }

    it("should endWithError") {
      var seen = 0
      var completed = false
      Observable.unit(1).endWithError(new RuntimeException("dummy")).doOnComplete{completed=true}.subscribe(
        elem => { seen = elem; Continue },
        exception => {
          expect(exception.getMessage).toBe("dummy")
        }
      )
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(1)
    }

    it("should +:") {
      expectSeqOfInt((1 +: Observable.unit(2)).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1,2), Seq.empty)
    }

    it("should startWith") {
      expectSeqOfInt(Observable.unit(4).startWith(1,2,3).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1,2,3,4), Seq.empty)
    }

    it("should :+") {
      expectSeqOfInt((Observable.unit(1) :+ 2).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1,2), Seq.empty)
    }

    it("should endWith") {
      expectSeqOfInt(Observable.unit(1).endWith(2, 3, 4).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1,2,3,4), Seq.empty)
    }

    it("should ++") {
      expectSeqOfInt((Observable.unit(1) ++ Observable.unit(2)).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1,2), Seq.empty)
    }

    it("should head") {
      expectSeqOfInt(Observable.unit(1).head.foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1), Seq.empty)
    }

    it("should tail") {
      expectSeqOfInt(Observable.unit(1).tail.foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq.empty, Seq(0))
    }

    it("should last") {
      expectSeqOfInt(Observable.unit(1).last.foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1), Seq.empty)
    }

    it("should headOrElse") {
      expectSeqOfInt(Observable.unit(1).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(1), Seq.empty)

      expectSeqOfInt(Observable.unit(1).filter(_ => false).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture, Seq(2), Seq.empty)
    }

    it("should zip") {
      val f = Observable.unit(1).zip(Observable.unit(2)).asFuture
      jasmine.Clock.tick(1)
      val actual = f.value.get.get.get
      expect(actual._1).toBe(1)
      expect(actual._2).toBe(2)
    }

    it("should max") {
      expectInt(Observable.unit(1).max.asFuture, 1, -1)
    }

    it("should maxBy") {
      case class Person(age: Int)
      val f = Observable.unit(Person(32)).maxBy(_.age).asFuture
      jasmine.Clock.tick(1)
      val actual = f.value.get.get.get
      val expected = Person(32)
      expect(actual.hashCode()).toBe(expected.hashCode())
      expect(actual.age).toBe(expected.age)
    }

    it("should min") {
      expectInt(Observable.unit(1).min.asFuture, 1, -1)
    }

    it("should minBy") {
      case class Person(age: Int)
      val f = Observable.unit(Person(32)).minBy(_.age).asFuture
      jasmine.Clock.tick(1)
      val actual = f.value.get.get.get
      val expected = Person(32)
      expect(actual.hashCode()).toBe(expected.hashCode())
      expect(actual.age).toBe(expected.age)
    }

    it("should sum") {
      expectInt(Observable.unit(1).sum.asFuture, 1, -1)
    }

    it("should asyncBoundary") {
      expectInt(Observable.unit(1).asyncBoundary(Unbounded).sum.asFuture, 1, -1)

      expectInt(Observable.unit(1).asyncBoundary(BackPressured(2)).sum.asFuture, 1, -1)

      expectInt(Observable.unit(1).asyncBoundary(BackPressured(1000)).sum.asFuture, 1, -1)

      expectInt(Observable.unit(1).asyncBoundary(OverflowTriggering(2)).sum.asFuture, 1, -1)

      expectInt(Observable.unit(1).asyncBoundary(OverflowTriggering(1000)).sum.asFuture, 1, -1)
    }

    it("should distinct") {
      expectInt(Observable.unit(1).distinct.asFuture, 1, -1)

      expectInt(Observable.unit(1).distinct(_ + 1).asFuture, 1, -1)
    }

    it("should distinctUntilChanged") {
      expectInt(Observable.unit(1).distinctUntilChanged.asFuture, 1, -1)

      expectInt(Observable.unit(1).distinctUntilChanged(x => x + 1).asFuture, 1, -1)
    }

    it("should subscribeOn") {
      expectInt(Observable.unit(1).subscribeOn(globalScheduler).asFuture, 1, -1)
    }

    it("should materialize") {
      var completed = false
      val f = Observable.unit(1).materialize.foldLeft(Seq.empty[Notification[Int]])(_:+_).doOnComplete{completed=true}.asFuture
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      val result = f.value.get.get.get
      expect(result.size).toBe(2)
      expect(result(0).asInstanceOf[OnNext[Int]].elem).toBe(1)
    }

    it("should repeat") {
      expectInt(Observable.unit(1).repeat.take(1000).sum.asFuture, 1000, 1)
    }

    it("should multicast") {
      for (_ <- 0 until 1000) {
        var seen = 0
        var completed = false

        val connectable = Observable.unit(1).multicast(BehaviorSubject(1))
        connectable.doOnComplete{completed = true}.sum.foreach(x => seen = x)

        expect(completed).toBe(false)
        expect(seen).toBe(0)

        connectable.connect()
        jasmine.Clock.tick(1)
        expect(completed).toBe(true)
        expect(seen).toBe(2)
      }
    }

    it("should publish") {
      var seen = 0
      var completed = false

      val connectable = Observable.unit(1).publish()
      connectable.doOnComplete{completed = true}.sum.foreach(x => seen = x)

      expect(completed).toBe(false)
      expect(seen).toBe(0)

      connectable.connect()
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(1)
    }

    it("should behavior") {
      var seen = 0
      var completed = false

      val connectable = Observable.unit(1).behavior(1)
      connectable.doOnComplete{completed = true}.sum.foreach(x => seen = x)

      expect(seen).toBe(0)
      expect(completed).toBe(false)

      connectable.connect()
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(2)
    }

    it("should replay") {
      var seen = 0
      var completed = false

      val connectable = Observable.unit(1).replay()
      connectable.connect()

      connectable.doOnComplete{completed = true}.sum.foreach(x => seen = x)
      jasmine.Clock.tick(1)
      expect(completed).toBe(true)
      expect(seen).toBe(1)
    }

    it("should check empty") {
      expectBoolean(Observable.empty.isEmpty.asFuture, expected = true, default = false)
      expectBoolean(Observable.unit(1).isEmpty.asFuture, expected = false, default = true)
    }

    it("should check non-empty") {
      expectBoolean(Observable.empty.nonEmpty.asFuture, expected = false, default = true)
      expectBoolean(Observable.unit(1).nonEmpty.asFuture, expected = true, default = false)
    }
  }

  def expectInt(f: Future[Option[Int]], expected: Int, default: Int) {
    jasmine.Clock.tick(1)
    expect(f.value.get.get.getOrElse(default)).toBe(expected)
  }

  def expectSeqOfInt(f: Future[Option[Seq[Int]]], expected: Seq[Int], default: Seq[Int]) {
    jasmine.Clock.tick(1)
    val result = f.value.get.get.getOrElse(default)
    expect(result.size).toBe(expected.size)
    val expectedInt = expected.iterator
    for (actualInt <- result) {
      expect(actualInt).toBe(expectedInt.next())
    }
  }

  def expectBoolean(f: Future[Option[Boolean]], expected: Boolean, default: Boolean) {
    jasmine.Clock.tick(1)
    expect(f.value.get.get.getOrElse(default)).toBe(expected)
  }

  def expectNone[T](f: Future[Option[T]]) {
    jasmine.Clock.tick(1)
    expect(f.value.get.get.isDefined).toBe(false)
  }
}
