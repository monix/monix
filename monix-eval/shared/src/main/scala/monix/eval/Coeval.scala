/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.eval

import monix.eval.Coeval._
import monix.types.Evaluable
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** `Coeval` represents lazy computations that can execute synchronously.
  *
  * Word definition and origin:
  *
  *  - Having the same age or date of origin; a contemporary; synchronous.
  *  - From the Latin "coævus": com- ‎("equal") in combination with aevum ‎(aevum, "age").
  *  - The constructor of `Coeval` is the dual of an expression that evaluates to an `A`.
  *
  * There are three evaluation strategies:
  *
  *  - [[monix.eval.Coeval.Now Now]]: evaluated immediately
  *  - [[monix.eval.Coeval.Error Error]]: evaluated immediately, representing an error
  *  - [[monix.eval.Coeval.EvalOnce EvalOnce]]: evaluated a single time
  *  - [[monix.eval.Coeval.EvalAlways EvalAlways]]: evaluated every time the value is needed
  *
  * The `EvalOnce` and `EvalAlways` are both lazy strategies while
  * `Now` and `Error` are eager. `EvalOnce` and `EvalAlways` are
  * distinguished from each other only by memoization: once evaluated
  * `EvalOnce` will save the value to be returned immediately if it is
  * needed again. `EvalAlways` will run its computation every time.
  *
  * `Coeval` supports stack-safe lazy computation via the .map and .flatMap
  * methods, which use an internal trampoline to avoid stack overflows.
  * Computation done within .map and .flatMap is always done lazily,
  * even when applied to a `Now` instance.
  */
sealed abstract class Coeval[+A] extends Serializable with Product { self =>
  /** Evaluates the underlying computation and returns the result.
    *
    * NOTE: this can throw exceptions.
    */
  def value: A = runAttempt match {
    case Now(value) => value
    case Error(ex) => throw ex
  }

  /** Evaluates the underlying computation and returns the
    * result or any triggered errors as a [[Coeval.Attempt]].
    */
  def runAttempt: Attempt[A] =
    Coeval.trampoline(this, Nil)

  /** Evaluates the underlying computation and returns the
    * result or any triggered errors as a `scala.util.Try`.
    */
  def runTry: Try[A] =
    Coeval.trampoline(this, Nil).asScala

  /** Converts the source [[Coeval]] into a [[Task]]. */
  def task: Task[A] =
    self match {
      case Now(a) => Task.Now(a)
      case Error(ex) => Task.Error(ex)
      case EvalOnce(thunk) => Task.EvalOnce(thunk)
      case EvalAlways(thunk) => Task.EvalAlways(thunk)
      case Suspend(thunk) => Task.Suspend(() => thunk().task)
      case other => Task.evalAlways(other.value)
    }

  /** Creates a new `Coeval` by applying a function to the successful result
    * of the source, and returns a new instance equivalent
    * to the result of the function.
    */
  def flatMap[B](f: A => Coeval[B]): Coeval[B] =
    self match {
      case Now(a) =>
        Suspend(() => try f(a) catch { case NonFatal(ex) => Error(ex) })
      case eval @ EvalOnce(_) =>
        Suspend(() => eval.runAttempt match {
          case Now(a) => try f(a) catch { case NonFatal(ex) => Error(ex) }
          case error @ Error(_) => error
        })
      case EvalAlways(thunk) =>
        Suspend(() => try f(thunk()) catch {
          case NonFatal(ex) => Error(ex)
        })
      case Suspend(thunk) =>
        BindSuspend(thunk, f)
      case BindSuspend(thunk, g) =>
        Suspend(() => BindSuspend(thunk, g andThen (_ flatMap f)))
      case error @ Error(_) =>
        error
    }


  /** Given a source Coeval that emits another Coeval, this function
    * flattens the result, returning a Coeval equivalent to the emitted
    * Coeval by the source.
    */
  def flatten[B](implicit ev: A <:< Coeval[B]): Coeval[B] =
    flatMap(a => a)

  /** Returns a failed projection of this coeval.
    *
    * The failed projection is a future holding a value of type
    * `Throwable`, emitting a value which is the throwable of the
    * original coeval in case the original coeval fails, otherwise if the
    * source succeeds, then it fails with a `NoSuchElementException`.
    */
  def failed: Coeval[Throwable] =
    materializeAttempt.flatMap {
      case Error(ex) => Now(ex)
      case Now(_) => Error(new NoSuchElementException("failed"))
    }

