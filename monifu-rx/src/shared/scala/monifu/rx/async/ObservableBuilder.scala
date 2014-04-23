package monifu.rx.async

import monifu.rx.base.{ObservableTypeClass => ObservableBuilderBase}
import scala.concurrent.ExecutionContext
import monifu.concurrent.{Scheduler, Cancelable}
import scala.concurrent.duration.FiniteDuration


final class ObservableBuilder(implicit val context: ExecutionContext)
  extends AnyVal with ObservableBuilderBase[Observable] {

  type O[-I] = monifu.rx.async.Observer[I]

  def create[T](f: Observer[T] => Cancelable): Observable[T] =
    Observable.create(f)

  def empty[A]: Observable[A] =
    Observable.empty

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): Observable[A] =
    Observable.unit(elem)

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): Observable[Nothing] =
    Observable.error(ex)

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never: Observable[Nothing] =
    Observable.never

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](seq: TraversableOnce[T]): Observable[T] =
    Observable.fromTraversable(seq)

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*): Observable[T] =
    Observable.flatten(sources: _*)

  def interval(initialDelay: FiniteDuration, period: FiniteDuration, s: Scheduler): Observable[Long] =
    Observable.interval(initialDelay, period, s)
}
