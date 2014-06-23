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

import org.scalatest.FunSpec
import scala.language.higherKinds
import scala.concurrent.{Future, Await}
import concurrent.duration._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.concurrent.extensions._
import scala.util.Random


class ObservableSanityTest extends FunSpec {
  describe("Observable.map") {
    it("should work") {
      val f = Observable.from(0 until 100).map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(f, 4.seconds) === Some(1 until 101))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(5, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.repeat(1).doOnComplete(latch.countDown()).map(x => x).take(1000).subscribe()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.takeWhile") {
    it("should work") {
      val f = Observable.from(0 until 100000).takeWhile(_ < 100)
        .map(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(f, 4.seconds) === Some(1 until 101))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.takeWhile(_ => true).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 10000).takeWhile { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.repeat(1).doOnComplete(latch.countDown()).takeWhile(_ => true).take(1000).subscribe()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.dropWhile") {
    it("should work") {
      val f = Observable.from(0 until 200).dropWhile(_ < 100)
        .foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(f, 4.seconds) === Some(100 until 200))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.dropWhile(_ => true).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).dropWhile { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.range(0,1000).doOnComplete(latch.countDown()).dropWhile(_ < 100).take(100).subscribe()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.scan") {
    it("should work") {
      val f = Observable.from(0 until 100).scan(0)(_ + _).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      assert(Await.result(f, 4.seconds) === Some((0 until 100).map(x => (0 to x).sum)))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.scan(0)(_ + _).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).scan(0) { (acc, elem) =>
        if (elem < 5) acc + elem else throw new RuntimeException("test")
      }

      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
    }

    it("should be empty on no elements") {
      val f = Observable.empty[Int].observeOn(global).scan(0)(_+_).asFuture
      assert(Await.result(f, 5.seconds) === None)
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.repeat(1).doOnComplete(latch.countDown()).scan(0)(_+_).take(1000).subscribe()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.filter") {
    it("should work") {
      val obs = Observable.from(1 to 10).filter(_ % 2 == 0).foldLeft(0)(_ + _).asFuture
      assert(Await.result(obs, 4.seconds) === Some((1 to 10).filter(_ % 2 == 0).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.filter(_ % 2 == 0).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).filter { x =>
        if (x < 5) true else throw new RuntimeException("test")
      }

      @volatile var sum = 0
      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
      assert(sum === (0 until 5).sum)
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.repeat(1).doOnComplete(latch.countDown()).filter(_ => true).take(1000).subscribe()
      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.flatMap") {
    it("should work") {
      val result = Observable.from(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => Observable.from(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      assert(Await.result(result, 4.seconds) === Some((0 until 100).sum))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.flatMap(x => Observable.unit(x)).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).flatMap { x =>
        if (x < 50) Observable.unit(x) else throw new RuntimeException("test")
      }

      @volatile var sum = 0
      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = e => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          else
            sum += e
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
      assert(sum === (0 until 50).sum)
    }

    it("should generate elements in order") {
      val obs = Observable.from(0 until 100).filter(_ % 5 == 0)
        .flatMap(x => Observable.from(x until (x + 5)))
        .foldLeft(Seq.empty[Int])(_ :+ _)
        .asFuture

      val result = Await.result(obs, 4.seconds)
      assert(result === Some(0 until 100))
    }

    it("should satisfy source.filter(p) == source.flatMap(x => if (p(x)) unit(x) else empty)") {
      val parent = Observable.from(0 until 1000)
      val res1 = parent.filter(_ % 5 == 0).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => if (x % 5 == 0) Observable.unit(x) else Observable.empty).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should satisfy source.map(f) == source.flatMap(x => unit(x))") {
      val parent = Observable.from(0 until 1000)
      val res1 = parent.map(_ + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.unit(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should satisfy source.map(f).flatten == source.flatMap(f)") {
      val parent = Observable.from(0 until 1000).filter(_ % 2 == 0)
      val res1 = parent.map(x => Observable.from(x until (x + 2))).flatten.foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val res2 = parent.flatMap(x => Observable.from(x until (x + 2))).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(res1, 4.seconds) === Await.result(res2, 4.seconds))
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(1)
      Observable.from(0 until 1000).doOnComplete(latch.countDown())
        .flatMap(x => Observable.repeat(x)).take(1000).subscribe()

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }

    it("should work with Futures") {
      val f = Observable.from(0 until 100).flatMap(x => Future(x + 1)).foldLeft(Seq.empty[Int])(_ :+ _).asFuture
      val result = Await.result(f, 4.seconds)
      assert(result === Some(1 to 100))
    }
  }

  describe("Observable.zip") {
    it("should work") {
      val obs1 = Observable.from(0 until 10).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.from(0 until 10).map(_ * 2).map(_.toLong)

      val zipped = obs1.zip(obs2)

      val finalObs = zipped.foldLeft(Seq.empty[(Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 4.seconds)

      assert(result === Some(0.until(10,2).map(x => (x,x))))
    }

    it("should work in four") {
      val obs1 = Observable.from(0 until 100).filter(_ % 2 == 0).map(_.toLong)
      val obs2 = Observable.from(0 until 1000).map(_ * 2).map(_.toLong)
      val obs3 = Observable.from(0 until 100).map(_ * 2).map(_.toLong)
      val obs4 = Observable.from(0 until 1000).filter(_ % 2 == 0).map(_.toLong)

      val zipped = obs1.zip(obs2).zip(obs3).zip(obs4).map {
        case (((a, b), c), d) => (a, b, c, d)
      }

      val finalObs = zipped.take(10).foldLeft(Seq.empty[(Long,Long,Long,Long)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 4.seconds)

      assert(result === Some(0.until(20,2).map(x => (x,x,x,x))))
    }

    it("should work when length is equal") {
      val obs1 = Observable.from(0 until 100)
      val obs2 = Observable.from(0 until 100)
      val zipped = obs1.zip(obs2)

      val finalObs = zipped.foldLeft(Seq.empty[(Int, Int)])(_ :+ _)
      val result = Await.result(finalObs.asFuture, 4.seconds)

      assert(result === Some((0 until 100).map(x => (x,x))))
    }

    it("should cancel when downstream has canceled") {
      val latch = new CountDownLatch(2)
      val obs1 = Observable.repeat(1).doOnComplete(latch.countDown())
      val obs2 = Observable.repeat(2).doOnComplete(latch.countDown())

      obs1.zip(obs2).take(1000).subscribe()

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
    }
  }

  describe("Observable.from(sequence)") {
    it("should work") {
      val expected = (0 until 5000).filter(_ % 5 == 0).flatMap(x => x until (x + 5)).sum

      val f = Observable.from(0 until 5000).filter(_ % 5 == 0)
        .flatMap(x => Observable.from(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      val result = Await.result(f, 10.seconds)
      assert(result === Some(expected))
    }

    it("should work without overflow") {
      val n = 1000000L
      val sum = n * (n + 1) / 2
      val obs = Observable.from(1 to n.toInt)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 20.seconds)
      assert(result === Some(sum))
    }

    it("should stop if terminated with a stop") {
      val n = 1000000L
      val sum = 101 * 50
      val obs = Observable.from(1 to n.toInt).take(100)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 4.seconds)
      assert(result === Some(sum))
    }
  }

  describe("Observable.from(iterable)") {
    it("should work") {
      val expected = (0 until 5000).filter(_ % 5 == 0).flatMap(x => x until (x + 5)).sum

      val f = Observable.from(0 until 5000).filter(_ % 5 == 0)
        .flatMap(x => Observable.from(x until (x + 5)))
        .foldLeft(0)(_ + _).asFuture

      val result = Await.result(f, 10.seconds)
      assert(result === Some(expected))
    }

    it("should work without overflow") {
      val n = 1000000L
      val sum = n * (n + 1) / 2
      val obs = Observable.from(1 to n.toInt)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 20.seconds)
      assert(result === Some(sum))
    }

    it("should stop if terminated with a stop") {
      val n = 1000000L
      val sum = 101 * 50
      val obs = Observable.from(1 to n.toInt).take(100)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 4.seconds)
      assert(result === Some(sum))
    }
  }

  describe("Observable.repeat") {
    it("should work") {
      val f = Observable.repeat(1).take(5000)
        .foldLeft(0)(_ + _).asFuture

      val result = Await.result(f, 10.seconds)
      assert(result === Some(5000))
    }
  }

  describe("Observable.range") {
    it("should work") {
      val expected = (0 until 5000).filter(_ % 5 == 0).flatMap(x => x until (x + 5)).sum

      val f = Observable.range(0, 5000).filter(_ % 5 == 0)
        .flatMap(x => Observable.range(x, x + 5))
        .foldLeft(0)(_ + _).asFuture

      val result = Await.result(f, 10.seconds)
      assert(result === Some(expected))
    }

    it("should work without overflow") {
      val n = 1000000L
      val sum = n * (n + 1) / 2
      val obs = Observable.range(1, (n + 1).toInt)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 20.seconds)
      assert(result === Some(sum))
    }

    it("should stop if terminated with a stop") {
      val n = 1000000L
      val sum = 101 * 50
      val obs = Observable.range(1, n.toInt).take(100)
      val res = obs.foldLeft(0L)(_ + _).asFuture

      val result = Await.result(res, 4.seconds)
      assert(result === Some(sum))
    }
  }

  describe("Observable.reduce") {
    it("should work") {
      val f = Observable.range(0, 1000)
        .observeOn(global).reduce(_ + _)
        .asFuture

      val r = Await.result(f, 10.seconds)
      assert(r === Some((0 until 1000).sum))
    }

    it("should be empty on zero elements") {
      val f = Observable.empty[Int]
        .observeOn(global).reduce(_ + _)
        .asFuture

      val r = Await.result(f, 10.seconds)
      assert(r === None)
    }

    it("should be empty on one elements") {
      val f = Observable.unit(100)
        .observeOn(global).reduce(_ + _)
        .asFuture

      val r = Await.result(f, 10.seconds)
      assert(r === None)
    }

    it("should be work for 2 elements") {
      val two = Observable.unit(100) ++ Observable.unit(200)
      val f = two.observeOn(global).reduce(_ + _)
        .asFuture

      val r = Await.result(f, 10.seconds)
      assert(r === Some(100 + 200))
    }

    it("should treat exceptions in subscribe implementations (guideline 6.5)") {
      val obs = Observable.create[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      val latch = new CountDownLatch(1)
      @volatile var result = ""

      obs.reduce(_+_).subscribe(
        nextFn = _ => {
          if (result != "")
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(result === "Test exception")
    }

    it("should protect calls to user code (guideline 6.4)") {
      val obs = Observable.from(0 until 100).reduce { (acc, elem) =>
        if (elem < 5) acc + elem else throw new RuntimeException("test")
      }

      @volatile var errorThrow: Throwable = null
      val latch = new CountDownLatch(1)

      obs.map(x => x).subscribe(
        nextFn = _ => {
          if (errorThrow != null)
            throw new IllegalStateException("Should not receive other elements after done")
          Continue
        },
        errorFn = ex => {
          errorThrow = ex
          latch.countDown()
          Cancel
        }
      )

      assert(latch.await(1, TimeUnit.SECONDS), "Latch await failed")
      assert(errorThrow.getMessage === "test")
    }
  }

  describe("Observable#repeat") {
    it("should work") {
      val f = Observable.from(0 until 5).repeat.take(100).foldLeft(Vector.empty[Int])(_:+_).asFuture
      assert(Await.result(f, 10.seconds) === Some((0 until 20).flatMap(_ => 0 until 5)))
    }

    it("should stop on error, test 1") {
      var elems = Vector.empty[Int]
      val latch = new CountDownLatch(2)
      var errorThrow = null : Throwable

      val obs = Observable.from(0 until 50).doOnComplete(latch.countDown())
        .repeat.filter(x => if (x == 10) throw new RuntimeException("dummy") else true)

      obs.subscribe(
        (elem) => { elems = elems :+ elem; Continue },
        (ex) => { errorThrow = ex; latch.countDown() },
        () => throw new IllegalStateException()
      )

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(errorThrow.getMessage == "dummy")
      assert(elems === (0 until 10))
    }

    it("should stop on error, test 2") {
      var elems = Vector.empty[Int]
      val latch = new CountDownLatch(2)
      var errorThrow = null : Throwable

      val obs = Observable.from(0 until 10).doOnComplete(latch.countDown()).repeat

      obs.subscribe(
        (elem) => {
          elems = elems :+ elem
          if (elems.size == 300) throw new RuntimeException("dummy")
          Continue
        },
        (ex) => { errorThrow = ex; latch.countDown() },
        () => throw new IllegalStateException()
      )

      assert(latch.await(10, TimeUnit.SECONDS), "latch.await should have succeeded")
      assert(errorThrow.getMessage == "dummy")
      assert(elems.size === 300)
    }
  }

  describe("Simple Operators") {
    it("should do max") {
      val max = Observable.range(0, 100).mergeMap(x => Observable.range(0, x+1)).max.asFuture
      assert(Await.result(max, 10.seconds) === Some(99))
    }

    it("should do max with a key function") {
      val max = Observable.range(0, 100).mergeMap(x => Observable.range(0, x+1)).maxBy(x => x).asFuture
      assert(Await.result(max, 10.seconds) === Some(99))
    }

    it("should sum") {
      val sum = Observable.range(0, 100).mergeMap(x => Observable.range(0, x+1)).sum.asFuture
      assert(Await.result(sum, 10.seconds) === Some((0 until 100).flatMap(x => (0 until (x+1))).sum))
    }

    it("should emit only distinct items") {
      val obs1 = Observable.from(Seq(1, 1, 2, 1, 3, 1, 4, 5, 6, 6, 5, 7))
        .distinct.foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(obs1, 10.seconds) === Some(Seq(1,2,3,4,5,6,7)))

      val obs2 = Observable.from(Seq(1, 1, 2, 1, 3, 1, 4, 5, 6, 6, 5, 7))
        .distinct(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(obs2, 10.seconds) === Some(Seq(1,2,3,4,5,6,7)))
    }

    it("should do distinctUntilChanged") {
      val obs1 = Observable.from(Seq(1, 1, 2, 1, 3, 1, 4, 5, 6, 6, 5, 7))
        .distinctUntilChanged.foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(obs1, 10.seconds) === Some(Seq(1,2,1,3,1,4,5,6,5,7)))

      val obs2 = Observable.from(Seq(1, 1, 2, 1, 3, 1, 4, 5, 6, 6, 5, 7))
        .distinctUntilChanged(x => x + 1).foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      assert(Await.result(obs2, 10.seconds) === Some(Seq(1,2,1,3,1,4,5,6,5,7)))
    }
  }

  describe("Observable.flatScan") {
    it("should work") {
      def sumUp(x: Long, y: Int) = Future(x + y)
      val obs = Observable.range(0, 1000).flatScan(0L)(sumUp)
        .foldLeft(Seq.empty[Long])(_ :+ _)

      val f = obs.asFuture
      val result = Await.result(f, 10.seconds).get

      assert(result === (0 until 1000).map(x => (0 to x).sum))
    }
  }
}
