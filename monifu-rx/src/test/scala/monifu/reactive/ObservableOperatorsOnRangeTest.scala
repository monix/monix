/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.reactive

import java.util.concurrent.{CountDownLatch, TimeUnit}

import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering, Unbounded}
import monifu.reactive.Notification.{OnComplete, OnNext}
import org.scalatest.FunSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Tests involving the Observable operators when used on `Observable.range`.
 */
class ObservableOperatorsOnRangeTest extends FunSpec {
  describe("Observable.range") {
    it("should map") {
      val f = Observable.range(0, 100).map(_ + 2).buffer(100).asFuture
      assert(Await.result(f, 2.seconds) === Some(2 until 102))
    }

    it("should filter") {
      val f1 = Observable.range(0, 100).filter(_ % 2 == 0).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some((0 until 100).filter(_ % 2 == 0)))

      val f2 = Observable.range(0, 100).filter(_ % 2 == 1).buffer(100).asFuture
      assert(Await.result(f2, 2.seconds) === Some((0 until 100).filter(_ % 2 == 1)))
    }

    it("should flatMap") {
      val f = Observable.range(0, 100).flatMap(_ => Observable.range(0, 100)).buffer(100000).asFuture
      assert(Await.result(f, 2.seconds) === Some((0 until 100).flatMap(_ => 0 until 100)))
    }

    it("should mergeMap") {
      val f = Observable.range(0, 100).mergeMap(_ => Observable.range(0, 100)).sum.asFuture
      assert(Await.result(f, 2.seconds) === Some((0 until 100).flatMap(_ => 0 until 100).sum))
    }

    it("should unsafeMerge") {
      val f = Observable.range(0, 100).map(_ => Observable.range(0, 100)).merge(Unbounded, batchSize=0).sum.asFuture
      assert(Await.result(f, 2.seconds) === Some((0 until 100).flatMap(_ => 0 until 100).sum))
    }

    it("should take") {
      val f1 = Observable.range(0, 100).take(50).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(0 until 50))

      val f2 = Observable.range(0, 100).take(150).buffer(200).asFuture
      assert(Await.result(f2, 2.seconds) === Some(0 until 100))

