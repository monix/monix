package monifu.rx.base

import language.higherKinds

trait ObservableBuilder[Observable[+T] <: ObservableGen[T]] extends Any {
  def empty[A]: Observable[A]

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
   * Merges the given list of ''observables'' into a single observable.
   *
   * NOTE: the result should be the same as [[monifu.rx.sync.Observable.concat concat]] and in
   *       the asynchronous version it always is.
   */
  def merge[T](sources: Observable[T]*): Observable[T]

  /**
   * Concatenates the given list of ''observables''.
   */
  def concat[T](sources: Observable[T]*): Observable[T]
}
