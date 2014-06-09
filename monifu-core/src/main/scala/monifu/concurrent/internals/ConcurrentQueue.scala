package monifu.concurrent.internals

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._

private[monifu] final class ConcurrentQueue[T](elems: T*) {
  private[this] val underlying =
    new ConcurrentLinkedQueue[T](elems.asJavaCollection)

  def offer(elem: T): Unit = {
    underlying.offer(elem)
  }

  def poll(): T = {
    underlying.poll()
  }

  def isEmpty: Boolean = {
    underlying.isEmpty
  }

  def nonEmpty: Boolean = {
    !underlying.isEmpty
  }

  def clear(): Unit = {
    underlying.clear()
  }
}
