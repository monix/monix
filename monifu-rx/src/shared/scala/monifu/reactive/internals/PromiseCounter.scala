package monifu.reactive.internals

import scala.concurrent.Promise
import monifu.concurrent.atomic.padded.Atomic

/**
 * Represents a Promise that completes with `value` after
 * receiving a `countdownUntil` number of `countdown()` calls.
 */
final class PromiseCounter[T] private (value: T, countdownUntil: Int) {
  require(countdownUntil > 0, "countdownUntil must be strictly positive")

  private[this] val promise = Promise[T]()
  private[this] val counter = Atomic(0)

  def future = promise.future

  def countdown(): Unit =
    if (counter.incrementAndGet() >= countdownUntil) {
      promise.success(value)
    }

  def success(value: T) = {
    promise.success(value)
  }
}

object PromiseCounter {
  def apply[T](value: T, countDown: Int): PromiseCounter[T] =
    new PromiseCounter[T](value, countDown)
}