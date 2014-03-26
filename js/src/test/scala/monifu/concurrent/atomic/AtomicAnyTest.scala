package monifu.concurrent.atomic

import utest._

object AtomicAnyTest extends TestSuite {

  def tests = TestSuite {
    "AtomicAny" - {
      "set" - {
      	val r = Atomic("initial")
      	assert(r.get == "initial")

      	r.set("update")
      	assert(r.get == "update")
      }

      "getAndSet" - {
      	val r = Atomic("initial")
      	assert(r.get == "initial")

      	assert(r.getAndSet("update") == "initial")
      	assert(r.get == "update")
      }

      "compareAndSet" - {
      	val r = Atomic("initial")
      	assert(r.get == "initial")

      	assert(r.compareAndSet("initial", "update") == true)
      	assert(r.get == "update")
      	assert(r.compareAndSet("initial", "other")  == false)
      	assert(r.get == "update")
      	assert(r.compareAndSet("update",  "other")  == true)
      	assert(r.get == "other")
      }

      "transform" - {
      	val r = Atomic("initial value")
      	assert(r.get == "initial value")

      	r.transform(s => "updated" + s.dropWhile(_ != ' '))
      	assert(r.get == "updated value")
      }

      "transformAndGet" - {
      	val r = Atomic("initial value")
      	assert(r.get == "initial value")

      	val value = r.transformAndGet(s => "updated" + s.dropWhile(_ != ' '))
      	assert(value == "updated value")
      }

      "getAndTransform" - {
      	val r = Atomic("initial value")
      	assert(r() == "initial value")

      	val value = r.getAndTransform(s => "updated" + s.dropWhile(_ != ' '))
      	assert(value == "initial value")
      	assert(r.get == "updated value")
      }

      "transformAndExtract" - {
      	val r = Atomic("initial value")
      	assert(r.get == "initial value")

      	val value = r.transformAndExtract { s =>
      	  val newS = "updated" + s.dropWhile(_ != ' ')
      	  (newS, "extracted")
      	}

      	assert(value == "extracted")
      	assert(r.get == "updated value")
      }
    }
  }
}