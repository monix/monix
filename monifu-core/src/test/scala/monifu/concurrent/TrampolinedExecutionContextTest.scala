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

package monifu.concurrent

import org.scalatest.FunSuite
import scala.concurrent._
import scala.concurrent.duration._


class TrampolinedExecutionContextTest extends FunSuite {
  val s = new TrampolinedExecutionContext(new ExecutionContext {
    def execute(runnable: Runnable) = {
      ExecutionContext.Implicits.global.execute(runnable)
    }
    
    def reportFailure(ex: Throwable) = {
      if (!ex.getMessage.contains("test-exception"))
        throw ex
    }
  })

  def runnable(action: => Unit) = 
    new Runnable { def run() = action }
  
  test("scheduleOnce") {
    val p = Promise[Int]()
    s.execute(runnable(p.success(1)))
    assert(p.future.value.get.get === 1)
  }

  test("immediate execution happens") {
    implicit val ec = s

    @volatile var effect = 0
    val seed = ThreadLocal(0)
    seed set 100

    for (v1 <- Future(seed.get()).map(_ * 100).filter(_ % 2 == 0); v2 <- Future(seed.get() * 3).map(_ - 100)) {
      effect = v1 + v2
    }

    assert(effect === 10000 + 200)
  }

  test("should execute async") {
    var stackDepth = 0
    var iterations = 0

    s.execute(runnable { 
      stackDepth += 1
      iterations += 1
      s.execute(runnable { 
        stackDepth += 1
        iterations += 1
        assert(stackDepth === 1)

        s.execute(runnable { 
          stackDepth += 1
          iterations += 1
          assert(stackDepth === 1)
          stackDepth -= 1
        })

        assert(stackDepth === 1)
        stackDepth -= 1
      })

      assert(stackDepth === 1)
      stackDepth -= 1
    })

    assert(iterations === 3)
    assert(stackDepth === 0)
  }

  test("triggering an exception shouldn't blow the thread, but should reschedule pending tasks") {
    val p = Promise[String]()
    val tl = ThreadLocal("result")
    tl.set("wrong-result")

    def run() =
      s.execute(runnable {
        s.execute(runnable {
          p.success(tl.get())
        })

        assert(p.isCompleted === false)
        throw new RuntimeException("test-exception-please-ignore")
      })

    run()
    assert(Await.result(p.future, 3.second) === "result")
  }

  test("blocking the thread reschedules pending tasks") {
    val seenValue = ThreadLocal("async-value")
    seenValue set "local-value"

    val async = Promise[String]()
    val local = Promise[String]()

    s.execute(runnable {
      // first schedule
      s.execute(runnable(async.success(seenValue.get())))
      // then do some blocking
      blocking {
        local.success(seenValue.get())
      }
    })

    assert(Await.result(local.future, 3.second) === "local-value")
    assert(Await.result(async.future, 3.second) === "async-value")
  }

  test("while blocking, all future tasks should be scheduled on the fallback") {
    val seenValue = ThreadLocal("async-value")
    seenValue set "local-value"

    val async = Promise[String]()
    val local = Promise[String]()

    s.execute(runnable {
      blocking {
        s.execute(runnable(async.success(seenValue.get())))
        local.success(seenValue.get())
      }
    })

    assert(Await.result(local.future, 3.second) === "local-value")
    assert(Await.result(async.future, 3.second) === "async-value")
  }
}