  /** Returns a new Coeval that applies the mapping function to
    * the element emitted by the source.
    */
  def map[B](f: A => B): Coeval[B] =
    flatMap(a => try Now(f(a)) catch { case NonFatal(ex) => Error(ex) })


  /** Creates a new [[Coeval]] that will expose any triggered error from
    * the source.
    */
  def materialize: Coeval[Try[A]] =
    materializeAttempt.map(_.asScala)

  /** Creates a new [[Coeval]] that will expose any triggered error from
    * the source.
    */
  def materializeAttempt: Coeval[Attempt[A]] =
    self match {
      case now @ Now(_) =>
        Now(now)
      case eval @ EvalOnce(_) =>
        Suspend(() => Now(eval.runAttempt))
      case EvalAlways(thunk) =>
        Suspend(() => Now(try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }))
      case Error(ex) =>
        Now(Error(ex))
      case Suspend(thunk) =>
        Suspend(() => try thunk().materializeAttempt catch { case NonFatal(ex) => Now(Error(ex)) })
      case BindSuspend(thunk, g) =>
        BindSuspend[Attempt[Any], Attempt[A]](
          () => try thunk().materializeAttempt catch { case NonFatal(ex) => Now(Error(ex)) },
          result => result match {
            case Now(any) =>
              try { g.asInstanceOf[Any => Coeval[A]](any).materializeAttempt }
              catch { case NonFatal(ex) => Now(Error(ex)) }
            case Error(ex) =>
              Now(Error(ex))
          })
    }

  /** Dematerializes the source's result from a `Try`. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Coeval[B] =
    self.asInstanceOf[Coeval[Try[B]]].flatMap(Attempt.fromTry)

  /** Dematerializes the source's result from an `Attempt`. */
  def dematerializeAttempt[B](implicit ev: A <:< Attempt[B]): Coeval[B] =
    self.asInstanceOf[Coeval[Attempt[B]]].flatMap(identity)

  /** Creates a new coeval that will try recovering from an error by
    * matching it with another coeval using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Coeval[B]]): Coeval[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, Coeval.error))

  /** Creates a new coeval that will handle any matching throwable that
    * this coeval might emit by executing another coeval.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  def onErrorHandleWith[B >: A](f: Throwable => Coeval[B]): Coeval[B] =
    self.materializeAttempt.flatMap {
      case now @ Now(_) => now
      case Error(ex) => try f(ex) catch { case NonFatal(err) => Error(err) }
    }

  /** Creates a new coeval that in case of error will fallback to the
    * given backup coeval.
    */
  def onErrorFallbackTo[B >: A](that: Coeval[B]): Coeval[B] =
    onErrorHandleWith(ex => that)

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRetry(maxRetries: Long): Coeval[A] =
    self.onErrorHandleWith(ex =>
      if (maxRetries > 0) self.onErrorRetry(maxRetries-1)
      else Error(ex))

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRetryIf(p: Throwable => Boolean): Coeval[A] =
    self.onErrorHandleWith(ex => if (p(ex)) self.onErrorRetryIf(p) else Error(ex))

  /** Creates a new coeval that will handle any matching throwable that
    * this coeval might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  def onErrorHandle[U >: A](f: Throwable => U): Coeval[U] =
    onErrorHandleWith(ex => try Now(f(ex)) catch { case NonFatal(err) => Error(err) })

  /** Creates a new coeval that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Coeval[U] =
    onErrorRecoverWith(pf.andThen(Coeval.now))

  /** Memoizes the result on the computation and reuses it on subsequent
    * invocations of `runAsync`.
    */
  def memoize: Coeval[A] =
    self match {
      case ref @ Now(_) => ref
      case error @ Error(_) => error
      case EvalAlways(thunk) => new EvalOnce[A](thunk)
      case Suspend(thunk) =>
        val evalOnce = EvalOnce(() => thunk().memoize)
        Suspend(evalOnce)
      case eval: EvalOnce[_] => self
      case BindSuspend(_,_) =>
        new EvalOnce[A](() => self.value)
    }

  /** Zips the values of `this` and `that` coeval, and creates a new coeval
    * that will emit the tuple of their results.
    */
  def zip[B](that: Coeval[B]): Coeval[(A, B)] =
    for (a <- this; b <- that) yield (a,b)

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  def zipWith[B,C](that: Coeval[B])(f: (A,B) => C): Coeval[C] =
    for (a <- this; b <- that) yield f(a,b)
}

