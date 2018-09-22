package monix.eval.internal
import cats.effect.CancelToken
import monix.eval.Task
import monix.execution.internal.Platform

import scala.collection.mutable.ListBuffer

private[internal] object CancelUtils {
  /**
    * Internal API.
    */
  def cancelAll(cancelables: CancelToken[Task]*): CancelToken[Task] = {
    if (cancelables.isEmpty) {
      Task.unit
    } else Task.suspend {
      cancelAll(cancelables.iterator)
    }
  }

  /**
    * Internal API.
    */
  def cancelAll(cursor: Iterator[CancelToken[Task]]): CancelToken[Task] =
    if (cursor.isEmpty) {
      Task.unit
    } else Task.suspend {
      val frame = new CancelAllFrame(cursor)
      frame.loop()
    }

  // Optimization for `cancelAll`
  private final class CancelAllFrame(cursor: Iterator[CancelToken[Task]])
    extends StackFrame[Unit, Task[Unit]] {

    private[this] val errors = ListBuffer.empty[Throwable]

    def loop(): CancelToken[Task] = {
      if (cursor.hasNext) {
        cursor.next().flatMap(this)
      } else {
        errors.toList match {
          case Nil =>
            Task.unit
          case first :: rest =>
            Task.raiseError(Platform.composeErrors(first, rest: _*))
        }
      }
    }

    def apply(a: Unit): Task[Unit] =
      loop()

    def recover(e: Throwable): Task[Unit] = {
      errors += e
      loop()
    }
  }
}
