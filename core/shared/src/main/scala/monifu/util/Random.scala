/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.util

import scala.annotation.tailrec

/**
 * Functions for generating pseudo-random numbers.
 *
 * The algorithm used is a "linear congruential generator", the same one
 * used in `scala.util.Random` or in `java.util.Random`.
 *
 * Note that the generated numbers are not cryptographically secure.
 * Consider instead using `java.security.SecureRandom` to get a
 * cryptographically secure pseudo-random number generator for use by
 * security-sensitive applications.
 *
 * Credits:
 *
 *   - https://www.manning.com/books/functional-programming-in-scala
 *   - https://en.wikipedia.org/wiki/Linear_congruential_generator
 */
object Random {
  type Seed = Long

  /**
   * Given a `seed`, generates a pseudo-random integer.
   */
  def int(seed: Seed): (Int, Seed) = {
    // `&` is bitwise AND. We use the current seed to generate a new seed.
    val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    // The next state, which is an `RNG` instance created from the new seed.
    val nextRNG = newSeed
    // `>>>` is right binary shift with zero fill. The value `n` is our new pseudo-random integer.
    val n = (newSeed >>> 16).toInt
    // The return value is a tuple containing both a pseudo-random integer and the next `RNG` state.
    (n, nextRNG)
  }

  /**
   * Generates a state action for generating pseudo-random
   * between min (inclusive) and max (exclusive).
   */
  def intInRange(min: Int, max: Int)(seed: Seed) = {
    require(max > min)
    val (i, r) = nonNegativeInt(seed)
    (i % (max - min) + min, r)
  }

  /**
   * Generates a state action for generating a pseudo-random
   * positive integer.
   */
  def nonNegativeInt(seed: Seed): (Int, Seed) = {
    val (i, r) = int(seed)
    (if (i < 0) -(i + 1) else i, r)
  }

  /**
   * Returns a state action for generating a list of pseudo-random
   * int values with a size equal to `count`.
   */
  def intList(count: Int)(seed: Seed): (List[Int], Seed) = {
    @tailrec
    def loop(count: Int, r: Seed, xs: List[Int]): (List[Int], Long) =
      if (count == 0)
        (xs, r)
      else {
        val (x, r2) = int(r)
        loop(count - 1, r2, x :: xs)
      }

    loop(count, seed, List.empty)
  }

  /**
   * Returns a state action for generating a pseudo-random double value
   * that is uniformly distributed between 0.0 and 1.0
   */
  def double(seed: Seed): (Double, Seed) = {
    val (i, r) = nonNegativeInt(seed)
    (i / (Int.MaxValue.toDouble + 1), r)
  }

  /**
   * Returns a state action for generating a pseudo-random double value
   * that is uniformly distributed between `min` and `max`
   */
  def doubleInRange(min: Double, max: Double)(seed: Seed): (Double, Seed) = {
    val (rnd, newSeed) = double(seed)
    (min + (max - min) * rnd, newSeed)
  }

  /**
   * Returns a state action for generating a list of pseudo-random
   * double values with a size equal to `count`.
   */
  def doubleList(count: Int)(seed: Seed): (List[Double], Seed) = {
    @tailrec
    def loop(count: Int, r: Seed, xs: List[Double]): (List[Double], Long) =
      if (count == 0)
        (xs, r)
      else {
        val (x, r2) = int(r)
        loop(count - 1, r2, x :: xs)
      }

    loop(count, seed, List.empty)
  }
}