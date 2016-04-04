package monix.cats

import cats._
import monix.eval.AsyncIterator

trait AsyncIteratorInstances {
  implicit val asyncIteratorInstances: Sequenceable[AsyncIterator] =
    new MonadFilter[AsyncIterator] with MonadError[AsyncIterator, Throwable]
      with CoflatMap[AsyncIterator] with MonadCombine[AsyncIterator] {

      def empty[A]: AsyncIterator[A] =
        AsyncIterator.empty[A]
      def raiseError[A](e: Throwable): AsyncIterator[A] =
        AsyncIterator.error(e)
      def pure[A](x: A): AsyncIterator[A] =
        AsyncIterator.now(x)
      override def pureEval[A](x: Eval[A]): AsyncIterator[A] =
        AsyncIterator.evalAlways(x.value)

      def flatMap[A, B](fa: AsyncIterator[A])(f: (A) => AsyncIterator[B]): AsyncIterator[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: AsyncIterator[A])(f: (AsyncIterator[A]) => B): AsyncIterator[B] =
        AsyncIterator.evalAlways(f(fa))
      override def coflatten[A](fa: AsyncIterator[A]): AsyncIterator[AsyncIterator[A]] =
        AsyncIterator.now(fa)
      def handleErrorWith[A](fa: AsyncIterator[A])(f: (Throwable) => AsyncIterator[A]): AsyncIterator[A] =
        fa.onErrorHandleWith(f)

      override def filter[A](fa: AsyncIterator[A])(f: (A) => Boolean): AsyncIterator[A] =
        fa.filter(f)
      override def map[A, B](fa: AsyncIterator[A])(f: (A) => B): AsyncIterator[B] =
        fa.map(f)
      override def handleError[A](fa: AsyncIterator[A])(f: (Throwable) => A): AsyncIterator[A] =
        fa.onErrorHandle(f)

      def combineK[A](x: AsyncIterator[A], y: AsyncIterator[A]): AsyncIterator[A] =
        x ++ y
    }
}
