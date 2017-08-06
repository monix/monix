/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import cats.Eval
import cats.effect.IO
import monix.eval.Coeval._
import monix.eval.instances.CatsSyncInstances
import monix.eval.internal.{CoevalRunLoop, LazyOnSuccess, Transformation}
import monix.execution.misc.NonFatal

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
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
  * [[monix.eval.Coeval.Eager Eager]] trait, a sub-type of [[Coeval]]
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
  override def apply(): A =
    CoevalRunLoop.start(this) match {
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

  /** Evaluates the underlying computation and returns the result or any
    * triggered errors as a Scala `Either`, where `Right(_)` is for successful
    * values and `Left(_)` is for thrown errors.
    */
  def run: Either[Throwable, A] =
    CoevalRunLoop.start(this) match {
      case Coeval.Now(a) => Right(a)
      case Coeval.Error(e) => Left(e)
    }

  /** Evaluates the underlying computation and returns the
    * result or any triggered errors as a [[Coeval.Eager]].
    */
  def runToEager: Coeval.Eager[A] =
    CoevalRunLoop.start(this)

  /** Evaluates the underlying computation and returns the
    * result or any triggered errors as a `scala.util.Try`.
    */
  def runTry: Try[A] =
    CoevalRunLoop.start(this).asScala

  /** Creates a new [[Coeval]] that will expose any triggered error
    * from the source.
    */
  def attempt: Coeval[Either[Throwable, A]] =
    FlatMap(this, AttemptCoeval.asInstanceOf[Transformation[A, Coeval[Either[Throwable, A]]]])

  /** Returns a failed projection of this coeval.
    *
    * The failed projection is a `Coeval` holding a value of type `Throwable`,
    * emitting the error yielded by the source, in case the source fails,
    * otherwise if the source succeeds the result will fail with a
    * `NoSuchElementException`.
    */
  def failed: Coeval[Throwable] =
    self.transformWith(_ => Error(new NoSuchElementException("failed")), e => Now(e))

  /** Creates a new `Coeval` by applying a function to the successful result
    * of the source, and returns a new instance equivalent
    * to the result of the function.
    */
  def flatMap[B](f: A => Coeval[B]): Coeval[B] =
    FlatMap(this, f)

  /** Given a source Coeval that emits another Coeval, this function
    * flattens the result, returning a Coeval equivalent to the emitted
    * Coeval by the source.
    */
  def flatten[B](implicit ev: A <:< Coeval[B]): Coeval[B] =
    flatMap(a => a)

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
    FlatMap(this, MaterializeCoeval.asInstanceOf[Transformation[A, Coeval[Try[A]]]])

  /** Dematerializes the source's result from a `Try`. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Coeval[B] =
    self.asInstanceOf[Coeval[Try[B]]].flatMap(Eager.fromTry)

  /** Converts the source [[Coeval]] into a [[Task]]. */
  def toTask: Task[A] = Task.coeval(self)

  /** Converts the source [[Coeval]] into a `cats.Eval`. */
  def toEval: Eval[A] =
    this match {
      case Coeval.Now(value) => Eval.now(value)
      case Coeval.Error(e) => Eval.always(throw e)
      case Coeval.Always(thunk) => new cats.Always(thunk)
      case other => Eval.always(other.value)
    }

  /** Converts the source [[Coeval]] into a `cats.effect.IO`. */
  def toIO: IO[A] =
    this match {
      case Coeval.Now(value) => IO.pure(value)
      case Coeval.Error(e) => IO.raiseError(e)
      case other => IO(other.value)
    }

  /** Creates a new `Coeval` by applying the 'fa' function to the successful result of
    * this future, or the 'fe' function to the potential errors that might happen.
    *
    * This function is similar with [[map]], except that it can also transform
    * errors and not just successful results.
    *
    * @param fa function that transforms a successful result of the receiver
    * @param fe function that transforms an error of the receiver
    */
  def transform[R](fa: A => R, fe: Throwable => R): Coeval[R] =
    FlatMap(this, Transformation(fa, fe).andThen(Coeval.now))

  /** Creates a new `Coeval` by applying the 'fa' function to the successful result of
    * this future, or the 'fe' function to the potential errors that might happen.
    *
    * This function is similar with [[flatMap]], except that it can also transform
    * errors and not just successful results.
    *
    * @param fa function that transforms a successful result of the receiver
    * @param fe function that transforms an error of the receiver
    */
  def transformWith[R](fa: A => Coeval[R], fe: Throwable => Coeval[R]): Coeval[R] =
    FlatMap(this, Transformation(fa, fe))

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
    self.transformWith(Coeval.Now.apply, f)

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
    transform(a => a, f)

  /** Creates a new coeval that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Coeval[U] =
    onErrorRecoverWith(pf.andThen(Coeval.now))

  /** Memoizes (caches) the result of the source and reuses it on
    * subsequent invocations of `value`.
    *
    * The resulting coeval will be idempotent, meaning that
    * evaluating the resulting coeval multiple times will have the
    * same effect as evaluating it once.
    *
    * @see [[memoizeOnSuccess]] for a version that only caches
    *     successful results
    */
  def memoize: Coeval[A] =
    self match {
      case Now(_) | Error(_) =>
        self
      case Always(thunk) =>
        new Once[A](thunk)
      case _: Once[_] =>
        self
      case other =>
        new Once[A](() => other.value)
    }

  /** Memoizes (cache) the successful result of the source
    * and reuses it on subsequent invocations of `value`.
    * Thrown exceptions are not cached.
    *
    * The resulting coeval will be idempotent, but only if the
    * result is successful.
    *
    * @see [[memoize]] for a version that caches both successful
    *     results and failures
    */
  def memoizeOnSuccess: Coeval[A] =
    self match {
      case Now(_) | Error(_) =>
        self
      case Always(thunk) =>
        val lf = LazyOnSuccess(thunk)
        if (lf eq thunk) self else Always(lf)
      case _: Once[_] =>
        self
      case other =>
        Always[A](LazyOnSuccess(() => other.value))
    }

  /** Returns a new `Coeval` in which `f` is scheduled to be run on completion.
    * This would typically be used to release any resources acquired by this
    * `Coeval`.
    */
  def doOnFinish(f: Option[Throwable] => Coeval[Unit]): Coeval[A] =
    transformWith(
      a => f(None).map(_ => a),
      e => f(Some(e)).flatMap(_ => Error(e))
    )

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

/** [[Coeval]] builders.
  *
  * @define attemptDeprecation This change happened in order to achieve
  *         naming consistency with the Typelevel ecosystem, where
  *         `Attempt[A]` is usually an alias for `Either[Throwable, A]`.
  */
object Coeval {
  /** Promotes a non-strict value to a [[Coeval]].
    *
    * Alias of [[eval]].
    */
  def apply[A](f: => A): Coeval[A] =
    Always(f _)

  /** Returns a `Coeval` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Coeval[A] = Now(a)

  /** Lifts a value into the coeval context. Alias for [[now]]. */
  def pure[A](a: A): Coeval[A] = Now(a)

  /** Returns a `Coeval` that on execution is always finishing in error
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

  /** Promote a non-strict value to a `Coeval`, catching exceptions in the
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

  /** Builds a `Coeval` out of a `cats.Eval` value. */
  def fromEval[A](a: Eval[A]): Coeval[A] =
    a match {
      case cats.Now(v) => Coeval.Now(v)
      case other => Coeval.eval(other.value)
    }

  /** Builds a `Coeval` out of a Scala `Try` value. */
  def fromTry[A](a: Try[A]): Coeval[A] =
    Eager.fromTry(a)

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

  /** The `Eager` type represents a strict, already evaluated result
    * of a [[Coeval]] that either resulted in success, wrapped in a
    * [[Now]], or in an error, wrapped in an [[Error]].
    *
    * It's the moral equivalent of `scala.util.Try`, except that
    * application of functions such as `map` and `flatMap` produces
    * [[Coeval]] references that are still lazily evaluated.
    */
  sealed abstract class Eager[+A] extends Coeval[A] with Product {
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
    final def isError: Boolean = this match {
      case Error(_) => true
      case _ => false
    }

    /** Converts this attempt into a `scala.util.Try`. */
    final def asScala: Try[A] =
      this match {
        case Now(a) => Success(a)
        case Error(ex) => Failure(ex)
      }

    override def failed: Eager[Throwable] =
      self match {
        case Coeval.Now(_) =>
          Coeval.Error(new NoSuchElementException("failed"))
        case Coeval.Error(e) =>
          Coeval.Now(e)
      }

    override final def memoize: Eager[A] =
      this
  }

  object Eager {
    /** Promotes a non-strict value to a [[Coeval.Eager]]. */
    def apply[A](f: => A): Eager[A] =
      try Now(f) catch {
        case NonFatal(ex) => Error(ex)
      }

    /** Builds an [[Coeval.Eager Eager]] from a `scala.util.Try` */
    def fromTry[A](value: Try[A]): Eager[A] =
      value match {
        case Success(a) => Now(a)
        case Failure(ex) => Error(ex)
      }
  }

  /** Constructs an eager [[Coeval]] instance from a strict
    * value that's already known.
    */
  final case class Now[+A](override val value: A) extends Eager[A] {
    override def apply(): A = value
    override def runToEager: Now[A] = this
  }

  /** Constructs an eager [[Coeval]] instance for
    * a result that represents an error.
    */
  final case class Error(ex: Throwable) extends Eager[Nothing] {
    override def apply(): Nothing = throw ex
    override def runToEager: Error = this
  }

  /** Constructs a lazy [[Coeval]] instance that gets evaluated
    * only once.
    *
    * In some sense it is equivalent to using a lazy val.
    * When caching is not required or desired,
    * prefer [[Always]] or [[Now]].
    */
  final class Once[+A](f: () => A) extends Coeval[A] with (() => A) { self =>
    private[this] var thunk: () => A = f

    override def apply(): A = runToEager match {
      case Now(a) => a
      case Error(ex) => throw ex
    }

    override lazy val runToEager: Eager[A] = {
      try {
        Now(thunk())
      } catch {
        case NonFatal(ex) => Error(ex)
      } finally {
        // GC relief
        thunk = null
      }
    }

    override def toString: String =
      synchronized {
        if (thunk != null) s"Coeval.Once($thunk)"
        else s"Coeval.Once($runToEager)"
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

    override def runToEager: Eager[A] =
      try Now(f()) catch {
        case NonFatal(ex) => Error(ex)
      }
  }

  /** Internal state, the result of [[Coeval.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Coeval[A])
    extends Coeval[A]

  /** Internal [[Coeval]] state that is the result of applying `flatMap`. */
  private[eval] final case class FlatMap[A, B](source: Coeval[A], f: A => Coeval[B])
    extends Coeval[B]

  /** Used as optimization by [[Coeval.attempt]]. */
  private object AttemptCoeval extends Transformation[Any, Coeval[Either[Throwable, Any]]] {
    override def success(a: Any): Coeval[Either[Throwable, Any]] =
      Coeval.now(Right(a))
    override def error(e: Throwable): Coeval[Either[Throwable, Any]] =
      Coeval.now(Left(e))
  }

  /** Used as optimization by [[Coeval.materialize]]. */
  private object MaterializeCoeval extends Transformation[Any, Coeval[Try[Any]]] {
    override def success(a: Any): Coeval[Try[Any]] =
      Coeval.now(Success(a))
    override def error(e: Throwable): Coeval[Try[Any]] =
      Coeval.now(Failure(e))
  }

  /** Type class instances of [[Coeval]] for Cats. */
  implicit def catsInstances: CatsSyncInstances[Coeval] =
    CatsSyncInstances.ForCoeval
}