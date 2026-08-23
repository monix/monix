/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.eval
package internal

/** Type-erased continuation stack owned by one [[IOFiber]].
  *
  * References and tags are kept in parallel arrays to avoid allocating a frame for
  * every bind, error handler, or cancellation finalizer. The fiber run-loop is
  * serialized, therefore this stack needs no synchronization of its own.
  */
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
    // Each outcome searches only for a frame which can handle it. Frames for the
    // other outcomes no longer apply and are discarded on the way.
    // For example, an error skips binds and onCancel finalizers until it finds an
    // error handler.
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
      // Pop is logical: old array slots remain until a later push overwrites them or
      // the complete stack is collected.
      val ret = refs(size - 1)
      size -= 1
      ret
    }
  }

  private def push(ref: AnyRef, tag: Int): Unit = {
    val index = size
    size += 1
    if (size == capacity) {
      // Both arrays describe one logical stack and must grow together.
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
