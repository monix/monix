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

package monix.execution.atomic

case class BoxedLong(value: Long)

object BoxedLong {
  final val MinValue = BoxedLong(Long.MinValue)
  final val MaxValue = BoxedLong(Long.MaxValue)

  implicit val numeric = new Numeric[BoxedLong] {
    def plus(x: BoxedLong, y: BoxedLong): BoxedLong =
      BoxedLong(x.value + y.value)
    def toDouble(x: BoxedLong): Double =
      x.value.toDouble
    def toFloat(x: BoxedLong): Float =
      x.value.toFloat
    def toInt(x: BoxedLong): Int =
      x.value.toInt
    def negate(x: BoxedLong): BoxedLong =
      BoxedLong(-1 * x.value)
    def fromInt(x: Int): BoxedLong =
      BoxedLong(x)
    def toLong(x: BoxedLong): Long =
      x.value
    def times(x: BoxedLong, y: BoxedLong): BoxedLong =
      BoxedLong(x.value * y.value)
    def minus(x: BoxedLong, y: BoxedLong): BoxedLong =
      BoxedLong(x.value - y.value)
    def compare(x: BoxedLong, y: BoxedLong): Int =
      x.value.compareTo(y.value)
    def parseString(str: String): Option[BoxedLong] =
      try Some(BoxedLong(str.toLong))
      catch { case _: NumberFormatException => None }
  }
}
