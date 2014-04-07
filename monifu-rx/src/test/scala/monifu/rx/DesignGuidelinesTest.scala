package monifu.rx

import monifu.test.MonifuTest
import java.util.concurrent.{TimeUnit, CountDownLatch}

class DesignGuidelinesTest extends MonifuTest {
  import monifu.concurrent.Scheduler.Implicits.computation

  describe("Observable") {
    it("should not throw exceptions in subscribe implementations") {
      val latch = new CountDownLatch(1)
      val obs = Observable[Int] { subscriber =>
        throw new RuntimeException("Test exception")
      }

      var result = ""
      obs.subscribeOn(computation).map(x => x).subscribe(
        nextFn = _ => (),
        errorFn = ex => {
          result = ex.getMessage
          latch.countDown()
        },
        completedFn = () =>
          latch.countDown()
      )

      latch.await(1, TimeUnit.SECONDS)
      expect(result).toBe("Test exception")
    }
  }
}
