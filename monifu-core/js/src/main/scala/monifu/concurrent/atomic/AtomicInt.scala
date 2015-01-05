package monifu.concurrent.atomic

final class AtomicInt private[atomic]
  (initialValue: Int) extends AtomicNumber[Int] {

  private[this] var ref = initialValue

  def getAndSet(update: Int): Int = {
    val current = ref
    ref = update
    current
  }

  def compareAndSet(expect: Int, update: Int): Boolean = {
    if (ref == expect) {
      ref = update
      true
    }
    else
      false
  }

  def set(update: Int): Unit = {
    ref = update
  }

  def get: Int = ref

  @inline
  def update(value: Int): Unit = set(value)

  @inline
  def `:=`(value: Int): Unit = set(value)

  @inline
  def lazySet(update: Int): Unit = set(update)

  def transformAndExtract[U](cb: (Int) => (U, Int)): U = {
    val (r, update) = cb(ref)
    ref = update
    r
  }

  def transformAndGet(cb: (Int) => Int): Int = {
    val update = cb(ref)
    ref = update
    update
  }

  def getAndTransform(cb: (Int) => Int): Int = {
    val current = ref
    ref = cb(ref)
    current
  }

  def transform(cb: (Int) => Int): Unit = {
    ref = cb(ref)
  }

  def getAndSubtract(v: Int): Int = {
    val c = ref
    ref = ref - v
    c
  }

  def subtractAndGet(v: Int): Int = {
    ref = ref - v
    ref
  }

  def subtract(v: Int): Unit = {
    ref = ref - v
  }

  def getAndAdd(v: Int): Int = {
    val c = ref
    ref = ref + v
    c
  }

  def getAndIncrement(v: Int = 1): Int = {
    val c = ref
    ref = ref + v
    c
  }

  def addAndGet(v: Int): Int = {
    ref = ref + v
    ref
  }

  def incrementAndGet(v: Int = 1): Int = {
    ref = ref + v
    ref
  }

  def add(v: Int): Unit = {
    ref = ref + v
  }

  def increment(v: Int = 1): Unit = {
    ref = ref + v
  }

  def countDownToZero(v: Int = 1): Int = {
    val current = get
    if (current != 0) {
      val decrement = if (current >= v) v else current
      ref = current - decrement
      decrement
    }
    else
      0
  }

  def decrement(v: Int = 1): Unit = increment(-v)
  def decrementAndGet(v: Int = 1): Int = incrementAndGet(-v)
  def getAndDecrement(v: Int = 1): Int = getAndIncrement(-v)
  def `+=`(v: Int): Unit = addAndGet(v)
  def `-=`(v: Int): Unit = subtractAndGet(v)
}

object AtomicInt {
  def apply(initialValue: Int): AtomicInt =
    new AtomicInt(initialValue)
}
