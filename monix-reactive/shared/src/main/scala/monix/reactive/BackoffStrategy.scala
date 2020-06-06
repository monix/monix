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

package monix.reactive

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

abstract class BackoffStrategy extends ((Long, FiniteDuration, FiniteDuration) => FiniteDuration)

object BackoffStrategy {

  /**
    * Implements a Linear Backoff Strategy. For example, an initial delay of 1 second plus a max attempts
    * value of 5 would result in the following:
    *
    * | Attempt | Delay     |
    * | ---     | ---       |
    * | 1       | 1 seconds |
    * | 2       | 2 seconds |
    * | 3       | 3 seconds |
    * | 4       | 4 seconds |
    * | 5       | 5 seconds |
    */
  final case object Linear extends BackoffStrategy {
    override def apply(attempt: Long, initialDelay: FiniteDuration, currentDelay: FiniteDuration): FiniteDuration = {
      initialDelay * attempt
    }
  }

  /**
    * Implements an Exponential Backoff Strategy. For example, an initial delay of 1 second plus a max attempts
    * value of 5 would result in the following:
    *
    * | Attempt | Delay      |
    * | ---     | ---        |
    * | 1       | 1 second   |
    * | 2       | 2 seconds  |
    * | 3       | 4 seconds  |
    * | 4       | 8 seconds  |
    * | 5       | 16 seconds |
    */
  final case class Exponential(factor: Long = 2) extends BackoffStrategy {
    override def apply(attempt: Long, initialDelay: FiniteDuration, currentDelay: FiniteDuration): FiniteDuration =
      currentDelay * factor
  }

  /**
    * Implements a Fibonacci Backoff Strategy, for example, and initial delay of 1 second plus a max attempts
    * value of 5 would result in the following:
    *
    * | Attempt | Delay     |
    * | ---     | ---       |
    * | 1       | 1 second  |
    * | 2       | 1 second  |
    * | 3       | 2 seconds |
    * | 4       | 3 seconds |
    * | 5       | 5 seconds |
    */
  final case object Fibonacci extends BackoffStrategy {
    private def getNthFibonacciNumber(attempt: Long): Long = {
      @tailrec def loop(round: Long, a: Long, b: Long): Long = {
        if (round == attempt) b else loop(round + 1, b, a + b)
      }

      loop(1, 0, 1)
    }

    override def apply(attempt: Long, initialDelay: FiniteDuration, currentDelay: FiniteDuration): FiniteDuration =
      initialDelay * getNthFibonacciNumber(attempt)
  }
}