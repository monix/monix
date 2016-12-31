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

import monix.types._
import monix.eval.Coeval._
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.generic.CanBuildFrom
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
  *  - [[monix.eval.Coeval.Now Now]] or [[monix.eval.Coeval.Error Error]]:
  *    for describing strict values, evaluated immediately
  *  - [[monix.eval.Coeval.Once Once]]: expressions evaluated a single time
  *  - [[monix.eval.Coeval.Always Always]]: expressions evaluated every time
  *    the value is needed
  *
  * The `Once` and `Always` are both lazy strategies while
  * `Now` and `Error` are eager. `Once` and `Always` are
  * distinguished from each other only by memoization: once evaluated
  * `Once` will save the value to be returned immediately if it is
  * needed again. `Always` will run its computation every time.
  *
  * Both `Now` and `Error` are represented by the
  * [[monix.eval.Coeval.Attempt Attempt]] trait, a sub-type of [[Coeval]]
  * that can be used as a replacement for Scala's own `Try` type.
  *
  * `Coeval` supports stack-safe lazy computation via the .map and .flatMap
  * methods, which use an internal trampoline to avoid stack overflows.
  * Computation done within .map and .flatMap is always done lazily,
  * even when applied to a `Now` instance.
  */
sealed abstract class Coeval[+A] extends (() => A) with Serializable { self =>
  /** Evaluates the underlying computation and returns the result.
    *
    * NOTE: this can throw exceptions.
    */
  override def apply(): A = runAttempt match {
    case Now(value) => value
    case Error(ex) => throw ex
  }

  /** Evaluates the underlying computation and returns the result.
    *
    * NOTE: this can throw exceptions.
    *
    * Alias for [[apply]].
    */
  def value: A = apply()

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
    Task.coeval(self)

  /** Creates a new `Coeval` by applying a function to the successful result
    * of the source, and returns a new instance equivalent
    * to the result of the function.
    */
  def flatMap[B](f: A => Coeval[B]): Coeval[B] =
    self match {
      case Now(a) =>
        Suspend(() => try f(a) catch { case NonFatal(ex) => Error(ex) })
      case eval @ Once(_) =>
        Suspend(() => eval.runAttempt match {
          case Now(a) => try f(a) catch { case NonFatal(ex) => Error(ex) }
          case error @ Error(_) => error
        })
      case Always(thunk) =>
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

  /** Returns a new task that upon evaluation will execute
    * the given function for the generated element,
    * transforming the source into a `Coeval[Unit]`.
    *
    * Similar in spirit with normal [[foreach]], but lazy,
    * as obviously nothing gets executed at this point.
    */
  def foreachL(f: A => Unit): Coeval[Unit] =
    self.map { a => f(a); () }

  /** Triggers the evaluation of the source, executing
    * the given function for the generated element.
    *
    * The application of this function has strict
    * behavior, as the coeval is immediately executed.
    */
  def foreach(f: A => Unit): Unit =
    foreachL(f).value

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
      case eval @ Once(_) =>
        Suspend(() => Now(eval.runAttempt))
      case Always(thunk) =>
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

  /** Given a predicate function, keep retrying the
    * coeval until the function returns true.
    */
  def restartUntil(p: (A) => Boolean): Coeval[A] =
    self.flatMap(a => if (p(a)) Coeval.now(a) else self.restartUntil(p))

  /** Creates a new coeval that will try recovering from an error by
    * matching it with another coeval using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Coeval[B]]): Coeval[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, Coeval.raiseError))

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
    onErrorHandleWith(_ => that)

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestart(maxRetries: Long): Coeval[A] =
    self.onErrorHandleWith(ex =>
      if (maxRetries > 0) self.onErrorRestart(maxRetries-1)
      else Error(ex))

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestartIf(p: Throwable => Boolean): Coeval[A] =
    self.onErrorHandleWith(ex => if (p(ex)) self.onErrorRestartIf(p) else Error(ex))

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
      case Always(thunk) =>
        new Once[A](thunk)
      case _: Once[_] => self
      case other =>
        new Once[A](() => other.value)
    }

  /** Returns a new `Coeval` in which `f` is scheduled to be run on completion.
    * This would typically be used to release any resources acquired by this
    * `Coeval`.
    */
  def doOnFinish(f: Option[Throwable] => Coeval[Unit]): Coeval[A] =
    materializeAttempt.flatMap {
      case Coeval.Now(value) =>
        f(None).map(_ => value)
      case Coeval.Error(ex) =>
        f(Some(ex)).flatMap(_ => Coeval.raiseError(ex))
    }

  /** Zips the values of `this` and `that` coeval, and creates a new coeval
    * that will emit the tuple of their results.
    */
  def zip[B](that: Coeval[B]): Coeval[(A, B)] =
    for (a <- this; b <- that) yield (a,b)

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  def zipMap[B,C](that: Coeval[B])(f: (A,B) => C): Coeval[C] =
    for (a <- this; b <- that) yield f(a,b)
}

object Coeval {
  /** Promotes a non-strict value to a [[Coeval]].
    *
    * Alias of [[eval]].
    */
  def apply[A](f: => A): Coeval[A] =
    Always(f _)

