/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
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

package monifu.concurrent.schedulers

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.schedulers.TestScheduler._
import monifu.concurrent.{Cancelable, Scheduler}

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal

/**
 * A scheduler meant for testing purposes.
 */
final class TestScheduler private () extends ReferenceScheduler {
  /*
   * The `internalClock` is used for executing tasks. Upon calling [[tick]], the
   * internal clock is advanced and pending `tasks` are executed.
   */
  val state = Atomic(State(
    lastID = 0,
    clock = Duration.Zero,
    tasks = SortedSet.empty[Task],
    cancelTask = cancelTask,
    lastReportedError = null
  ))

  override def nanoTime: Long =
    state.get.clock.toNanos

  def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable =
    state.transformAndExtract(_.scheduleOnce(initialDelay, r.run()))

  private[this] def cancelTask(t: Task): Unit =
    state.transform(s => s.copy(tasks = s.tasks - t))

  def execute(runnable: Runnable): Unit =
    state.transform(_.execute(runnable))

  def reportFailure(t: Throwable): Unit = {
    state.transform(_.copy(lastReportedError = t))
  }

  @tailrec
  def tickOne(): Boolean = {
    val current = state.get
    val firstOpt = current.tasks
      .headOption.filter(_.runsAt <= current.clock)

    firstOpt match {
      case Some(head) =>
        val rest = current.tasks.tail

        if (!state.compareAndSet(current, current.copy(tasks = rest)))
          tickOne()
        else {
          // execute task
          try head.task.run() catch {
            case NonFatal(ex) =>
              reportFailure(ex)
          }

          true
        }

      case None =>
        false
    }
  }

  def tick(time: FiniteDuration = Duration.Zero): Boolean = {
    @tailrec
    def loop(time: FiniteDuration, result: Boolean): Boolean = {
      val current = state.get
      val currentClock = current.clock + time

      val firstOpt = current.tasks
        .headOption.filter(_.runsAt <= currentClock)

      firstOpt match {
        case Some(head) =>
          val rest = current.tasks.tail

          if (!state.compareAndSet(current, current.copy(clock = head.runsAt, tasks = rest)))
            loop(time, result)
          else {
            // execute task
            try head.task.run() catch {
              case NonFatal(ex) =>
                reportFailure(ex)
            }

            // have to retry execution, as those pending tasks
            // may have registered new tasks for immediate execution
            loop(currentClock - head.runsAt, result = true)
          }

        case None =>
          if (!state.compareAndSet(current, current.copy(clock = currentClock)))
            loop(time, result)
          else
            result
      }
    }

    loop(time, result = false)
  }
}

object TestScheduler {
  /**
   * Builder for [[TestScheduler]].
   */
  def apply(): TestScheduler = {
    new TestScheduler()
  }

  /**
   * Used internally by [[TestScheduler]], represents a
   * unit of work pending execution.
   */
  case class Task(id: Long, task: Runnable, runsAt: FiniteDuration)

  object Task {
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

  /**
   * Used internally by [[TestScheduler]], represents the internal
   * state used for task scheduling and execution.
   */
  case class State(
    lastID: Long,
    clock: FiniteDuration,
    tasks: SortedSet[Task],
    cancelTask: Task => Unit,
    lastReportedError: Throwable) {

    assert(!tasks.headOption.exists(_.runsAt < clock),
      "The runsAt for any task must never be in the past")

    /**
     * Returns a new state with the runnable scheduled for execution.
     */
    def execute(runnable: Runnable): State = {
      val newID = lastID + 1
      val task = Task(newID, runnable, clock)
      copy(lastID = newID, tasks = tasks + task)
    }

    /**
     * Returns a new state with a scheduled task included.
     */
    def scheduleOnce(delay: FiniteDuration, action: => Unit): (Cancelable, State) = {
      require(delay >= Duration.Zero, "The given delay must be positive")

      val newID = lastID + 1
      val cancelable = SingleAssignmentCancelable()
      val runnable = new Runnable { def run() = if (!cancelable.isCanceled) action }

      val task = Task(newID, runnable, this.clock + delay)
      cancelable := Cancelable { cancelTask(task) }

      (cancelable, copy(
        lastID = newID,
        tasks = tasks + task
      ))
    }
  }
}
