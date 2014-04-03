package monifu.test

import org.scalatest.FunSpec

trait MonifuTest extends FunSpec {
  case class Matcher[+T](value: T) {
    def toBe[U >: T](expected: U) =
      if (value != expected)
        throw new AssertionError(s"$value != $expected")
  }

  def expect[T](value: T) = Matcher(value)
}