  /** Returns an `Coeval` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Coeval[A] = Now(a)

  /** Lifts a value into the coeval context. Alias for [[now]]. */
  def pure[A](a: A): Coeval[A] = Now(a)

  /** Returns an `Coeval` that on execution is always finishing in error
    * emitting the specified exception.
    */
  def raiseError[A](ex: Throwable): Coeval[A] =
    Error(ex)

  /** Promote a non-strict value representing a `Coeval`
    * to a `Coeval` of the same type.
    */
  def defer[A](fa: => Coeval[A]): Coeval[A] =
    Suspend(() => fa)

  /** Alias for [[defer]]. */
  def suspend[A](fa: => Coeval[A]): Coeval[A] = defer(fa)

  /** Promote a non-strict value to a `Coeval` that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](a: => A): Coeval[A] = Once(a _)

  /** Promote a non-strict value to an `Coeval`, catching exceptions in the
    * process.
    *
    * Note that since `Coeval` is not memoized, this will recompute the
    * value each time the `Coeval` is executed.
    */
  def eval[A](a: => A): Coeval[A] = Always(a _)

  /** Alias for [[eval]]. */
  def delay[A](a: => A): Coeval[A] = eval(a)

  /** A `Coeval[Unit]` provided for convenience. */
  val unit: Coeval[Unit] = Now(())

  /** Builds a `Coeval` out of a Scala `Try` value. */
  def fromTry[A](a: Try[A]): Coeval[A] =
    Attempt.fromTry(a)

  /** Keeps calling `f` until it returns a `Right` result.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[A,B](a: A)(f: A => Coeval[Either[A,B]]): Coeval[B] =
    Coeval.defer(f(a)).flatMap {
      case Left(continueA) => tailRecM(continueA)(f)
      case Right(b) => Coeval.now(b)
    }

  /** Transforms a `TraversableOnce` of coevals into a coeval producing
    * the same collection of gathered results.
    *
    * It's a simple version of [[traverse]].
    */
  def sequence[A, M[X] <: TraversableOnce[X]](sources: M[Coeval[A]])
    (implicit cbf: CanBuildFrom[M[Coeval[A]], A, M[A]]): Coeval[M[A]] = {
    val init = eval(cbf(sources))
    val r = sources.foldLeft(init)((acc,elem) => acc.zipMap(elem)(_ += _))
    r.map(_.result())
  }

