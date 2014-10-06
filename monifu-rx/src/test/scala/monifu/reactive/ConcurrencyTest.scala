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
 
package monifu.reactive

import monifu.concurrent.extensions._
import org.scalatest.FunSpec
import monifu.concurrent.Implicits.globalScheduler
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random


class ConcurrencyTest extends FunSpec {
  describe("Observable.take") {
    it("should work asynchronously") {
      val obs = Observable.range(0, 10000)
        .subscribeOn(globalScheduler)
        .asyncBoundary().take(9000)
        .asyncBoundary()
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 20.seconds)
      assert(r === Some(0 until 9000))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.range(0, 10000)
        .asyncBoundary()
        .take(9000)
        .flatMap(x => Observable.range(x, x + 100).take(5))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some((0 until 9000).flatMap(x => x until (x + 5)).sum))
    }
  }

  describe("Observable.takeWhile") {
    it("should work asynchronously") {
      val obs = Observable.range(0, 10000)
        .subscribeOn(globalScheduler)
        .asyncBoundary().takeWhile(_ < 9000)
        .asyncBoundary()
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(0 until 9000))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.range(0, 10000)
        .asyncBoundary()
        .takeWhile(_ < 9000)
        .flatMap(x => Observable.range(x, x + 100).takeWhile(_ < x + 5))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some((0 until 9000).flatMap(x => x until (x + 5)).sum))
    }
  }

  describe("Observable.drop") {
    it("should work asynchronously") {
      val obs = Observable.range(10000, 0, -1)
        .subscribeOn(globalScheduler)
        .asyncBoundary().drop(9900)
        .asyncBoundary()
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1)))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.from(10000.until(0, -1))
        .asyncBoundary()
        .drop(9900)
        .flatMap(x => Observable.range(x, x + 100).drop(90))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1).flatMap(x => x.until(x + 100).drop(90)).sum))
    }
  }

  describe("Observable.dropWhile") {
    it("should work asynchronously") {
      val obs = Observable.range(10000, 0, -1)
        .subscribeOn(globalScheduler)
        .asyncBoundary().dropWhile(_ > 100)
        .asyncBoundary()
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1)))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.from(10000.until(0, -1))
        .asyncBoundary()
        .dropWhile(_ > 100)
        .flatMap(x => Observable.range(x, x + 100).dropWhile(_ < x + 90))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1).flatMap(x => x.until(x + 100).drop(90)).sum))
    }
  }

  describe("Observable.interval") {
    it("should not have concurrency problems") {
      val f = Observable.intervalWithFixedDelay(1.millisecond).asyncBoundary()
        .take(100)
        .foldLeft(Seq.empty[Long])(_:+_)
        .asFuture

      val list = Await.result(f, 40.seconds)
      assert(list === Some(0 until 100))
    }
  }

  describe("Observable.fromIterable") {
    it("should not have concurrency problems") {
      val f = Observable.from(1 until 1000).asyncBoundary()
        .map(_.toLong)
        .take(100)
        .foldLeft(Seq.empty[Long])(_:+_)
        .asFuture

      val list = Await.result(f, 10.seconds)
      assert(list === Some(1 to 100))
    }
  }

  describe("Observable.takeRight") {
    it("should not have concurrency problems") {
      val f = Observable.range(0, 10000).asyncBoundary().takeRight(100)
        .foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      val r = Await.result(f, 20.seconds)
      assert(r === Some(9900 until 10000))
    }
  }

  describe("Observable.flatScan") {
    it("should not have concurrency problems") {
      def sumUp(x: Long, y: Int) =
        Future.delayedResult(Random.nextInt(3).millisecond)(x + y)

      val obs = Observable.range(0, 1000).flatScan(0L)(sumUp)
        .foldLeft(Seq.empty[Long])(_ :+ _)

      val f = obs.asFuture
      val result = Await.result(f, 30.seconds).get

      assert(result === (0 until 1000).map(x => (0 to x).sum))
    }
  }
}
