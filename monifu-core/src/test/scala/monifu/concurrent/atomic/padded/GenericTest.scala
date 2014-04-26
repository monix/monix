package monifu.concurrent.atomic.padded

import monifu.concurrent.atomic.GenericTest

class GenericPaddedAtomicAnyTest extends GenericTest[String, AtomicAny[String]](
  "PaddedAtomicAny", Atomic.builderFor(""), x => x.toString, x => x.toInt)

class GenericPaddedAtomicBooleanTest extends GenericTest[Boolean, AtomicBoolean](
  "PaddedAtomicBoolean", Atomic.builderFor(true), x => if (x == 1) true else false, x => if (x) 1 else 0)

class GenericPaddedAtomicNumberAnyTest extends GenericTest[BigInt, AtomicNumberAny[BigInt]](
  "PaddedAtomicNumberAny", Atomic.builderFor(BigInt(0)), x => BigInt(x), x => x.toInt)

class GenericPaddedAtomicFloatTest extends GenericTest[Float, AtomicFloat](
  "PaddedAtomicFloat", Atomic.builderFor(0.0f), x => x.toFloat, x => x.toInt)

class GenericPaddedAtomicDoubleTest extends GenericTest[Double, AtomicDouble](
  "PaddedAtomicDouble", Atomic.builderFor(0.toDouble), x => x.toDouble, x => x.toInt)

class GenericPaddedAtomicShortTest extends GenericTest[Short, AtomicShort](
  "PaddedAtomicShort", Atomic.builderFor(0.toShort), x => x.toShort, x => x.toInt)

class GenericPaddedAtomicByteTest extends GenericTest[Byte, AtomicByte](
  "PaddedAtomicByte", Atomic.builderFor(0.toByte), x => x.toByte, x => x.toInt)

class GenericPaddedAtomicCharTest extends GenericTest[Char, AtomicChar](
  "PaddedAtomicChar", Atomic.builderFor(0.toChar), x => x.toChar, x => x.toInt)

class GenericPaddedAtomicIntTest extends GenericTest[Int, AtomicInt](
  "PaddedAtomicInt", Atomic.builderFor(0), x => x, x => x)

class GenericPaddedAtomicLongTest extends GenericTest[Long, AtomicLong](
  "PaddedAtomicLong", Atomic.builderFor(0.toLong), x => x.toLong, x => x.toInt)