  /** Transforms a `TraversableOnce[A]` into a coeval of the same collection
    * using the provided function `A => Coeval[B]`.
    *
    * It's a generalized version of [[sequence]].
    */
  def traverse[A, B, M[X] <: TraversableOnce[X]](sources: M[A])(f: A => Coeval[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Coeval[M[B]] = {
    val init = eval(cbf(sources))
    val r = sources.foldLeft(init)((acc,elem) => acc.zipMap(f(elem))(_ += _))
    r.map(_.result())
  }

  /** Zips together multiple [[Coeval]] instances. */
  def zipList[A](sources: Coeval[A]*): Coeval[List[A]] = {
    val init = eval(mutable.ListBuffer.empty[A])
    val r = sources.foldLeft(init)((acc, elem) => acc.zipMap(elem)(_ += _))
    r.map(_.toList)
  }

  /** Pairs two [[Coeval]] instances. */
  def zip2[A1, A2, R](fa1: Coeval[A1], fa2: Coeval[A2]): Coeval[(A1, A2)] =
    fa1.zipMap(fa2)((_, _))

  /** Pairs two [[Coeval]] instances, creating a new instance that will apply
    * the given mapping function to the resulting pair. */
  def zipMap2[A1, A2, R](fa1: Coeval[A1], fa2: Coeval[A2])(f: (A1, A2) => R): Coeval[R] =
    fa1.zipMap(fa2)(f)

  /** Pairs three [[Coeval]] instances. */
  def zip3[A1, A2, A3](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3]): Coeval[(A1, A2, A3)] =
    zipMap3(fa1, fa2, fa3)((a1, a2, a3) => (a1, a2, a3))

  /** Pairs four [[Coeval]] instances. */
  def zip4[A1, A2, A3, A4](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4]): Coeval[(A1, A2, A3, A4)] =
    zipMap4(fa1, fa2, fa3, fa4)((a1, a2, a3, a4) => (a1, a2, a3, a4))

  /** Pairs five [[Coeval]] instances. */
  def zip5[A1, A2, A3, A4, A5](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5]): Coeval[(A1, A2, A3, A4, A5)] =
    zipMap5(fa1, fa2, fa3, fa4, fa5)((a1, a2, a3, a4, a5) => (a1, a2, a3, a4, a5))

