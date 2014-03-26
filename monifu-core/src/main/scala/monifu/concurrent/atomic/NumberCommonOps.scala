package monifu.concurrent.atomic

import scala.annotation.tailrec

private[atomic] trait NumberCommonOps[@specialized T] { self: AtomicNumber[T] =>
  protected def plusOp(a: T, b: T): T
  protected def minusOp(a: T, b: T): T
  protected def incrOp(a: T, b: Int): T

  @tailrec
  final def increment(v: Int): Unit = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      increment(v)
  }

  @tailrec
  final def add(v: T): Unit = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      add(v)
  }

  @tailrec
  final def incrementAndGet(v: Int): T = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      incrementAndGet(v)
    else
      update
  }

  @tailrec
  final def addAndGet(v: T): T = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      addAndGet(v)
    else
      update
  }

  @tailrec
  final def getAndIncrement(v: Int): T = {
    val current = get
    val update = incrOp(current, v)
    if (!compareAndSet(current, update))
      getAndIncrement(v)
    else
      current
  }

  @tailrec
  final def getAndAdd(v: T): T = {
    val current = get
    val update = plusOp(current, v)
    if (!compareAndSet(current, update))
      getAndAdd(v)
    else
      current
  }

  @tailrec
  final def subtract(v: T): Unit = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtract(v)
  }

  @tailrec
  final def subtractAndGet(v: T): T = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      subtractAndGet(v)
    else
      update
  }

  @tailrec
  final def getAndSubtract(v: T): T = {
    val current = get
    val update = minusOp(current, v)
    if (!compareAndSet(current, update))
      getAndSubtract(v)
    else
      current
  }

  final def increment(): Unit = increment(1)
  final def decrement(v: Int): Unit = increment(-v)
  final def decrement(): Unit = increment(-1)
  final def incrementAndGet(): T = incrementAndGet(1)
  final def decrementAndGet(v: Int): T = incrementAndGet(-v)
  final def decrementAndGet(): T = incrementAndGet(-1)
  final def getAndIncrement(): T = getAndIncrement(1)
  final def getAndDecrement(): T = getAndIncrement(-1)
  final def getAndDecrement(v: Int): T = getAndIncrement(-v)
  final def `+=`(v: T): Unit = addAndGet(v)
  final def `-=`(v: T): Unit = subtractAndGet(v)
}
