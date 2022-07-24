/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution

import org.scalacheck.Test.Parameters
import org.scalacheck.util.Pretty
import org.scalacheck.{ Arbitrary, Prop, Shrink, Test }

trait Checkers {
  import munit.Assertions._

  /** ScalaCheck test parameters instance. */
  def checkConfig: Parameters = Test.Parameters.default

  /** Test a given ScalaCheck `Prop`. */
  def check(prop: Prop, config: Parameters = checkConfig): Unit = {
    val result = Test.check(config, prop)
    val reason = Pretty.pretty(result)
    if (!result.passed) fail(reason)
  }

  /** Convert the passed 1-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check1[A1, P](
    f: A1 => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1), config)
  }

  /** Convert the passed 2-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check2[A1, A2, P](
    f: (A1, A2) => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty,
    a2: Arbitrary[A2],
    s2: Shrink[A2],
    pp2: A2 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1, a2, s2, pp2), config)
  }

  /** Convert the passed 3-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check3[A1, A2, A3, P](
    f: (A1, A2, A3) => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty,
    a2: Arbitrary[A2],
    s2: Shrink[A2],
    pp2: A2 => Pretty,
    a3: Arbitrary[A3],
    s3: Shrink[A3],
    pp3: A3 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1, a2, s2, pp2, a3, s3, pp3), config)
  }

  /** Convert the passed 4-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check4[A1, A2, A3, A4, P](
    f: (A1, A2, A3, A4) => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty,
    a2: Arbitrary[A2],
    s2: Shrink[A2],
    pp2: A2 => Pretty,
    a3: Arbitrary[A3],
    s3: Shrink[A3],
    pp3: A3 => Pretty,
    a4: Arbitrary[A4],
    s4: Shrink[A4],
    pp4: A4 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1, a2, s2, pp2, a3, s3, pp3, a4, s4, pp4), config)
  }

  /** Convert the passed 5-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check5[A1, A2, A3, A4, A5, P](
    f: (A1, A2, A3, A4, A5) => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty,
    a2: Arbitrary[A2],
    s2: Shrink[A2],
    pp2: A2 => Pretty,
    a3: Arbitrary[A3],
    s3: Shrink[A3],
    pp3: A3 => Pretty,
    a4: Arbitrary[A4],
    s4: Shrink[A4],
    pp4: A4 => Pretty,
    a5: Arbitrary[A5],
    s5: Shrink[A5],
    pp5: A5 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1, a2, s2, pp2, a3, s3, pp3, a4, s4, pp4, a5, s5, pp5), config)
  }

  /** Convert the passed 6-arg function into a property, and check it.
    *
    * @param f
    *   the function to be converted into a property and checked
    */
  def check6[A1, A2, A3, A4, A5, A6, P](
    f: (A1, A2, A3, A4, A5, A6) => P,
    config: Parameters = checkConfig
  )(
    implicit
    p: P => Prop,
    a1: Arbitrary[A1],
    s1: Shrink[A1],
    pp1: A1 => Pretty,
    a2: Arbitrary[A2],
    s2: Shrink[A2],
    pp2: A2 => Pretty,
    a3: Arbitrary[A3],
    s3: Shrink[A3],
    pp3: A3 => Pretty,
    a4: Arbitrary[A4],
    s4: Shrink[A4],
    pp4: A4 => Pretty,
    a5: Arbitrary[A5],
    s5: Shrink[A5],
    pp5: A5 => Pretty,
    a6: Arbitrary[A6],
    s6: Shrink[A6],
    pp6: A6 => Pretty
  ): Unit = {

    check(Prop.forAll(f)(p, a1, s1, pp1, a2, s2, pp2, a3, s3, pp3, a4, s4, pp4, a5, s5, pp5, a6, s6, pp6), config)
  }
}
