package monix.eval

import scala.util.Success
import cats.implicits._


object TaskExecuteWithLocalContextSuite extends BaseTestSuite {
  test("cats' parSequence with LCP is stack safe") { implicit sc =>
    val f = List.fill(2000)(Task.unit).parSequence_
      .executeWithOptions(_.enableLocalContextPropagation)
      .runToFuture

    sc.tick()

    assertEquals(f.value, Some(Success(())))
  }
}