object Coeval {
  /** Promotes a non-strict value to a [[Coeval]].
    *
    * Alias of [[evalAlways]].
    */
  def apply[A](f: => A): Coeval[A] =
    EvalAlways(f _)

  /** Returns an `Coeval` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Coeval[A] = Now(a)

  /** Returns an `Coeval` that on execution is always finishing in error
    * emitting the specified exception.
    */
  def error[A](ex: Throwable): Coeval[A] =
    Error(ex)

  /** Promote a non-strict value representing a `Coeval` to a `Coeval` of the
    * same type.
    */
  def defer[A](coeval: => Coeval[A]): Coeval[A] =
    Suspend(() => coeval)

  /** Promote a non-strict value to a `Coeval` that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](f: => A): Coeval[A] =
    EvalOnce(f _)

  /** Promote a non-strict value to an `Coeval`, catching exceptions in the
    * process.
    *
    * Note that since `Coeval` is not memoized, this will recompute the
    * value each time the `Coeval` is executed.
    */
  def evalAlways[A](f: => A): Coeval[A] =
    EvalAlways(f _)

  /** A `Coeval[Unit]` provided for convenience. */
  val unit: Coeval[Unit] = Now(())

  /** Zips together multiple [[Coeval]] instances. */
  def zipList[A](sources: Seq[Coeval[A]]): Coeval[List[A]] = {
    val init = mutable.ListBuffer.empty[A]
    val r = sources.foldLeft(now(init))((acc,elem) => acc.zipWith(elem)(_ += _))
    r.map(_.toList)
  }

  /** Pairs two [[Coeval]] instances. */
  def zip2[A1,A2,R](fa1: Coeval[A1], fa2: Coeval[A2]): Coeval[(A1,A2)] =
    fa1.zipWith(fa2)((_,_))

  /** Pairs two [[Coeval]] instances, creating a new instance that will apply
    * the given mapping function to the resulting pair. */
  def zipWith2[A1,A2,R](fa1: Coeval[A1], fa2: Coeval[A2])(f: (A1,A2) => R): Coeval[R] =
    fa1.zipWith(fa2)(f)

