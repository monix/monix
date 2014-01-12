package monifu.concurrent.atomic

import annotation.tailrec

trait Atomic[@specialized T] {
  type Underlying
  def asJava: Underlying

  def get: T
  def apply(): T = get

  def set(update: T): Unit
  def update(value: T): Unit = set(value)
  def `:=`(value: T): Unit = set(value)

  def lazySet(update: T)

  def compareAndSet(expect: T, update: T): Boolean
  def weakCompareAndSet(expect: T, update: T): Boolean
  def getAndSet(update: T): T

  @tailrec
  final def awaitCompareAndSet(expect: T, update: T): Unit = {
    if (!compareAndSet(expect, update))
      awaitCompareAndSet(expect, update)
  }

  @tailrec
  final def awaitValue(expect: T): Unit = {
    if (get != expect) awaitValue(expect)
  }

  @tailrec
  final def awaitCondition(p: T => Boolean): Unit = {
    if (!p(get)) awaitCondition(p)
  }

  @tailrec
  final def transformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = get
    val (update, extract) = cb(current)
    if (!compareAndSet(current, update))
      transformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def weakTransformAndExtract[U](cb: (T) => (T, U)): U = {
    val current = get
    val (update, extract) = cb(current)
    if (!weakCompareAndSet(current, update))
      weakTransformAndExtract(cb)
    else
      extract
  }

  @tailrec
  final def transformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transformAndGet(cb)
    else
      update
  }

  @tailrec
  final def weakTransformAndGet(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!weakCompareAndSet(current, update))
      weakTransformAndGet(cb)
    else
      update
  }

  @tailrec
  final def getAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      getAndTransform(cb)
    else
      current
  }

  @tailrec
  final def weakGetAndTransform(cb: (T) => T): T = {
    val current = get
    val update = cb(current)
    if (!weakCompareAndSet(current, update))
      weakGetAndTransform(cb)
    else
      current
  }

  @tailrec
  final def transform(cb: (T) => T) {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      transform(cb)
  }

  @tailrec
  final def weakTransform(cb: (T) => T) {
    val current = get
    val update = cb(current)
    if (!compareAndSet(current, update))
      weakTransform(cb)
  }
}

object Atomic {
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: AtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)
}



