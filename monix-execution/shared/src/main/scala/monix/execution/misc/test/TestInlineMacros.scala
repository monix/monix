/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.execution.misc.test

import monix.execution.misc._
import scala.reflect.macros.whitebox
import scala.language.experimental.macros

private[execution] object TestInlineMacros {
  def testInlineSingleArg(): Either[String, Unit] =
    macro Macros.testInlineSingleArg

  def testInlineMultipleArgs(): Either[String, Unit] =
    macro Macros.testInlineMultipleArgs

  def testInlineSingleArgUnderscore(): Either[String, Unit] =
    macro Macros.testInlineSingleArgUnderscore

  def testInlineMultipleArgsUnderscore(): Either[String, Unit] =
    macro Macros.testInlineMultipleArgsUnderscore

  def testInlinePatternMatch(): Either[String, Unit] =
    macro Macros.testInlinePatternMatch

  class Macros(override val c: whitebox.Context)
    extends InlineMacros {
    import c.universe._

    def testInlineSingleArg(): c.Expr[Either[String, Unit]] = {
      val tests = List({
          val actual = inlineAndReset(q"((x:Int) => x + 1)(10)").tree
          val expected = q"10 + 1"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        },
        {
          val actual = inlineAndReset(q"((x:Int) => x + 1).apply(10)").tree
          val expected = q"10 + 1"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        })

      val result = tests.collect { case Left(msg) => msg }
      if (result.nonEmpty) {
        val expr = c.Expr[String](Literal(Constant(result.mkString("; "))))
        reify(Left(expr.splice) : Either[String, Unit])
      } else {
        reify(Right(()) : Either[String, Unit])
      }
    }

    def testInlineMultipleArgs(): c.Expr[Either[String, Unit]] = {
      val tests = List({
          val actual = inlineAndReset(q"((x:Int, y:Int) => {val z = x + 1; y + z})(10, 20)").tree
          val expected = q"{val z = 10 + 1; 20 + z}"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        },
        {
          val actual = inlineAndReset(q"((x:Int, y:Int) => {val z = x + 1; y + z}).apply(10, 20)").tree
          val expected = q"{val z = 10 + 1; 20 + z}"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        })

      val result = tests.collect { case Left(msg) => msg }
      if (result.nonEmpty) {
        val expr = c.Expr[String](Literal(Constant(result.mkString("; "))))
        reify(Left(expr.splice) : Either[String, Unit])
      } else {
        reify(Right(()) : Either[String, Unit])
      }
    }

    def testInlineSingleArgUnderscore(): c.Expr[Either[String, Unit]] = {
      val tests = List({
        val actual = inlineAndReset(q"(_ + 1)(10)").tree
        val expected = q"10 + 1"
        if (actual.equalsStructure(expected))
          Right(())
        else
          Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
      },
        {
          val actual = inlineAndReset(q"(_ + 1).apply(10)").tree
          val expected = q"10 + 1"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        })

      val result = tests.collect { case Left(msg) => msg }
      if (result.nonEmpty) {
        val expr = c.Expr[String](Literal(Constant(result.mkString("; "))))
        reify(Left(expr.splice) : Either[String, Unit])
      } else {
        reify(Right(()) : Either[String, Unit])
      }
    }

    def testInlineMultipleArgsUnderscore(): c.Expr[Either[String, Unit]] = {
      val tests = List({
        val actual = inlineAndReset(q"(_ + _)(10, 20)").tree
        val expected = q"10 + 20"
        if (actual.equalsStructure(expected))
          Right(())
        else
          Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
      },
        {
          val actual = inlineAndReset(q"(_ + _).apply(10, 20)").tree
          val expected = q"10 + 20"
          if (actual.equalsStructure(expected))
            Right(())
          else
            Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
        })

      val result = tests.collect { case Left(msg) => msg }
      if (result.nonEmpty) {
        val expr = c.Expr[String](Literal(Constant(result.mkString("; "))))
        reify(Left(expr.splice) : Either[String, Unit])
      } else {
        reify(Right(()) : Either[String, Unit])
      }
    }

    def testInlinePatternMatch(): c.Expr[Either[String, Unit]] = {
      val tests = List({
        val actual = inlineAndReset(q"((x:Int) => x match { case x => x + 1})(10)").tree
        val expected = q"10 match { case x => 10 + 1}"
        if (actual.equalsStructure(expected))
          Right(())
        else
          Left(s"Expected $expected but got $actual".replaceAll("[\\n\\s]+", " "))
      })

      val result = tests.collect { case Left(msg) => msg }
      if (result.nonEmpty) {
        val expr = c.Expr[String](Literal(Constant(result.mkString("; "))))
        reify(Left(expr.splice) : Either[String, Unit])
      } else {
        reify(Right(()) : Either[String, Unit])
      }
    }

  }
}
