package monifu.rx

import monifu.concurrent.Cancelable
import monifu.concurrent.cancelables.SingleAssignmentCancelable

trait SafeObservable[+A] extends Observable[A] {
  import monifu.rx.SafeObservable.defaultConstructor

  override final def subscribe(observer: Observer[A]): Cancelable = {
    val sub = SingleAssignmentCancelable()
    sub := fn(SafeObserver(observer, sub))
    sub
  }

  override final def Observable[B](subscribe: (Observer[B]) => Cancelable): Observable[B] =
    defaultConstructor(subscribe)
}

object SafeObservable {
  private def defaultConstructor[A](f: Observer[A] => Cancelable): SafeObservable[A] =
    new SafeObservable[A] {
      def fn(observer: Observer[A]) =
        f(observer)
    }

  def apply[A](f: Observer[A] => Cancelable): SafeObservable[A] =
    defaultConstructor(f)
}