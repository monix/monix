package monifu.concurrent.schedulers

import monifu.concurrent.{Cancelable, Scheduler}
import scala.concurrent.duration.FiniteDuration
import scala.collection.immutable.Queue
import scala.annotation.tailrec
import scala.util.control.NonFatal


final class TrampolineScheduler private[concurrent] (fallback: Scheduler, reporter: (Throwable) => Unit) extends Scheduler {
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
      val task = {
        val (t, newQueue) = immediateQueue.dequeue
        immediateQueue = newQueue
        t
      }

      try {
        task.run()
      }
      catch {
        case NonFatal(ex) =>
          // exception in the immediate scheduler must be reported
          // but first reschedule the pending tasks on the fallback
          try { rescheduleOnFallback(immediateQueue) } finally {
            immediateQueue = Queue.empty
            reportFailure(ex)
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

  def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
    if (initialDelay.toMillis < 1)
      scheduleOnce(action)
    else {
      // we cannot schedule tasks with an initial delay on the current thread as that
      // will block the thread, instead we delegate to our fallback
      fallback.scheduleOnce(initialDelay, action)
    }
  }

  def reportFailure(t: Throwable): Unit =
    reporter(t)
}

object TrampolineScheduler {
  def apply(fallback: Scheduler): TrampolineScheduler =
    new TrampolineScheduler(fallback, fallback.reportFailure)
}
