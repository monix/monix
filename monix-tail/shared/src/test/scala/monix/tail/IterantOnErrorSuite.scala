package monix.tail

import monix.eval.Coeval
import monix.execution.exceptions.DummyException

object IterantOnErrorSuite extends BaseTestSuite {
  test("fa.attempt <-> fa.map(Right) for successful streams") { implicit s =>
    val i = Iterant[Coeval].of(1, 2, 3)

    assertEquals(
      i.attempt.toListL.value,
      i.map(Right.apply).toListL.value
    )
  }

  test("fa.attempt ends with a Left in case of error") { implicit s =>
    val dummy = DummyException("dummy")
    val i = Iterant[Coeval].of(1, 2, 3) ++ Iterant[Coeval].raiseError[Int](dummy)

    assertEquals(
      i.attempt.toListL.value,
      List(Right(1), Right(2), Right(3), Left(dummy))
    )
  }

  test("fa.attempt.flatMap <-> fa") { implicit s =>
    check1 { (fa: Iterant[Coeval, Int]) =>
      val fae = fa.attempt
      val r = fae.flatMap(_.fold(
        e => Iterant[Coeval].raiseError[Int](e),
        a => Iterant[Coeval].pure(a)
      ))

      r <-> fa
    }
  }
}
