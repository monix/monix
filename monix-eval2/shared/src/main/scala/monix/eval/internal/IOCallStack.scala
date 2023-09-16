package monix.eval
package internal

private[internal] final class IOCallStack(initialCapacity: Int) {
  private[this] var refs = new Array[AnyRef](initialCapacity)
  private[this] var tags = new Array[Int](initialCapacity)
  private[this] var capacity = initialCapacity
  private[this] var size = 0

  def findAndPopNextFlatMap(): Any => IO[Any] =
    findAndPop(IOCallStack.FlatMapTag).asInstanceOf[Any => IO[Any]]

  def findAndPopNextHandleError(): Throwable => IO[Any] =
    findAndPop(IOCallStack.HandleErrorTag).asInstanceOf[Throwable => IO[Any]]

  def findAndPopNextOnCancel(): IO[Unit] =
    findAndPop(IOCallStack.OnCancelTag).asInstanceOf[IO[Unit]]

  private def findAndPop(tag: Int): AnyRef = {
    var found = false
    while (!found && size > 0) {
      val index = size - 1
      if (tags(index) == tag)
        found = true
      else
        size -= 1
    }
    if (!found) {
      null
    } else {
      val ret = refs(size - 1)
      size -= 1
      ret
    }
  }

  private def push(ref: AnyRef, tag: Int): Unit = {
    val index = size
    size += 1
    if (size == capacity) {
      capacity *= 2

      val newArrayRefs = new Array[AnyRef](capacity)
      System.arraycopy(refs, 0, newArrayRefs, 0, refs.length)
      refs = newArrayRefs

      val newArrayTags = new Array[Int](capacity)
      System.arraycopy(tags, 0, newArrayTags, 0, tags.length)
      tags = newArrayTags
    }
    refs(index) = ref
    tags(index) = tag
  }

  def pushFlatMap(f: Any => IO[Any]): Unit =
    push(f, IOCallStack.FlatMapTag)

  def pushHandleError(f: Throwable => IO[Any]): Unit =
    push(f, IOCallStack.HandleErrorTag)

  def pushOnCancel(f: IO[Unit]): Unit =
    push(f, IOCallStack.OnCancelTag)
}

private[internal] object IOCallStack {
  final val FlatMapTag = 0
  final val HandleErrorTag = 1
  final val OnCancelTag = 2
}
