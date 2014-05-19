package monifu.concurrent.schedulers

import monifu.concurrent.{ThreadLocal, Cancelable, Scheduler}
import scala.concurrent.duration.FiniteDuration
import scala.collection.immutable.Queue
import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.concurrent.{CanAwait, BlockContext}


final class TrampolineScheduler private[concurrent] (fallback: ConcurrentScheduler, reporter: Throwable => Unit)
  extends Scheduler with BlockContext {

  private[this] val immediateQueue = ThreadLocal(Queue.empty[Runnable])
  private[this] val withinLoop = ThreadLocal(false)
  private[this] val parentBlockContext = ThreadLocal(null : BlockContext)
  private[this] val duringBlocking = ThreadLocal(false)

  def execute(runnable: Runnable): Unit =
    if (!duringBlocking.get()) {
      immediateQueue set immediateQueue.get().enqueue(runnable)
      if (!withinLoop.get) {
        withinLoop set true
        try immediateLoop() finally withinLoop set false
      }
    }
    else {
      fallback.execute(runnable)
    }

  @tailrec
  private[this] def immediateLoop(): Unit = {
    if (immediateQueue.get.nonEmpty) {
      val task = {
        val (t, newQueue) = immediateQueue.get.dequeue
        immediateQueue set newQueue
        t
      }

      val prevBlockContext = BlockContext.current
      BlockContext.withBlockContext(this) {
        parentBlockContext set prevBlockContext

        try {
          task.run()
        }
        catch {
          case NonFatal(ex) =>
            // exception in the immediate scheduler must be reported
            // but first we reschedule the pending tasks on the fallback
            try { rescheduleOnFallback(immediateQueue.get) } finally {
              immediateQueue set Queue.empty
              reportFailure(ex)
            }
        }
        finally {
          parentBlockContext set null
        }
      }

      immediateLoop()
    }
  }

  // if we know that a task will be blocking, then reschedule all
  // pending tasks on the fallback Scheduler
  def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
    duringBlocking set true
    try {
      val queue = immediateQueue.get()
      immediateQueue set Queue.empty
      rescheduleOnFallback(queue)
      parentBlockContext.get.blockOn(thunk)
    }
    finally {
      duringBlocking set false
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
  def apply(implicit fallback: ConcurrentScheduler): TrampolineScheduler =
    new TrampolineScheduler(fallback, fallback.reportFailure)
}
