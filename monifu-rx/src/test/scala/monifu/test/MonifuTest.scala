package monifu.test

import org.scalatest.FunSpec

trait MonifuTest extends FunSpec {
  case class Matcher[T](value: T) {
    def toBe(expected: T) =
      if (value != expected)
        throw new AssertionError(s"$value != $expected")
  }

  def expect[T](value: T) = Matcher(value)
}
