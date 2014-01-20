package monifu.rx

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import java.util.Date

trait Scheduler extends ExecutionContext {

  /**
   * Schedules a cancelable action to be executed.
   *
   * @param action Action to schedule.
   * @return a subscription to be able to unsubscribe from action.
   */
  def schedule(action: Scheduler => Subscription): Subscription

  /**
   * Schedules an action to be executed.
   *
   * @param action action to execute
   * @return a subscription to be able to unsubscribe from action.
   */
  def schedule(action: => Unit): Subscription

  /**
   * Schedules a cancelable action to be executed in delayTime.
   *
   * @param action Action to schedule.
   * @param delay  Time the action is to be delayed before executing.
   * @return a subscription to be able to unsubscribe from action.
   */
  def schedule(delay: FiniteDuration, action: Scheduler => Subscription): Subscription

  /**
   * Schedules an action to be executed in delayTime.
   *
   * @param action action
   * @return a subscription to be able to unsubscribe from action.
   */
  def schedule(delayTime: FiniteDuration, action: => Unit): Subscription

  /**
   * Schedules a cancelable action to be executed periodically.
   *
   * This default implementation schedules recursively and waits for actions to complete (instead of potentially executing
   * long-running actions concurrently). Each scheduler that can do periodic scheduling in a better way should override this.
   *
   * @param action The action to execute periodically.
   * @param initialDelay Time to wait before executing the action for the first time.
   * @param period The time interval to wait each time in between executing the action.
   * @return A subscription to be able to unsubscribe from action.
   */
  def schedule(initialDelay: FiniteDuration, period: FiniteDuration, action: Scheduler => Subscription): Subscription

  /**
   * Schedules an action to be executed periodically.
   *
   * @param action
   *            The action to execute periodically.
   * @param initialDelay
   *            Time to wait before executing the action for the first time.
   * @param period
   *            The time interval to wait each time in between executing the action.
   * @return A subscription to be able to unsubscribe from action.
   */
  def schedule(initialDelay: FiniteDuration, period: FiniteDuration, action: => Unit): Subscription

  /**
   * Schedules a cancelable action to be executed at dueTime.
   *
   * @param action Action to schedule.
   * @param dueTime Time the action is to be executed. If in the past it will be executed immediately.
   * @return a subscription to be able to unsubscribe from action.
   */
  def scheduleAt(dueTime: Date, action: Scheduler => Subscription): Subscription

  /**
   * Schedules a cancelable action to be executed at dueTime.
   *
   * @param action Action to schedule.
   * @param dueTime Time the action is to be executed. If in the past it will be executed immediately.
   * @return a subscription to be able to unsubscribe from action.
   */
  def scheduleAt(dueTime: Date, action: => Unit): Subscription

  /**
   * Runs a block of code in this ExecutionContext.
   * Inherited from ExecutionContext.
   */
  def execute(runnable: Runnable): Unit

  /**
   * Reports that an asynchronous computation failed.
   * Inherited from ExecutionContext.
   */
  def reportFailure(t: Throwable): Unit
}

