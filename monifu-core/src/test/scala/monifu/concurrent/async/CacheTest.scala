package monifu.concurrent.async

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.FunSuite
import concurrent.duration._


class CacheTest extends FunSuite {
  test("get(), set()") {
    withInstance { cache =>
      assert(cache.get[String]("hello") === None)

      cache.set("hello", "world")
      assert(cache.get[String]("hello") === Some("world"))
    }
  }

  test("add()") {
    withInstance { cache =>
      assert(cache.get[String]("hello") === None)

      assert(cache.add("hello", "world"), "value should be added successfully")
      assert(cache.get[String]("hello") === Some("world"))

      assert(!cache.add("hello", "world version 2"), "value already exists")
      assert(cache.get[String]("hello") === Some("world"))

      cache.set("hello", "world version 2")
      assert(cache.get[String]("hello") === Some("world version 2"))
    }
  }

  test("getOrElse()") {
    withInstance { cache =>
      assert(cache.getOrElse("hello", "default") === "default")
      cache.set("hello", "world")
      assert(cache.getOrElse("hello", "world") === "world")
    }
  }

  test("delete()") {
    withInstance { cache =>
      assert(cache.get[String]("hello") === None)
      cache.set("hello", "world")
      assert(cache.get[String]("hello") === Some("world"))

      assert(cache.delete("hello"), "item should be deleted")
      assert(cache.get[String]("hello") === None)
      assert(!cache.delete("hello"), "item should not be there anymore")
    }
  }

  test("cachedFuture()") {
    withInstance { cache =>
      assert(cache.get[String]("hello") === None)

      def future() = cache.cachedFuture("hello", 1.minute) {
        Future {
          Thread.sleep(1000)
          "world"
        }
      }

      for (idx <- 0 until 10000)
        assert(Await.result(future(), 4.seconds) === "world")
    }
  }

  test("compareAndSet()") {
    withInstance { cache =>
      assert(cache.compareAndSet("hello", None, "world"), "first CAS should succeed")
      assert(cache.compareAndSet("hello", Some("world"), "world updated"), "second CAS should succeed")
      assert(cache.get[String]("hello") === Some("world updated"))
      assert(!cache.compareAndSet("hello", Some("bollocks"), "world"), "third CAS should fail")
    }
  }

  test("transformAndGet() (with expiry)") {
    withInstance { cache =>
      def incr() = cache.transformAndGet[Int]("number", 1.second) {
        case Some(nr) => nr + 1
        case None => 0
      }

      for (idx <- 0 until 100)
        assert(incr() === idx)

      Thread.sleep(1000)
      assert(incr() === 0)
    }
  }

  test("getAndTransform() (with expiry)") {
    withInstance { cache =>
      def incr() = cache.getAndTransform[Int]("number", 1.second) {
        case Some(nr) => nr + 1
        case None => 1
      }

      for (idx <- 0 until 100)
        if (idx == 0)
          assert(incr() === None)
        else
          assert(incr() === Some(idx))

      Thread.sleep(1000)
      assert(incr() === None)
    }
  }

  test("add() expiration") {
    withInstance { cache =>
      assert(cache.add("hello", "world", 1.second), "add() should work")
      assert(cache.get[String]("hello") === Some("world"))

      Thread.sleep(1000)
      assert(cache.get[String]("hello") === None)
    }
  }

  test("set() expiration") {
    withInstance { cache =>
      cache.set("hello", "world", 1.second)
      assert(cache.get[String]("hello") === Some("world"))

      Thread.sleep(1000)
      assert(cache.get[String]("hello") === None)
    }
  }

  test("delete() expiration") {
    withInstance { cache =>
      cache.set("hello", "world", 1.second)
      assert(cache.get[String]("hello") === Some("world"))

      Thread.sleep(1000)
      assert(!cache.delete("hello"), "delete() should return false")
    }
  }

  test("cachedFuture() expiration") {
    withInstance { cache =>
      val result = Await.result(cache.cachedFuture("hello", 1.second) { Future("world") }, 1.second)
      assert(result === "world")

      val size = cache.realSize
      assert(size === 1)

      Thread.sleep(1000)
      assert(cache.get[String]("hello") === None)
    }
  }

  test("compareAndSet() expiration") {
    withInstance { cache =>
      assert(cache.compareAndSet("hello", None, "world", 1.second), "CAS should succeed")
      assert(cache.get[String]("hello") === Some("world"))

      Thread.sleep(1000)
      assert(cache.get[String]("hello") === None)
    }
  }

  test("maintenance / scheduler") {
    withInstance { cache =>
      val startTS = System.currentTimeMillis()

      cache.set("hello", "world", 1.second)
      cache.set("hello2", "world2")

      assert(cache.realSize === 2)

      val diff = Await.result(cache.maintenance, 20.seconds)
      val m1ts = System.currentTimeMillis()

      assert(diff === 1)
      assert(cache.realSize === 1)

      val timeWindow1 = math.round((m1ts - startTS) / 1000.0)
      assert(timeWindow1 >= 3 && timeWindow1 <= 7, "scheduler should run at no less than 3 secs and no more than 7 secs")

      val diff2 = Await.result(cache.maintenance, 20.seconds)
      val m2ts = System.currentTimeMillis()

      assert(diff2 === 0)
      assert(cache.realSize === 1)

      val timeWindow2 = math.round((m2ts - m1ts) / 1000.0)
      assert(timeWindow2 >= 3 && timeWindow2 <= 7, "scheduler should run at no less than 3 secs and no more than 7 secs")
    }
  }

  def withInstance[T](cb: Cache => T) = {
    val instance = Cache(global)
    try cb(instance) finally {
      instance.close()
    }
  }
}