  /** Pairs three [[Coeval]] instances. */
  def zip3[A1,A2,A3](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3]): Coeval[(A1,A2,A3)] =
    zipWith3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))
  /** Pairs four [[Coeval]] instances. */
  def zip4[A1,A2,A3,A4](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4]): Coeval[(A1,A2,A3,A4)] =
    zipWith4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))
  /** Pairs five [[Coeval]] instances. */
  def zip5[A1,A2,A3,A4,A5](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5]): Coeval[(A1,A2,A3,A4,A5)] =
    zipWith5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))
  /** Pairs six [[Coeval]] instances. */
  def zip6[A1,A2,A3,A4,A5,A6](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5], fa6: Coeval[A6]): Coeval[(A1,A2,A3,A4,A5,A6)] =
    zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Pairs three [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith3[A1,A2,A3,R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3])(f: (A1,A2,A3) => R): Coeval[R] = {
    val fa12 = zip2(fa1, fa2)
    zipWith2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  /** Pairs four [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith4[A1,A2,A3,A4,R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4])(f: (A1,A2,A3,A4) => R): Coeval[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipWith2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  /** Pairs five [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith5[A1,A2,A3,A4,A5,R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5])(f: (A1,A2,A3,A4,A5) => R): Coeval[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipWith2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  /** Pairs six [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith6[A1,A2,A3,A4,A5,A6,R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5], fa6: Coeval[A6])(f: (A1,A2,A3,A4,A5,A6) => R): Coeval[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipWith2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }

  /** The `Attempt` represents a strict, already evaluated result
    * of a [[Coeval]] that either resulted in success, wrapped in a
    * [[Now]], or in an error, wrapped in a [[Error]].
    *
    * It's the moral equivalent of `scala.util.Try`.
    */
  sealed abstract class Attempt[+A] extends Coeval[A] { self =>
    /** Returns true if value is a successful one. */
    def isSuccess: Boolean = this match { case Now(_) => true; case _ => false }

    /** Returns true if result is an error. */
    def isFailure: Boolean = this match { case Error(_) => true; case _ => false }

    override def failed: Attempt[Throwable] =
      self match {
        case Now(_) => Error(new NoSuchElementException("failed"))
        case Error(ex) => Now(ex)
      }

    /** Converts this attempt into a `scala.util.Try`. */
    def asScala: Try[A] =
      this match {
        case Now(a) => Success(a)
        case Error(ex) => Failure(ex)
      }

    override def materializeAttempt: Attempt[Attempt[A]] =
      self match {
        case now @ Now(_) =>
          Now(now)
        case Error(ex) =>
          Now(Error(ex))
      }

    override def dematerializeAttempt[B](implicit ev: <:<[A, Attempt[B]]): Attempt[B] =
      self match {
        case Now(now) => now
        case error @ Error(_) => error
      }

    override def memoize: Attempt[A] = this
  }

  object Attempt {
    /** Promotes a non-strict value to a [[Coeval.Attempt]]. */
    def apply[A](f: => A): Attempt[A] =
      try Now(f) catch { case NonFatal(ex) => Error(ex) }

    /** Builds a [[Task.Attempt]] from a `scala.util.Try` */
    def fromTry[A](value: Try[A]): Attempt[A] =
      value match {
        case Success(a) => Now(a)
        case Failure(ex) => Error(ex)
      }
  }

  /** Constructs an eager [[Coeval]] instance from a strict
    * value that's already known.
    */
  final case class Now[+A](override val value: A) extends Attempt[A] {
    override def runAttempt: Now[A] = this
  }

  /** Constructs an eager [[Coeval]] instance for
    * a result that represents an error.
    */
  final case class Error(ex: Throwable) extends Attempt[Nothing] {
    override def value: Nothing = throw ex
    override def runAttempt: Error = this
  }

  /** Constructs a lazy [[Coeval]] instance that gets evaluated
    * only once.
    *
    * In some sense it is equivalent to using a lazy val.
    * When caching is not required or desired,
    * prefer [[EvalAlways]] or [[Now]].
    */
  final class EvalOnce[+A](f: () => A) extends Coeval[A] with (() => A) {
    private[this] var thunk: () => A = f

    def apply(): A = runAttempt match {
      case Now(a) => a
      case Error(ex) => throw ex
    }

    override lazy val runAttempt: Attempt[A] = {
      val result = try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }
      thunk = null
      result
    }

    override def equals(other: Any): Boolean = other match {
      case that: EvalOnce[_] => runAttempt == that.runAttempt
      case _ => false
    }

    override def hashCode(): Int =
      runAttempt.hashCode()

    def productArity: Int = 1
    def productElement(n: Int): Any = runAttempt
    def canEqual(that: Any): Boolean =
      that.isInstanceOf[EvalOnce[_]]
  }

  object EvalOnce {
    /** Builder for an [[EvalOnce]] instance. */
    def apply[A](a: () => A): EvalOnce[A] =
      new EvalOnce[A](a)

    /** Deconstructs an [[EvalOnce]] instance. */
    def unapply[A](coeval: EvalOnce[A]): Some[() => A] =
      Some(coeval)
  }

  /** Constructs a lazy [[Coeval]] instance.
    *
    * This type can be used for "lazy" values. In some sense it is
    * equivalent to using a Function0 value.
    */
  final case class EvalAlways[+A](f: () => A) extends Coeval[A] {
    override def value: A = f()
    override def runAttempt: Attempt[A] =
      try Now(f()) catch { case NonFatal(ex) => Error(ex) }
  }

  /** Internal state, the result of [[Coeval.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Coeval[A]) extends Coeval[A]
  /** Internal [[Coeval]] state that is the result of applying `flatMap`. */
  private[eval] final case class BindSuspend[A,B](thunk: () => Coeval[A], f: A => Coeval[B]) extends Coeval[B]

  private type Current = Coeval[Any]
  private type Bind = Any => Coeval[Any]

  /** Trampoline for lazy evaluation. */
  private[eval] def trampoline[A](source: Coeval[A], binds: List[Bind]): Attempt[A] = {
    @tailrec  def reduceCoeval(source: Coeval[Any], binds: List[Bind]): Attempt[Any] = {
      source match {
        case error @ Error(_) => error
        case now @ Now(a) =>
          binds match {
            case Nil => now
            case f :: rest =>
              val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
              reduceCoeval(fa, rest)
          }

        case eval @ EvalOnce(_) =>
          eval.runAttempt match {
            case now @ Now(a) =>
              binds match {
                case Nil => now
                case f :: rest =>
                  val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
                  reduceCoeval(fa, rest)
              }
            case error @ Error(_) =>
              error
          }

        case EvalAlways(thunk) =>
          val fa = try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }
          reduceCoeval(fa, binds)

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          reduceCoeval(fa, binds)

        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          reduceCoeval(fa, f.asInstanceOf[Bind] :: binds)
      }
    }

    reduceCoeval(source, Nil).asInstanceOf[Attempt[A]]
  }

  /** Implicit type-class instances of [[Coeval]]. */
  implicit val instances: Evaluable[Coeval] =
    new Evaluable[Coeval] {
      def point[A](a: A): Coeval[A] = Coeval.now(a)
      def now[A](a: A): Coeval[A] = Coeval.now(a)
      def unit: Coeval[Unit] = Coeval.unit
      def evalAlways[A](f: => A): Coeval[A] = Coeval.evalAlways(f)
      def evalOnce[A](f: => A): Coeval[A] = Coeval.evalOnce(f)
      def error[A](ex: Throwable): Coeval[A] = Coeval.error(ex)
      def defer[A](fa: => Coeval[A]): Coeval[A] = Coeval.defer(fa)
      def memoize[A](fa: Coeval[A]): Coeval[A] = fa.memoize
      def task[A](fa: Coeval[A]): Task[A] = fa.task

      def flatten[A](ffa: Coeval[Coeval[A]]): Coeval[A] = ffa.flatten
      def flatMap[A, B](fa: Coeval[A])(f: (A) => Coeval[B]): Coeval[B] = fa.flatMap(f)
      def map[A, B](fa: Coeval[A])(f: (A) => B): Coeval[B] = fa.map(f)

      def onErrorRetryIf[A](fa: Coeval[A])(p: (Throwable) => Boolean): Coeval[A] =
        fa.onErrorRetryIf(p)
      def onErrorRetry[A](fa: Coeval[A], maxRetries: Long): Coeval[A] =
        fa.onErrorRetry(maxRetries)
      def onErrorRecover[A](fa: Coeval[A])(pf: PartialFunction[Throwable, A]): Coeval[A] =
        fa.onErrorRecover(pf)
      def onErrorRecoverWith[A](fa: Coeval[A])(pf: PartialFunction[Throwable, Coeval[A]]): Coeval[A] =
        fa.onErrorRecoverWith(pf)
      def onErrorHandle[A](fa: Coeval[A])(f: (Throwable) => A): Coeval[A] =
        fa.onErrorHandle(f)
      def onErrorHandleWith[A](fa: Coeval[A])(f: (Throwable) => Coeval[A]): Coeval[A] =
        fa.onErrorHandleWith(f)
      def onErrorFallbackTo[A](fa: Coeval[A], fallback: Coeval[A]): Coeval[A] =
        fa.onErrorFallbackTo(fallback)

      def failed[A](fa: Coeval[A]): Coeval[Throwable] = fa.failed
      def materialize[A](fa: Coeval[A]): Coeval[Try[A]] = fa.materialize
      def dematerialize[A](fa: Coeval[Try[A]]): Coeval[A] = fa.dematerialize

      def zipList[A](sources: Seq[Coeval[A]]): Coeval[Seq[A]] = Coeval.zipList(sources)
      def zipWith2[A1, A2, R](fa1: Coeval[A1], fa2: Coeval[A2])(f: (A1, A2) => R): Coeval[R] =
        Coeval.zipWith2(fa1, fa2)(f)
      override def zip2[A1, A2](fa1: Coeval[A1], fa2: Coeval[A2]): Coeval[(A1, A2)] =
        Coeval.zip2(fa1, fa2)
    }
}