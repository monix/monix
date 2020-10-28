/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution
package schedulers

import monix.execution.atomic.AtomicAny
import monix.execution.cancelables.SingleAssignCancelable
import scala.util.control.NonFatal
import monix.execution.schedulers.TestScheduler._

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.{Duration, FiniteDuration, TimeUnit}
import scala.util.Random

/** [[Scheduler]] and a provider of `cats.effect.Timer` instances,
  * that can simulate async boundaries and time passage, useful for
  * testing purposes.
  *
  * Usage for simulating an `ExecutionContext`:
  *
  * {{{
  *   implicit val ec = TestScheduler()
  *
  *   ec.execute(new Runnable { def run() = println("task1") })
  *
  *   ex.execute(new Runnable {
  *     def run() = {
  *       println("outer")
  *
  *       ec.execute(new Runnable {
  *         def run() = println("inner")
  *       })
  *     }
  *   })
  *
  *   // Nothing executes until `tick` gets called
  *   ec.tick()
  *
  *   // Testing the resulting state
  *   assert(ec.state.tasks.isEmpty)
  *   assert(ec.state.lastReportedError == null)
  * }}}
  *
  * `TestScheduler` can also simulate the passage of time:
  *
  * {{{
  *   val ctx = TestScheduler()
  *   val f = Task(1 + 1).delayExecution(10.seconds).runAsync
  *
  *   // This only triggers immediate execution, so nothing happens yet
  *   ctx.tick()
  *   assert(f.value == None)
  *
  *   // Simulating the passage of 5 seconds, nothing happens yet
  *   ctx.tick(5.seconds)
  *   assert(f.value == None)
  *
  *   // Simulating another 5 seconds, now we're done!
  *   assert(f.value == Some(Success(2)))
  * }}}
  *
  * We are also able to build a `cats.effect.Timer` from any `Scheduler`
  * and for any data type:
  *
  *
  * {{{
  *   val ctx = TestScheduler()
  *
  *   val timer: Timer[IO] = ctx.timer[IO]
  * }}}
  *
  * We can now simulate time passage for `cats.effect.IO` as well:
  *
  * {{{
  *   val io = timer.sleep(10.seconds) *> IO(1 + 1)
  *   val f = io.unsafeToFuture()
  *
  *   // This invariant holds true, because our IO is async
  *   assert(f.value == None)
  *
  *   // Not yet completed, because this does not simulate time passing:
  *   ctx.tick()
  *   assert(f.value == None)
  *
  *   // Simulating time passing:
  *   ctx.tick(10.seconds)
  *   assert(f.value == Some(Success(2))
  * }}}
  *
  * Simulating time makes this pretty useful for testing race conditions:
  *
  * {{{
  *   val timeoutError = new TimeoutException
  *   val timeout = Task.raiseError[Int](timeoutError)
  *     .delayExecution(10.seconds)
  *
  *   val pair = (Task.never, timeout).parMapN(_ + _)
  *
  *   // Not yet
  *   ctx.tick()
  *   assert(f.value == None)
  *
  *   // Not yet
  *   ctx.tick(5.seconds)
  *   assert(f.value == None)
  *
  *   // Good to go:
  *   ctx.tick(5.seconds)
  *   assert(f.value == Some(Failure(timeoutError)))
  * }}}
  */
