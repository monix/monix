package monix.cats

import cats._
import monix.reactive.Observable

trait ObservableInstances {
  implicit val observableInstances: Sequenceable[Observable] =
    new MonadFilter[Observable] with MonadError[Observable, Throwable]
      with CoflatMap[Observable] with MonadCombine[Observable] {

      def flatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: Observable[A])(f: (Observable[A]) => B): Observable[B] =
        Observable.evalAlways(f(fa))
      override def coflatten[A](fa: Observable[A]): Observable[Observable[A]] =
        Observable.now(fa)
      def handleErrorWith[A](fa: Observable[A])(f: (Throwable) => Observable[A]): Observable[A] =
        fa.onErrorHandleWith(f)
      def raiseError[A](e: Throwable): Observable[A] =
        Observable.error(e)
      def pure[A](x: A): Observable[A] =
        Observable.now(x)

      override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
        fa.map(f)
      override def handleError[A](fa: Observable[A])(f: (Throwable) => A): Observable[A] =
        fa.onErrorHandle(f)
      override def pureEval[A](x: Eval[A]): Observable[A] =
        Observable.evalAlways(x.value)

      def empty[A]: Observable[A] =
        Observable.empty[A]
      override def filter[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
        fa.filter(f)

      def combineK[A](x: Observable[A], y: Observable[A]): Observable[A] =
        x ++ y
    }
}