  /** Pairs six [[Coeval]] instances. */
  def zip6[A1, A2, A3, A4, A5, A6](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5], fa6: Coeval[A6]): Coeval[(A1, A2, A3, A4, A5, A6)] =
    zipMap6(fa1, fa2, fa3, fa4, fa5, fa6)((a1, a2, a3, a4, a5, a6) => (a1, a2, a3, a4, a5, a6))

  /** Pairs three [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap3[A1, A2, A3, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3])
    (f: (A1, A2, A3) => R): Coeval[R] = {

    val fa12 = zip2(fa1, fa2)
    zipMap2(fa12, fa3) { case ((a1, a2), a3) => f(a1, a2, a3) }
  }

  /** Pairs four [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap4[A1, A2, A3, A4, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4])
    (f: (A1, A2, A3, A4) => R): Coeval[R] = {

    val fa123 = zip3(fa1, fa2, fa3)
    zipMap2(fa123, fa4) { case ((a1, a2, a3), a4) => f(a1, a2, a3, a4) }
  }

  /** Pairs five [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap5[A1, A2, A3, A4, A5, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5])
    (f: (A1, A2, A3, A4, A5) => R): Coeval[R] = {

    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipMap2(fa1234, fa5) { case ((a1, a2, a3, a4), a5) => f(a1, a2, a3, a4, a5) }
  }

  /** Pairs six [[Coeval]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap6[A1, A2, A3, A4, A5, A6, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5], fa6: Coeval[A6])
    (f: (A1, A2, A3, A4, A5, A6) => R): Coeval[R] = {

    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipMap2(fa12345, fa6) { case ((a1, a2, a3, a4, a5), a6) => f(a1, a2, a3, a4, a5, a6) }
  }

  /** The `Attempt` represents a strict, already evaluated result
    * of a [[Coeval]] that either resulted in success, wrapped in a
    * [[Now]], or in an error, wrapped in a [[Error]].
    *
    * It's the moral equivalent of `scala.util.Try`.
    */
  sealed abstract class Attempt[+A] extends Coeval[A] with Product {
    self =>

    /** Retrieve the (successful) value or throw the error.
      *
      * Alias for [[Coeval.value]].
      */
    final def get: A = value

    /** Returns true if value is a successful one. */
    final def isSuccess: Boolean = this match {
      case Now(_) => true
      case _ => false
    }

    /** Returns true if result is an error. */
    final def isFailure: Boolean = this match {
      case Error(_) => true
      case _ => false
    }

    override final def failed: Attempt[Throwable] =
      self match {
        case Now(_) => Error(new NoSuchElementException("failed"))
        case Error(ex) => Now(ex)
      }

    /** Converts this attempt into a `scala.util.Try`. */
    final def asScala: Try[A] =
      this match {
        case Now(a) => Success(a)
        case Error(ex) => Failure(ex)
      }

    override final def materializeAttempt: Attempt[Attempt[A]] =
      self match {
        case now@Now(_) =>
          Now(now)
        case Error(ex) =>
          Now(Error(ex))
      }

    override final def dematerializeAttempt[B](implicit ev: <:<[A, Attempt[B]]): Attempt[B] =
      self match {
        case Now(now) => now
        case error@Error(_) => error
      }

    override final def memoize: Attempt[A] = this
  }

  object Attempt {
    /** Promotes a non-strict value to a [[Coeval.Attempt]]. */
    def apply[A](f: => A): Attempt[A] =
      try Now(f) catch {
        case NonFatal(ex) => Error(ex)
      }

    /** Builds an [[Coeval.Attempt Attempt]] from a `scala.util.Try` */
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
    override def apply(): A = value
    override def runAttempt: Now[A] = this
  }

  /** Constructs an eager [[Coeval]] instance for
    * a result that represents an error.
    */
  final case class Error(ex: Throwable) extends Attempt[Nothing] {
    override def apply(): Nothing = throw ex
    override def runAttempt: Error = this
  }

  /** Constructs a lazy [[Coeval]] instance that gets evaluated
    * only once.
    *
    * In some sense it is equivalent to using a lazy val.
    * When caching is not required or desired,
    * prefer [[Always]] or [[Now]].
    */
  final class Once[+A](f: () => A) extends Coeval[A] with (() => A) {
    private[this] var thunk: () => A = f

    override def apply(): A = runAttempt match {
      case Now(a) => a
      case Error(ex) => throw ex
    }

    override lazy val runAttempt: Attempt[A] = {
      try {
        Now(thunk())
      } catch {
        case NonFatal(ex) => Error(ex)
      } finally {
        // GC relief
        thunk = null
      }
    }

    override def toString =
      synchronized {
        if (thunk != null) s"Coeval.Once($thunk)"
        else s"Coeval.Once($runAttempt)"
      }
  }

  object Once {
    /** Builder for an [[Once]] instance. */
    def apply[A](a: () => A): Once[A] =
      new Once[A](a)

    /** Deconstructs an [[Once]] instance. */
    def unapply[A](coeval: Once[A]): Some[() => A] =
      Some(coeval)
  }

  /** Constructs a lazy [[Coeval]] instance.
    *
    * This type can be used for "lazy" values. In some sense it is
    * equivalent to using a Function0 value.
    */
  final case class Always[+A](f: () => A) extends Coeval[A] {
    override def apply(): A = f()

    override def runAttempt: Attempt[A] =
      try Now(f()) catch {
        case NonFatal(ex) => Error(ex)
      }
  }

  /** Internal state, the result of [[Coeval.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Coeval[A]) extends Coeval[A]

  /** Internal [[Coeval]] state that is the result of applying `flatMap`. */
  private[eval] final case class BindSuspend[A, B](thunk: () => Coeval[A], f: A => Coeval[B]) extends Coeval[B]

  private type Current = Coeval[Any]
  private type Bind = Any => Coeval[Any]

  /** Trampoline for lazy evaluation. */
  private[eval] def trampoline[A](source: Coeval[A], binds: List[Bind]): Attempt[A] = {
    @tailrec def reduceCoeval(source: Coeval[Any], binds: List[Bind]): Attempt[Any] = {
      source match {
        case error@Error(_) => error
        case now@Now(a) =>
          binds match {
            case Nil => now
            case f :: rest =>
              val fa = try f(a) catch {
                case NonFatal(ex) => Error(ex)
              }
              reduceCoeval(fa, rest)
          }

        case eval@Once(_) =>
          eval.runAttempt match {
            case now@Now(a) =>
              binds match {
                case Nil => now
                case f :: rest =>
                  val fa = try f(a) catch {
                    case NonFatal(ex) => Error(ex)
                  }
                  reduceCoeval(fa, rest)
              }
            case error@Error(_) =>
              error
          }

        case Always(thunk) =>
          val fa = try Now(thunk()) catch {
            case NonFatal(ex) => Error(ex)
          }
          reduceCoeval(fa, binds)

        case Suspend(thunk) =>
          val fa = try thunk() catch {
            case NonFatal(ex) => Error(ex)
          }
          reduceCoeval(fa, binds)

        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch {
            case NonFatal(ex) => Error(ex)
          }
          reduceCoeval(fa, f.asInstanceOf[Bind] :: binds)
      }
    }

    reduceCoeval(source, Nil).asInstanceOf[Attempt[A]]
  }

  /** Implicit type-class instances of [[Coeval]]. */
  implicit val typeClassInstances: TypeClassInstances = new TypeClassInstances

  /** Groups the implementation for the type-classes defined in [[monix.types]]. */
  class TypeClassInstances
    extends Suspendable.Instance[Coeval]
    with Memoizable.Instance[Coeval]
    with MonadError.Instance[Coeval,Throwable]
    with Comonad.Instance[Coeval]
    with MonadRec.Instance[Coeval] {

    override def pure[A](a: A): Coeval[A] = Coeval.now(a)
    override def suspend[A](fa: => Coeval[A]): Coeval[A] = Coeval.defer(fa)
    override def evalOnce[A](a: => A): Coeval[A] = Coeval.evalOnce(a)
    override def eval[A](a: => A): Coeval[A] = Coeval.eval(a)
    override def memoize[A](fa: Coeval[A]): Coeval[A] = fa.memoize
    override val unit: Coeval[Unit] = Coeval.now(())

    override def extract[A](x: Coeval[A]): A =
      x.value
    override def flatMap[A, B](fa: Coeval[A])(f: (A) => Coeval[B]): Coeval[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Coeval[Coeval[A]]): Coeval[A] =
      ffa.flatten
    override def tailRecM[A, B](a: A)(f: (A) => Coeval[Either[A, B]]): Coeval[B] =
      Coeval.tailRecM(a)(f)
    override def coflatMap[A, B](fa: Coeval[A])(f: (Coeval[A]) => B): Coeval[B] =
      Coeval.eval(f(fa))
    override def ap[A, B](ff: Coeval[(A) => B])(fa: Coeval[A]): Coeval[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Coeval[A], fb: Coeval[B])(f: (A, B) => Z): Coeval[Z] =
      for (a <- fa; b <- fb) yield f(a, b)
    override def map[A, B](fa: Coeval[A])(f: (A) => B): Coeval[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Coeval[A] =
      Coeval.raiseError(e)
    override def onErrorHandle[A](fa: Coeval[A])(f: (Throwable) => A): Coeval[A] =
      fa.onErrorHandle(f)
    override def onErrorHandleWith[A](fa: Coeval[A])(f: (Throwable) => Coeval[A]): Coeval[A] =
      fa.onErrorHandleWith(f)
    override def onErrorRecover[A](fa: Coeval[A])(pf: PartialFunction[Throwable, A]): Coeval[A] =
      fa.onErrorRecover(pf)
    override def onErrorRecoverWith[A](fa: Coeval[A])(pf: PartialFunction[Throwable, Coeval[A]]): Coeval[A] =
      fa.onErrorRecoverWith(pf)
  }
}
