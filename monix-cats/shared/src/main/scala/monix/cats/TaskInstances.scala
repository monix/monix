package monix.cats

import _root_.cats._
import _root_.algebra.{Group, Monoid, Semigroup}
import monix.eval.Task

/** Provides Cats compatibility for the [[Task]] type. */
trait TaskInstances extends TaskInstances2 {
  implicit val taskInstances: Deferrable[Task] =
    new MonadError[Task, Throwable] with CoflatMap[Task] {
      def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
        fa.flatMap(f)
      def coflatMap[A, B](fa: Task[A])(f: (Task[A]) => B): Task[B] =
        Task.evalAlways(f(fa))
      def handleErrorWith[A](fa: Task[A])(f: (Throwable) => Task[A]): Task[A] =
        fa.onErrorHandleWith(f)
      def raiseError[A](e: Throwable): Task[A] =
        Task.error(e)
      def pure[A](x: A): Task[A] =
        Task.now(x)

      override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
        fa.map(f)
      override def handleError[A](fa: Task[A])(f: (Throwable) => A): Task[A] =
        fa.onErrorHandle(f)
      override def pureEval[A](x: Eval[A]): Task[A] =
        Task.evalAlways(x.value)
      override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
        Task.mapBoth(fa,fb)(f)
    }
}

private[cats] trait TaskInstances2 extends TaskInstances1 {
  implicit def taskGroup[A](implicit A: Group[A]): Group[Task[A]] =
    new Group[Task[A]] {
      val empty: Task[A] = Task.now(A.empty)
      def combine(x: Task[A], y: Task[A]): Task[A] =
        x.zipAsyncWith(y)(A.combine)
      def inverse(a: Task[A]): Task[A] =
        a.map(A.inverse)
    }
}

private[cats] trait TaskInstances1 extends TaskInstances0 {
  implicit def taskMonoid[A](implicit A: Monoid[A]): Monoid[Task[A]] =
    new Monoid[Task[A]] {
      val empty: Task[A] = Task.now(A.empty)
      def combine(x: Task[A], y: Task[A]): Task[A] =
        x.zipAsyncWith(y)(A.combine)
    }
}

private[cats] trait TaskInstances0 {
  implicit def taskSemigroup[A](implicit A: Semigroup[A]): Semigroup[Task[A]] =
    new Semigroup[Task[A]] {
      def combine(x: Task[A], y: Task[A]): Task[A] =
        x.zipAsyncWith(y)(A.combine)
    }
}