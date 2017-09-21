package monix.execution.schedulers

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monix.execution.misc.Local
import monix.execution.{Cancelable, Scheduler, UncaughtExceptionReporter, ExecutionModel => ExecModel}
import scala.concurrent.ExecutionContext

final class TracingScheduler private (
  scheduler: ScheduledExecutorService,
  ec: ExecutionContext,
  r: UncaughtExceptionReporter,
  val executionModel: ExecModel) extends ReferenceScheduler with BatchingScheduler { self =>

  /** Executes the given task with tracing.
    *
    * @param r is the callback to be executed
    */
  override def executeAsync(r: Runnable): Unit = {
    val oldContext = Local.getContext()
    ec.execute(new Runnable {
      override def run = {
        Local.withContext(oldContext)(r.run())
      }
    })
  }

  /** Schedules a task to run in the future, after `initialDelay`.
    *
    * For example the following schedules a message to be printed to
    * standard output after 5 minutes:
    * {{{
    *   val task = scheduler.scheduleOnce(5, TimeUnit.MINUTES, new Runnable {
    *     def run() = print("Hello, world!")
    *   })
    *
    *   // later if you change your mind ...
    *   task.cancel()
    * }}}
    *
    * @param initialDelay is the time to wait until the execution happens
    * @param unit         is the time unit used for `initialDelay`
    * @param r            is the callback to be executed
    * @return a `Cancelable` that can be used to cancel the created task
    *         before execution.
    */
  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    if (initialDelay <= 0) {
      executeAsync(r)
      Cancelable.empty
    } else {
      val deferred = new Runnable {
        override def run(): Unit = executeAsync(r)
      }
      val task = scheduler.schedule(deferred, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  /** Reports that an asynchronous computation failed. */
  override def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  override def withExecutionModel(em: ExecModel): TracingScheduler =
    new TracingScheduler(scheduler, ec, r, em)
}

object TracingScheduler {

  def apply(
    schedulerService: ScheduledExecutorService,
    ec: ExecutionContext,
    reporter: UncaughtExceptionReporter,
    executionModel: ExecModel): TracingScheduler =
    new TracingScheduler(schedulerService, ec, reporter, executionModel)
}