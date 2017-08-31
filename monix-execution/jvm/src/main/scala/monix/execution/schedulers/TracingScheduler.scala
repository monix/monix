package monix.execution.schedulers

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import monix.execution.{Cancelable, Scheduler, UncaughtExceptionReporter, ExecutionModel => ExecModel}
import scala.concurrent.ExecutionContext
import scala.util.DynamicVariable

case class TracingScheduler[T] private (
   scheduler: ScheduledExecutorService,
   ec: ExecutionContext,
   r: UncaughtExceptionReporter,
   executionModel: ExecModel)(implicit dv: DynamicVariable[T])
  extends ReferenceScheduler { self =>

  def executeAsync(r: Runnable): Unit =
    ec.execute(r)

  override def withExecutionModel(em: ExecModel): Scheduler = super.withExecutionModel(em)

  /** Reports that an asynchronous computation failed. */
  override def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

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
      ec.execute(r)
      Cancelable.empty
    } else {
      val deferred = new Runnable {
        override def run(): Unit = ec.execute(r)
      }
      val task = scheduler.schedule(deferred, initialDelay, unit)
      Cancelable(() => task.cancel(true))
    }
  }

  private[this] val trampoline =
    TrampolineExecutionContext(new ExecutionContext {
      def execute(runnable: Runnable): Unit =
        self.executeAsync(runnable)
      def reportFailure(cause: Throwable): Unit =
        self.reportFailure(cause)
    })

  override final def execute(runnable: Runnable): Unit = {

    runnable match {
      case r: TrampolinedRunnable =>
        trampoline.execute(r)
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }
  }
}
