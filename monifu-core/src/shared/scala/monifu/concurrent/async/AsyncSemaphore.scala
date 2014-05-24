package monifu.concurrent.async

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.extensions.FutureExtensions
import monifu.concurrent.Scheduler

final class AsyncSemaphore private (limit: Int) {
  private[this] val queue = {
    val elems = for (i <- 0 until limit) yield ()
    AsyncQueue(elems :_*)
  }

  def acquire[T](timeout: FiniteDuration)(f: => Future[T])(implicit s: Scheduler): Future[T] =
    queue.poll().flatMap { _ =>
      val t = f.withTimeout(timeout)
      t.onComplete { _ => queue.offer(()) }
      t
    }

  def acquire[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    queue.poll().flatMap { _ =>
      f.onComplete { _ => queue.offer(()) }
      f
    }
}

object AsyncSemaphore {
  def apply(limit: Int): AsyncSemaphore =
    new AsyncSemaphore(limit)
}