      val f3 = Observable.range(0, 100).take(0).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should takeRight") {
      val f1 = Observable.range(0, 100).takeRight(50).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(50 until 100))

      val f2 = Observable.range(0, 100).takeRight(150).buffer(200).asFuture
      assert(Await.result(f2, 2.seconds) === Some(0 until 100))

      val f3 = Observable.range(0, 100).takeRight(0).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should drop") {
      val f1 = Observable.range(0, 100).drop(50).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(50 until 100))

      val f2 = Observable.range(0, 100).drop(0).buffer(200).asFuture
      assert(Await.result(f2, 2.seconds) === Some(0 until 100))

      val f3 = Observable.range(0, 100).drop(100).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should takeWhile") {
      val f1 = Observable.range(0, 100).takeWhile(_ < 50).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(0 until 50))

      val f2 = Observable.range(0, 100).takeWhile(_ < 150).buffer(200).asFuture
      assert(Await.result(f2, 2.seconds) === Some(0 until 100))

      val f3 = Observable.range(0, 100).takeWhile(_ > 50).asFuture
      assert(Await.result(f3, 2.seconds) === None)
    }

    it("should dropWhile") {
      val f1 = Observable.range(0, 100).dropWhile(_ < 50).buffer(100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(50 until 100))

      val f2 = Observable.range(0, 100).dropWhile(_ < 150).buffer(200).asFuture
      assert(Await.result(f2, 2.seconds) === None)

      val f3 = Observable.range(0, 100).dropWhile(_ > 50).buffer(200).asFuture
      assert(Await.result(f3, 2.seconds) === Some(0 until 100))
    }

    it("should buffer(count)") {
      val f1 = Observable.range(0,100).buffer(50).foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some(Seq(0 until 50, 50 until 100)))

      val f2 = Observable.range(0, 100).buffer(2).foldLeft(Seq.empty[Seq[Int]])(_:+_).asFuture
      assert(Await.result(f2, 2.seconds) === Some((0 until 100).filter(_ % 2 == 0).map(x => Seq(x, x+1))))
    }

    it("should buffer(timestamp)") {
      val f = Observable.range(0,100).buffer(200.millis).asFuture
      assert(Await.result(f, 5.seconds) === Some(0 until 100))
    }

    it("should foldLeft") {
      val f1 = Observable.range(0, 100, 2).foldLeft(0)(_+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some((0 until 100).filter(_ % 2 == 0).sum))

      val f2 = Observable.range(100, 0, -2).foldLeft(0)(_+_).asFuture
      assert(Await.result(f2, 2.seconds) === Some(100.until(0, -2).sum))
    }

    it("should reduce") {
      val f1 = Observable.range(0, 100).reduce(_+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some(0.until(100).sum))

      val f2 = Observable.range(0, 1).reduce(_+_).asFuture
      assert(Await.result(f2, 2.seconds) === None)
    }

    it("should scan") {
      val f = Observable.range(0, 100).scan(2)(_+_).foldLeft(0)(_+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(166850))
    }

    it("should flatScan") {
      val f = Observable.range(0, 100).flatScan(2)((a,b) => Future(a+b)).foldLeft(0)(_+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(166850))
    }

    it("should doOnComplete") {
      val latch = new CountDownLatch(1)
      Observable.range(0,10000).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should doWork") {
      var seen = 0
      val latch = new CountDownLatch(1)
      Observable.range(0, 100).doWork(x => seen += x).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(seen === (0 until 100).sum)
    }

    it("should doOnStart") {
      var seen = 0
      val latch = new CountDownLatch(1)
      Observable.range(1, 100).doOnStart(x => seen = x).doOnComplete(latch.countDown()).subscribe()
      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(seen === 1)
    }

    it("should find") {
      val f1 = Observable.range(0,100).find(_ == 50).asFuture
      assert(Await.result(f1, 2.seconds) === Some(50))

      val f2 = Observable.range(0,100).find(_ == 150).asFuture
      assert(Await.result(f2, 2.seconds) === None)
    }

    it("should exists") {
      val f1 = Observable.range(0,100).exists(_ == 50).asFuture
      assert(Await.result(f1, 2.seconds) === Some(true))

      val f2 = Observable.range(0,100).exists(_ == 150).asFuture
      assert(Await.result(f2, 2.seconds) === Some(false))
    }

    it("should forAll") {
      val f1 = Observable.range(0,100).forAll(x => x >= 0 && x < 100).asFuture
      assert(Await.result(f1, 2.seconds) === Some(true))

      val f2 = Observable.range(0,100).forAll(x => x >= 0 && x < 99).asFuture
      assert(Await.result(f2, 2.seconds) === Some(false))
    }

    it("should complete") {
      val f = Observable.range(0,100).complete.asFuture
      assert(Await.result(f, 2.seconds) === None)
    }

    it("should error") {
      val f = Observable.range(0,100).map(_ => throw new RuntimeException("dummy")).error.asFuture
      val ex = Await.result(f, 2.seconds)
      assert(ex.isDefined && ex.get.getMessage == "dummy", s"exception test failed, $ex returned")
    }

    it("should endWithError") {
      var sum = 0
      val latch = new CountDownLatch(1)

      Observable.range(0,100).endWithError(new RuntimeException("dummy")).subscribe(
        elem => { sum+=elem; Continue },
        exception => {
          assert(exception.getMessage === "dummy")
          latch.countDown()
        }
      )

      assert(latch.await(2, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(sum === 4950)
    }

    it("should +:") {
      val twoElems = 1 +: Observable.range(2,100)
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(1 until 100))
    }

    it("should startWith") {
      val multiple = Observable.range(4,100).startWith(1,2,3)
      val f = multiple.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(1 until 100))
    }

    it("should :+") {
      val twoElems = Observable.range(1, 100) :+ 100
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(1 to 100))
    }

    it("should endWith") {
      val multiple = Observable.range(0, 50).endWith(50 until 100 : _*)
      val f = multiple.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(0 until 100))
    }

    it("should ++") {
      val twoElems = Observable.range(0, 50) ++ Observable.range(50, 100)
      val f = twoElems.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some(0 until 100))
    }

    it("should head") {
      val f = Observable.range(1,100).head.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(Seq(1)))
    }

    it("should tail") {
      val f = Observable.range(1,100).tail.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(2 until 100))
    }

    it("should last") {
      val f = Observable.range(1,100).last.foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 2.seconds) === Some(Seq(99)))
    }

    it("should headOrElse") {
      val f1 = Observable.range(1,100).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f1, 2.seconds) === Some(Seq(1)))

      val f2 = Observable.range(1,100).filter(_ => false).headOrElse(2).foldLeft(Seq.empty[Int])(_:+_).asFuture
      assert(Await.result(f2, 2.seconds) === Some(Seq(2)))
    }

    it("should zip") {
      val f = Observable.range(0,100).zip(Observable.range(0,100)).buffer(200).asFuture
      assert(Await.result(f, 5.seconds) === Some((0 until 100).zip(0 until 100)))
    }

    it("should max") {
      val f = Observable.range(0,100).max.asFuture
      assert(Await.result(f, 5.seconds) === Some(99))
    }

    it("should maxBy") {
      case class Person(age: Int)
      val f = Observable.range(0,100).map(x => Person(x)).maxBy(_.age).asFuture
      assert(Await.result(f, 5.seconds) === Some(Person(99)))
    }

    it("should min") {
      val f = Observable.range(1, 100).min.asFuture
      assert(Await.result(f, 5.seconds) === Some(1))
    }

    it("should minBy") {
      case class Person(age: Int)
      val f = Observable.range(1,100).map(x => Person(x)).minBy(_.age).asFuture
      assert(Await.result(f, 5.seconds) === Some(Person(1)))
    }

    it("should sum") {
      val f = Observable.range(1,100).sum.asFuture
      assert(Await.result(f, 5.seconds) === Some(0.until(100).sum))
    }

    it("should observeOn") {
      val f1 = Observable.range(1,10000).observeOn(global, Unbounded).sum.asFuture
      assert(Await.result(f1, 5.seconds) === Some(1.until(10000).sum))

      val f2 = Observable.range(1,10000).observeOn(global, BackPressured(2)).sum.asFuture
      assert(Await.result(f2, 5.seconds) === Some(1.until(10000).sum))

      val f3 = Observable.range(1,10000).observeOn(global, BackPressured(100000)).sum.asFuture
      assert(Await.result(f3, 5.seconds) === Some(1.until(10000).sum))

      val f4 = Observable.range(1,10000).observeOn(global, OverflowTriggering(10000)).sum.asFuture
      assert(Await.result(f4, 5.seconds) === Some(1.until(10000).sum))

      val f5 = Observable.range(1,10000).observeOn(global, OverflowTriggering(100000)).sum.asFuture
      assert(Await.result(f5, 5.seconds) === Some(1.until(10000).sum))
    }

    it("should distinct") {
      val f1 = Observable.range(1,100).flatMap(x => Observable.range(1,100)).distinct.sum.asFuture
      assert(Await.result(f1, 5.seconds) === Some((1 until 100).sum))

      val f2 = Observable.range(1,100).flatMap(x => Observable.range(1,100)).distinct(_ + 1).sum.asFuture
      assert(Await.result(f2, 5.seconds) === Some((1 until 100).sum))
    }

    it("should distinctUntilChanged") {
      val f1 = (Observable.range(1, 3) ++ Observable.range(1, 100)).distinctUntilChanged.sum.asFuture
      assert(Await.result(f1, 5.seconds) === Some(1 + 2 + 1.until(100).sum))

      val f2 = (Observable.range(1, 3) ++ Observable.range(2, 100)).distinctUntilChanged.sum.asFuture
      assert(Await.result(f2, 5.seconds) === Some(1 + 2 + 3.until(100).sum))
    }

    it("should subscribeOn") {
      val f = Observable.range(1,100).subscribeOn(global).sum.asFuture
      assert(Await.result(f, 5.seconds) === Some((1 until 100).sum))
    }

    it("should materialize") {
      val f = Observable.range(1, 100).materialize.foldLeft(Seq.empty[Notification[Int]])(_:+_).asFuture
      assert(Await.result(f, 5.seconds) === Some((1 until 100).map(OnNext.apply) :+ OnComplete))
    }

    it("should repeat") {
      val f = Observable.range(0,3).repeat.take(1000).sum.asFuture
      assert(Await.result(f, 10.seconds) === Some((0 until 1000).map(_ % 3).sum))
    }
  }
}
