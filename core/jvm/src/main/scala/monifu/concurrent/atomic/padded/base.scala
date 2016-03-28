/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monifu.concurrent.atomic.padded

import java.lang.Double._
import java.lang.Float._
import java.util.concurrent.atomic.{AtomicLong => JavaAtomicLong}
import java.util.concurrent.atomic.{AtomicInteger => JavaAtomicInteger}
import java.util.concurrent.atomic.{AtomicBoolean => JavaAtomicBoolean}
import java.util.concurrent.atomic.{AtomicReference => JavaAtomicReference}

import monifu.concurrent.atomic
import monifu.concurrent.atomic.{AtomicBuilder => BaseAtomicBuilder}

/**
 * Adds 128 bytes of cache-line padding to Java's AtomicLong.
 */
private[padded] final class PaddedJavaAtomicLong(value: Long)
  extends JavaAtomicLong(value) {

  @volatile
  var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15 = 10L

  def `$sum$` = {
    p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15
  }
}

/**
 * Adds 128 bytes of cache-line padding to Java's AtomicInteger.
 */
private[padded] final class PaddedJavaAtomicInteger(value: Int)
  extends JavaAtomicInteger(value) {

  @volatile
  var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15 = 10L

  def `$sum$` = {
    p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15
  }
}

/**
 * Adds 128 bytes of cache-line padding to Java's AtomicBoolean.
 */
private[padded] final class PaddedJavaAtomicBoolean(value: Boolean)
  extends JavaAtomicBoolean(value) {

  @volatile
  var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15 = 10L

  def `$sum$` = {
    p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15
  }
}

/**
 * Adds 128 bytes of cache-line padding to Java's AtomicReference.
 */
private[padded] final class PaddedJavaAtomicReference[T](value: T)
  extends JavaAtomicReference[T](value) {

  @volatile
  var p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15 = 10L

  def `$sum$` = {
    p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15
  }
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.Atomic Atomic]] references.
 */
object Atomic {
  /**
   * Constructs an `Atomic[T]` reference. Based on the `initialValue`, it will return the best, most specific
   * type. E.g. you give it a number, it will return something inheriting from `AtomicNumber[T]`. That's why
   * it takes an `AtomicBuilder[T, R]` as an implicit parameter - but worry not about such details as it just works.
   *
   * @param initialValue is the initial value with which to initialize the Atomic reference
   * @param builder is the builder that helps us to build the best reference possible, based on our `initialValue`
   */
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)

  /**
   * Returns the builder that would be chosen to construct Atomic references
   * for the given `initialValue`.
   */
  def builderFor[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): AtomicBuilder[T, R] =
    builder
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicAny AtomicAny]] references.
 */
object AtomicAny {
  def apply[T](initialValue: T): AtomicAny[T] =
    atomic.AtomicAny.wrap(new PaddedJavaAtomicReference[T](initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicBoolean AtomicBoolean]] references.
 */
object AtomicBoolean {
  def apply(initialValue: Boolean): AtomicBoolean =
    atomic.AtomicBoolean.wrap(new PaddedJavaAtomicBoolean(initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicByte AtomicByte]] references.
 */
object AtomicByte {
  def apply(initialValue: Byte): AtomicByte =
    atomic.AtomicByte.wrap(new PaddedJavaAtomicInteger(initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicChar AtomicChar]] references.
 */
object AtomicChar {
  def apply(initialValue: Char): AtomicChar =
    atomic.AtomicChar.wrap(new PaddedJavaAtomicInteger(initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicDouble AtomicDouble]] references.
 */
object AtomicDouble {
  def apply(initialValue: Double): AtomicDouble =
    atomic.AtomicDouble.wrap(new PaddedJavaAtomicLong(doubleToLongBits(initialValue)))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicFloat AtomicFloat]] references.
 */
object AtomicFloat {
  def apply(initialValue: Float): AtomicFloat =
    atomic.AtomicFloat.wrap(new PaddedJavaAtomicInteger(floatToIntBits(initialValue)))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicInt AtomicInt]] references.
 */
object AtomicInt {
  def apply(initialValue: Int): AtomicInt =
    atomic.AtomicInt.wrap(new PaddedJavaAtomicInteger(initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicLong AtomicLong]] references.
 */
object AtomicLong {
  def apply(initialValue: Long): AtomicLong =
    atomic.AtomicLong.wrap(new PaddedJavaAtomicLong(initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicNumberAny AtomicNumberAny]] references.
 */
object AtomicNumberAny {
  def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] =
    atomic.AtomicNumberAny.wrap(new PaddedJavaAtomicReference[T](initialValue))
}

/**
 * Provides a constructor for building cache-line padded
 * [[monifu.concurrent.atomic.AtomicShort AtomicShort]] references.
 */
object AtomicShort {
  def apply(initialValue: Short): AtomicShort =
    atomic.AtomicShort.wrap(new PaddedJavaAtomicInteger(initialValue))
}

trait AtomicBuilder[T, R <: Atomic[T]] extends BaseAtomicBuilder[T, R]

object AtomicBuilder extends Implicits.Level3

private[padded] object Implicits {
  trait Level1 {
    implicit def AtomicRefBuilder[T] = new AtomicBuilder[T, AtomicAny[T]] {
      def buildInstance(initialValue: T) =
        AtomicAny(initialValue)
    }
  }

  trait Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T : Numeric] =
      new AtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T) =
          AtomicNumberAny(initialValue)
      }
  }

  trait Level3 extends Level2 {
    implicit val AtomicIntBuilder =
      new AtomicBuilder[Int, AtomicInt] {
        def buildInstance(initialValue: Int) =
          AtomicInt(initialValue)
      }

    implicit val AtomicLongBuilder =
      new AtomicBuilder[Long, AtomicLong] {
        def buildInstance(initialValue: Long) =
          AtomicLong(initialValue)
      }

    implicit val AtomicBooleanBuilder =
      new AtomicBuilder[Boolean, AtomicBoolean] {
        def buildInstance(initialValue: Boolean) =
          AtomicBoolean(initialValue)
      }

    implicit val AtomicByteBuilder =
      new AtomicBuilder[Byte, AtomicByte] {
        def buildInstance(initialValue: Byte): AtomicByte =
          AtomicByte(initialValue)
      }

    implicit val AtomicCharBuilder =
      new AtomicBuilder[Char, AtomicChar] {
        def buildInstance(initialValue: Char): AtomicChar =
          AtomicChar(initialValue)
      }

    implicit val AtomicShortBuilder =
      new AtomicBuilder[Short, AtomicShort] {
        def buildInstance(initialValue: Short): AtomicShort =
          AtomicShort(initialValue)
      }

    implicit val AtomicFloatBuilder =
      new AtomicBuilder[Float, AtomicFloat] {
        def buildInstance(initialValue: Float): AtomicFloat =
          AtomicFloat(initialValue)
      }

    implicit val AtomicDoubleBuilder =
      new AtomicBuilder[Double, AtomicDouble] {
        def buildInstance(initialValue: Double): AtomicDouble =
          AtomicDouble(initialValue)
      }
  }
}