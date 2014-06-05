package monifu.concurrent.internals

import scala.collection.mutable

private[monifu] final class ConcurrentQueue[T](elems: T*) {
  private[this] val underlying: mutable.Queue[T] =
    mutable.Queue(elems : _*)

  def offer(elem: T): Unit = {
    if (elem == null) throw null
    underlying.enqueue(elem)
  }

  def poll(): T = {
    if (underlying.isEmpty)
      null.asInstanceOf[T]
    else
      underlying.dequeue()
  }

  def isEmpty: Boolean = {
    underlying.isEmpty
  }

  def nonEmpty: Boolean = {
    !underlying.isEmpty
  }
}
