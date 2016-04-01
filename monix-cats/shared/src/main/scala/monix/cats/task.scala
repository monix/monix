package monix.cats

import _root_.algebra.{Monoid, Semigroup, Group}
import cats.{Monad, Eval, CoflatMap, MonadError}
import monix.async.{Coeval, Task}

/** Provides Cats compatibility for the
  * [[Task]] and [[Coeval]] types.
  */
trait TaskInstances extends CoevalInstances3

private[cats] trait TaskInstances0 {
  implicit def taskSemigroup[A](implicit A: Semigroup[A]): Semigroup[Task[A]] =
    new Semigroup[Task[A]] {
      def combine(x: Task[A], y: Task[A]): Task[A] =
        x.zipAsyncWith(y)(A.combine)
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

private[cats] trait TaskInstances3 extends TaskInstances2 {
  type TaskType = MonadError[Task, Throwable] with CoflatMap[Task]

  implicit val taskInstances: TaskType =
    new TaskType {
      def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
        fa.flatMapAsync(f)
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

private[cats] trait CoevalInstances0 extends TaskInstances3 {
  implicit def coevalSemigroup[A](implicit A: Semigroup[A]): Semigroup[Coeval[A]] =
    new Semigroup[Coeval[A]] {
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipEvalWith(y)(A.combine)
    }
}

private[cats] trait CoevalInstances1 extends CoevalInstances0 {
  implicit def coevalMonoid[A](implicit A: Monoid[A]): Monoid[Coeval[A]] =
    new Monoid[Coeval[A]] {
      val empty: Coeval[A] = Coeval.now(A.empty)
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipEvalWith(y)(A.combine)
    }
}

private[cats] trait CoevalInstances2 extends CoevalInstances1 {
  implicit def coevalGroup[A](implicit A: Group[A]): Group[Coeval[A]] =
    new Group[Coeval[A]] {
      val empty: Coeval[A] = Coeval.now(A.empty)
      def combine(x: Coeval[A], y: Coeval[A]): Coeval[A] =
        x.zipEvalWith(y)(A.combine)
      def inverse(a: Coeval[A]): Coeval[A] =
        a.map(A.inverse)
    }
}

private[cats] trait CoevalInstances3 extends CoevalInstances2 {
  type CoevalType = Monad[Coeval] with CoflatMap[Coeval]

  implicit val coevalInstances: CoevalType =
    new CoevalType {
      def flatMap[A, B](fa: Coeval[A])(f: (A) => Coeval[B]): Coeval[B] =
        fa.flatMapEval(f)
      def coflatMap[A, B](fa: Coeval[A])(f: (Coeval[A]) => B): Coeval[B] =
        Coeval.evalAlways(f(fa))
      def pure[A](x: A): Coeval[A] =
        Coeval.now(x)

      override def map[A, B](fa: Coeval[A])(f: (A) => B): Coeval[B] =
        fa.map(f)
      override def pureEval[A](x: Eval[A]): Coeval[A] =
        Coeval.evalAlways(x.value)
      override def map2[A, B, Z](fa: Coeval[A], fb: Coeval[B])(f: (A, B) => Z): Coeval[Z] =
        fa.zipEvalWith(fb)(f)
    }
}