/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution.internal

import scala.math.{ceil, round}

private[monix] object math {
  /** Natural log of 2 */
  val lnOf2 = scala.math.log(2)

  /**
    * Calculates the base 2 logarithm of the given argument.
    *
    * @return a number such that 2^nr^ is equal to our argument.
    */
  def log2(x: Double): Double = {
    scala.math.log(x) / lnOf2
  }

  /**
    * Given a positive integer, rounds it to the nearest power of two.
    * Note that the maximum that this function can
    * return is 2^30^ (or 1,073,741,824).
    *
    * @return an integer that is a power of 2 and that is "closest"
    *         to the given argument.
    */
  def roundToPowerOf2(nr: Int): Int = {
    require(nr >= 0, "nr must be positive")
    val bit = round(log2(nr.toDouble))
    1 << (if (bit > 30) 30 else bit.toInt)
  }

  /**
    * Given a long, rounds it to the nearest power of two.
    * Note that the maximum that this function can
    * return is 2^62^ (or 4,611,686,018,427,387,904).
    *
    * @return a long that is a power of 2 and that is "closest"
    *         to the given argument.
    */
  def roundToPowerOf2(nr: Long) = {
    require(nr >= 0, "nr must be positive")
    val bit = round(log2(nr.toDouble))
    1L << (if (bit > 62) 62 else bit.toInt)
  }

  /**
    * Given a positive integer, returns the next power of 2 that is bigger
    * than our argument, or the maximum that this function can
    * return which is 2^30^ (or 1,073,741,824).
    *
    * @return an integer that is a power of 2, that is bigger or
    *        equal with our argument and that is "closest" to it.
    */
  def nextPowerOf2(nr: Int): Int = {
    require(nr >= 0, "nr must be positive")
    val bit = ceil(log2(nr.toDouble))
    1 << (if (bit > 30) 30 else bit.toInt)
  }

  /**
    * Given a positive long, returns the next power of 2 that is bigger
    * than our argument, or the maximum that this function can
    * return which is 2^62^ (or 4,611,686,018,427,387,904).
    *
    * @return a long that is a power of 2 and that is "closest"
    *         to the given argument.
    */
  def nextPowerOf2(nr: Long) = {
    require(nr >= 0, "nr must be positive")
    val bit = ceil(log2(nr.toDouble))
    1L << (if (bit > 62) 62 else bit.toInt)
  }
}
