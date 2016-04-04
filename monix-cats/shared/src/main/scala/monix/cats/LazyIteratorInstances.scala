package monix.cats

import cats._
import monix.eval.LazyIterator

trait LazyIteratorInstances {
  implicit val lazyIteratorInstances: Sequenceable[LazyIterator] =
    new MonadFilter[LazyIterator] with MonadError[LazyIterator, Throwable]
      with CoflatMap[LazyIterator] with MonadCombine[LazyIterator] {

      def empty[A]: LazyIterator[A] =
        LazyIterator.empty[A]
      def raiseError[A](e: Throwable): LazyIterator[A] =
        LazyIterator.error(e)
      def pure[A](x: A): LazyIterator[A] =
        LazyIterator.now(x)
      override def pureEval[A](x: Eval[A]): LazyIterator[A] =
        LazyIterator.evalAlways(x.value)

      def flatMap[A, B](fa: LazyIterator[A])(f: (A) => LazyIterator[B]): LazyIterator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: LazyIterator[A])(f: (LazyIterator[A]) => B): LazyIterator[B] =
        LazyIterator.evalAlways(f(fa))
      override def coflatten[A](fa: LazyIterator[A]): LazyIterator[LazyIterator[A]] =
        LazyIterator.now(fa)
      def handleErrorWith[A](fa: LazyIterator[A])(f: (Throwable) => LazyIterator[A]): LazyIterator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: LazyIterator[A])(f: (A) => Boolean): LazyIterator[A] =
        fa.filter(f)
      override def map[A, B](fa: LazyIterator[A])(f: (A) => B): LazyIterator[B] =
        fa.map(f)
      override def handleError[A](fa: LazyIterator[A])(f: (Throwable) => A): LazyIterator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: LazyIterator[A], y: LazyIterator[A]): LazyIterator[A] =
        x ++ y
    }
}