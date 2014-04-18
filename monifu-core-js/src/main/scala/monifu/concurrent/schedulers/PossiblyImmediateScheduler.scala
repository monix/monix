package monifu.concurrent.schedulers

import monifu.concurrent.{Cancelable, Scheduler}
import scala.concurrent.duration.FiniteDuration
import scala.collection.immutable.Queue
import scala.annotation.tailrec
import scala.util.control.NonFatal


private[concurrent] final class PossiblyImmediateScheduler(fallback: AsyncScheduler.type) extends Scheduler {
  private[this] var immediateQueue = Queue.empty[Runnable]
  private[this] var withinLoop = false

  def execute(runnable: Runnable): Unit = {
    immediateQueue = immediateQueue.enqueue(runnable)
    if (!withinLoop) {
      withinLoop = true
      try  { immediateLoop() } finally { withinLoop = false }
    }
  }

  @tailrec
  private[this] def immediateLoop(): Unit = {
    if (immediateQueue.nonEmpty) {
      val (task, newQueue) = immediateQueue.dequeue
      immediateQueue = newQueue

      try {
        task.run()
      }
      catch {
        case NonFatal(ex) =>
          // exception in the immediate scheduler must be thrown,
          // but first we try to reschedule the pending tasks on the fallback
          try { rescheduleOnFallback(newQueue) } finally {
            immediateQueue = Queue.empty
            throw ex
          }
      }

      immediateLoop()
    }
  }

  @tailrec
  private[this] def rescheduleOnFallback(queue: Queue[Runnable]): Unit =
    if (queue.nonEmpty) {
      val (task, newQueue) = queue.dequeue
      fallback.execute(task)
      rescheduleOnFallback(newQueue)
    }


  def scheduleOnce(initialDelay: FiniteDuration, action: () => Unit): Cancelable = {
    if (initialDelay.toMillis < 1)
      scheduleOnce(action)
    else {
      // we cannot schedule tasks with an initial delay on the current thread as that
      // will block the thread, instead we delegate to our fallback
      fallback.scheduleOnce(action)
    }
  }

  def reportFailure(t: Throwable): Unit =
    fallback.reportFailure(t)
}