final class TestScheduler private (
  private[this] val stateRef: AtomicAny[State],
  override val executionModel: ExecutionModel)
  extends ReferenceScheduler with BatchingScheduler {

  /**
    * Returns the internal state of the `TestScheduler`, useful for testing
    * that certain execution conditions have been met.
    */
  def state: State = stateRef.get()

  override def clockRealTime(unit: TimeUnit): Long = {
    val d: FiniteDuration = stateRef.get().clock
    unit.convert(d.length, d.unit)
  }

  override def clockMonotonic(unit: TimeUnit): Long =
    clockRealTime(unit)

  @tailrec
  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    val current: State = stateRef.get()
    val (cancelable, newState) = TestScheduler.scheduleOnce(current, FiniteDuration(initialDelay, unit), r, cancelTask)

    if (stateRef.compareAndSet(current, newState)) cancelable
    else
      scheduleOnce(initialDelay, unit, r)
  }

  @tailrec
  protected override def executeAsync(r: Runnable): Unit = {
    val current: State = stateRef.get()
    val update = TestScheduler.execute(current, r)
    if (!stateRef.compareAndSet(current, update)) executeAsync(r)
  }

  @tailrec
  override def reportFailure(t: Throwable): Unit = {
    val current: State = stateRef.get()
    val update = current.copy(lastReportedError = t)
    if (!stateRef.compareAndSet(current, update)) reportFailure(t)
  }

  override def withExecutionModel(em: ExecutionModel): TestScheduler =
    new TestScheduler(stateRef, em)

  override val features: Features =
    Features(Scheduler.BATCHING)

  /**
    * Executes just one tick, one task, from the internal queue, useful
    * for testing that some runnable will definitely be executed next.
    *
    * Returns a boolean indicating that tasks were available and that
    * the head of the queue has been executed, so normally you have
    * this equivalence:
    *
    * {{{
    *   while (ec.tickOne()) {}
    *   // ... is equivalent with:
    *   ec.tick()
    * }}}
    *
    * Note that task extraction has a random factor, the behavior being like
    * [[tick]], in order to simulate non-determinism. So you can't rely on
    * some ordering of execution if multiple tasks are waiting execution.
    *
    * @return `true` if a task was available in the internal queue, and
    *        was executed, or `false` otherwise
    */
  @tailrec def tickOne(): Boolean = {
    val current = stateRef.get()

    // extracting one task by taking the immediate tasks
    extractOneTask(current, current.clock) match {
      case Some((head, rest)) =>
        if (!stateRef.compareAndSet(current, current.copy(tasks = rest)))
          tickOne()
        else {
          // execute task
          try head.task.run()
          catch {
            case ex if NonFatal(ex) =>
              reportFailure(ex)
          }

          true
        }

      case None =>
        false
    }
  }

  /**
    * Triggers execution by going through the queue of scheduled tasks and
    * executing them all, until no tasks remain in the queue to execute.
    *
    * Order of execution isn't guaranteed, the queued `Runnable`s are
    * being shuffled in order to simulate the needed non-determinism
    * that happens with multi-threading.
    *
    * {{{
    *   implicit val ec = TestScheduler()
    *
    *   val f = Future(1 + 1).map(_ + 1)
    *   // Execution is momentarily suspended in TestContext
    *   assert(f.value == None)
    *
    *   // Simulating async execution:
    *   ec.tick()
    *   assert(f.value, Some(Success(2)))
    * }}}
    *
    * The optional parameter can be used for simulating time:
    *
    * {{{
    *   implicit val ec = TestScheduler()
    *
    *   val f = Task.sleep(10.seconds).map(_ => 10).runAsync
    *
    *   // Not yet completed, because this does not simulate time passing:
    *   ctx.tick()
    *   assert(f.value == None)
    *
    *   // Simulating time passing:
    *   ctx.tick(10.seconds)
    *   assert(f.value == Some(Success(10))
    * }}}
    *
    * @param time is an optional parameter for simulating time passing
    *
    * @param maxImmediateTasks is an optional parameter that specifies a maximum
    *        number of immediate tasks to execute one after another; setting
    *        this parameter can prevent non-termination
    */
  def tick(time: FiniteDuration = Duration.Zero, maxImmediateTasks: Option[Int] = None): Unit = {
    @tailrec
    def loop(time: FiniteDuration, iterCount: Int, maxIterCount: Int): Unit = {
      val current: State = stateRef.get()
      val currentClock = current.clock + time

      extractOneTask(current, currentClock) match {
        case Some((head, rest)) =>
          if (!stateRef.compareAndSet(current, current.copy(clock = head.runsAt, tasks = rest)))
            loop(time, iterCount, maxIterCount)
          else {
            // execute task
            try head.task.run()
            catch {
              case ex if NonFatal(ex) =>
                reportFailure(ex)
            }

            // have to retry execution, as those pending tasks
            // may have registered new tasks for immediate execution
            val time2 = currentClock - head.runsAt
            if (time != time2 || maxIterCount == 0) {
              loop(time2, 0, 0)
            } else {
              val iterCount2 = iterCount + 1
              if (iterCount2 < maxIterCount)
                loop(time2, iterCount2, maxIterCount)
            }
          }

        case None =>
          if (!stateRef.compareAndSet(current, current.copy(clock = currentClock)))
            loop(time, iterCount, maxIterCount)
      }
    }

    loop(time, 0, maxImmediateTasks.getOrElse(0))
  }

  @tailrec
  private def cancelTask(t: Task): Unit = {
    val current: State = stateRef.get()
    val update = current.copy(tasks = current.tasks - t)
    if (!stateRef.compareAndSet(current, update)) cancelTask(t)
  }

  private def extractOneTask(current: State, clock: FiniteDuration): Option[(Task, SortedSet[Task])] = {
    current.tasks.headOption.filter(_.runsAt <= clock) match {
      case Some(value) =>
        val firstTick = value.runsAt
        val forExecution = {
          val arr = current.tasks.iterator.takeWhile(_.runsAt == firstTick).take(10).toArray
          arr(Random.nextInt(arr.length))
        }

        val remaining = current.tasks - forExecution
        Some((forExecution, remaining))

      case None =>
        None
    }
  }
}

