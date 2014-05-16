package monifu.rx.api

import language.higherKinds
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext


trait ObservableTypeClass[Observable[+T] <: ObservableLike[T, Observable]] extends Any {
  type O[-I]

  /**
   * Observable constructor. To be used for implementing new Observables and operators.
   */
  def create[T](f: O[T] => Cancelable): Observable[T]

  /**
   * Creates an Observable that doesn't emit anything.
   */
  def empty[T]: Observable[T]

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): Observable[A]

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): Observable[Nothing]

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never: Observable[Nothing]

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](sequence: TraversableOnce[T]): Observable[T]

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param period the delay between two emitted events
   * @param ec the execution context in which `onNext` will get called
   */
  final def interval(period: FiniteDuration)(implicit ec: ExecutionContext): Observable[Long] =
    interval(period, Scheduler.fromContext)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param period the delay between two emitted events
   * @param s the scheduler to use for scheduling the next event and for triggering `onNext`
   */
  final def interval(period: FiniteDuration, s: Scheduler): Observable[Long] =
    interval(period, period, s)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param initialDelay the initial delay to wait before the first emitted number
   * @param period the delay between two subsequent events
   * @param ec the execution context in which `onNext` will get called
   */
  final def interval(initialDelay: FiniteDuration, period: FiniteDuration)(implicit ec: ExecutionContext): Observable[Long] =
    interval(initialDelay, period, Scheduler.fromContext)

  /**
   * Creates an Observable that emits auto-incremented natural numbers with a fixed delay,
   * starting from number 1.
   *
   * @param initialDelay the initial delay to wait before the first emitted number
   * @param period the delay between two subsequent events
   * @param s the scheduler to use for scheduling the next event and for triggering `onNext`
   */
  def interval(initialDelay: FiniteDuration, period: FiniteDuration, s: Scheduler): Observable[Long]

  /**
   * Merges the given list of ''observables'' into a single observable.
   *
   * NOTE: the result should be the same as [[monifu.rx.sync.Observable.concat concat]] and in
   *       the asynchronous version it always is.
   */
  def flatten[T](sources: Observable[T]*): Observable[T]
}
