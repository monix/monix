package monifu.rx.api

import scala.concurrent.ExecutionContext
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.duration.FiniteDuration
import monifu.rx.{AsyncObserver, AsyncObservable}


final class AsyncObservableBuilder(implicit val context: ExecutionContext)
  extends AnyVal with ObservableTypeClass[AsyncObservable] {

  type O[-I] = AsyncObserver[I]

  def create[T](f: AsyncObserver[T] => Cancelable): AsyncObservable[T] =
    AsyncObservable.create(f)

  def empty[A]: AsyncObservable[A] =
    AsyncObservable.empty

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): AsyncObservable[A] =
    AsyncObservable.unit(elem)

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): AsyncObservable[Nothing] =
    AsyncObservable.error(ex)

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never: AsyncObservable[Nothing] =
    AsyncObservable.never

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](seq: TraversableOnce[T]): AsyncObservable[T] =
    AsyncObservable.fromTraversable(seq)

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: AsyncObservable[T]*): AsyncObservable[T] =
    AsyncObservable.flatten(sources: _*)

  def interval(initialDelay: FiniteDuration, period: FiniteDuration, s: Scheduler): AsyncObservable[Long] =
    AsyncObservable.interval(initialDelay, period, s)
}