object TestScheduler {
  /** Builder for [[TestScheduler]]. */
  def apply(): TestScheduler =
    apply(ExecutionModel.Default)

  /** Builder for [[TestScheduler]]. */
  def apply(executionModel: ExecutionModel): TestScheduler = {
    val state = AtomicAny(
      State(
        lastID = 0,
        clock = Duration.Zero,
        tasks = SortedSet.empty[Task],
        lastReportedError = null
      ))

    new TestScheduler(state, executionModel)
  }

  /** Used internally by [[TestScheduler]], represents a
    * unit of work pending execution.
    */
  final case class Task(id: Long, task: Runnable, runsAt: FiniteDuration)

  object Task {
    /**
      * Total ordering, making `Task` amendable for usage with an
      * `OrderedSet`.
      */
    implicit val ordering: Ordering[Task] =
      new Ordering[Task] {
        val longOrd = implicitly[Ordering[Long]]

        def compare(x: Task, y: Task): Int =
          x.runsAt.compare(y.runsAt) match {
            case nonZero if nonZero != 0 =>
              nonZero
            case _ =>
              longOrd.compare(x.id, y.id)
          }
      }
  }

  /** Used internally by [[TestScheduler]], represents the internal
    * state used for task scheduling and execution.
    */
  final case class State(lastID: Long, clock: FiniteDuration, tasks: SortedSet[Task], lastReportedError: Throwable) {

    // $COVERAGE-OFF$
    assert(!tasks.headOption.exists(_.runsAt < clock), "The runsAt for any task must never be in the past")
    // $COVERAGE-ON$
  }

  private def execute(state: State, runnable: Runnable): State = {
    val newID = state.lastID + 1
    val task = Task(newID, runnable, state.clock)
    state.copy(lastID = newID, tasks = state.tasks + task)
  }

  private def scheduleOnce(
    state: State,
    delay: FiniteDuration,
    r: Runnable,
    cancelTask: Task => Unit): (Cancelable, State) = {
    // $COVERAGE-OFF$
    require(delay >= Duration.Zero, "The given delay must be positive")
    // $COVERAGE-ON$

    val newID = state.lastID + 1
    SingleAssignCancelable()
    val task = Task(newID, r, state.clock + delay)
    val cancelable = new Cancelable {
      def cancel(): Unit = cancelTask(task)
      override def toString = {
        // $COVERAGE-OFF$
        s"monix.execution.schedulers.TestScheduler.TaskCancelable@$hashCode"
        // $COVERAGE-ON$
      }
    }

    (
      cancelable,
      state.copy(
        lastID = newID,
        tasks = state.tasks + task
      ))
  }
}
