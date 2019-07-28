/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.Monoid
import cats.~>
import cats.effect.{ExitCase, Sync}
import cats.kernel.Semigroup
import monix.eval.instances.{CatsMonadToMonoid, CatsMonadToSemigroup, CatsSyncForCoeval}
import monix.eval.internal.{CoevalBracket, CoevalDeprecated, CoevalRunLoop, LazyVal, StackFrame}
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.compat.BuildFrom
import monix.execution.compat.internal.newBuilder
import monix.execution.internal.Platform.fusionMaxStackDepth
import scala.annotation.unchecked.{uncheckedVariance => uV}
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
  *  - [[monix.eval.Coeval.now now]] or
  *    [[monix.eval.Coeval.raiseError raiseError]]: for describing
  *    strict values, evaluated immediately
  *  - [[monix.eval.Coeval.evalOnce evalOnce]]: expressions evaluated a single time
  *  - [[monix.eval.Coeval.eval eval]]: expressions evaluated every time
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
  * `Coeval` supports stack-safe lazy computation via the
  * [[monix.eval.Coeval!.map .map]] and [[Coeval!.flatMap .flatMap]] methods,
  * which use an internal trampoline to avoid stack overflows.
  * Computations done within `.map` and `.flatMap` are always
  * lazy, even when applied to a
  * [[monix.eval.Coeval.Eager Coeval.Eager]] instance (e.g.
  * [[monix.eval.Coeval.Now Coeval.Now]],
  * [[monix.eval.Coeval.Error Coeval.Error]]).
  *
  * =Evaluation Strategies=
  *
  * The "now" and "raiseError" builders are building `Coeval`
  * instances out of strict values:
  *
  * {{{
  *   val fa = Coeval.now(1)
  *   fa.value() // => 1
  *
  *   val fe = Coeval.raiseError(new RuntimeException("dummy"))
  *   fe.failed // => has RuntimeException
  * }}}
  *
  * The "always" strategy is equivalent with a plain function:
  *
  * {{{
  *   // For didactic purposes, don't use shared vars at home :-)
  *   var i = 0
  *   val coeval = Coeval.eval { i += 1; i }
  *
  *   coeval.value() // => 1
  *   coeval.value() // => 2
  *   coeval.value() // => 3
  * }}}
  *
  * The "once" strategy is equivalent with Scala's `lazy val`
  * (along with thread-safe idempotency guarantees):
  *
  * {{{
  *   var j = 0
  *   val coevalOnce = Coeval.evalOnce { j += 1; j }
  *
  *   coevalOnce.value() // => 1
  *   coevalOnce.value() // => 1
  *   coevalOnce.value() // => 1
  * }}}
  *
  * =Versus Task=
  *
  * The other option of suspending side-effects is [[Task]].
  * As a quick comparison:
  *
  *  - `Coeval`'s execution is always immediate / synchronous, whereas
  *    `Task` can describe asynchronous computations
  *  - `Coeval` is not cancelable, obviously, since execution is
  *    immediate and there's nothing to cancel
  *
  * =Versus cats.Eval=
  *
  * The `Coeval` data type is very similar with [[cats.Eval]].
  * As a quick comparison:
  *
  *  - `cats.Eval` is only for controlling laziness, but it doesn't
  *    handle side effects, hence `cats.Eval` is a `Comonad`
  *  - Monix's `Coeval` can handle side effects as well and thus it
  *    implements `MonadError[Coeval, Throwable]` and
  *    `cats.effect.Sync`, providing error-handling utilities
  *
  * If you just want to delay the evaluation of a pure expression
  * use `cats.Eval`, but if you need to suspend side effects or you
  * need error handling capabilities, then use `Coeval`.
  *
  * @define bracketErrorNote '''NOTE on error handling''': one big
  *         difference versus `try {} finally {}` is that, in case
  *         both the `release` function and the `use` function throws,
  *         the error raised by `use` gets signaled and the error
  *         raised by `release` gets reported with `System.err` for
  *         [[Coeval]] or with
  *         [[monix.execution.Scheduler.reportFailure Scheduler.reportFailure]]
  *         for [[Task]].
  *
  *         For example:
  *
  *         {{{
  *           Coeval("resource").bracket { _ =>
  *             // use
  *             Coeval.raiseError(new RuntimeException("Foo"))
  *           } { _ =>
  *             // release
  *             Coeval.raiseError(new RuntimeException("Bar"))
  *           }
  *         }}}
  *
  *         In this case the error signaled downstream is `"Foo"`,
  *         while the `"Bar"` error gets reported. This is consistent
  *         with the behavior of Haskell's `bracket` operation and NOT
  *         with `try {} finally {}` from Scala, Java or JavaScript.
  *
  * @define unsafeRun '''UNSAFE''' — this operation can trigger the
  *         execution of side effects, which break referential
  *         transparency and is thus not a pure function.
  *
  *         In FP code use with care, suspended in another `Coeval`
  *         or [[monix.eval.Task Task]], or at the edge of the FP
  *         program.
  *
  * @define unsafeMemoize '''UNSAFE''' — this operation allocates a shared,
  *         mutable reference, which can break in certain cases
  *         referential transparency, even if this operation guarantees
  *         idempotency (i.e. referential transparency implies idempotency,
  *         but idempotency does not imply referential transparency).
  *
  *         The allocation of a mutable reference is known to be a
  *         side effect, thus breaking referential transparency,
  *         even if calling this method does not trigger the evaluation
  *         of side effects suspended by the source.
  *
  *         Use with care. Sometimes it's easier to just keep a shared,
  *         memoized reference to some connection, but keep in mind
  *         it might be better to pass such a reference around as
  *         a parameter.
  */
sealed abstract class Coeval[+A] extends (() => A) with Serializable { self =>
  import monix.eval.Coeval._

  /** Runs this underlying computation first and then, when successful, the given one.
    * Returns the result of the given underlying computation.
    *
    * Example:
    * {{{
    *   val combined = Coeval{println("first"); "first"} >> Coeval{println("second"); "second"}
    *   // Prints "first" and then "second"
    *   // Result value will be "second"
    * }}}
    */
  final def >>[B](that: => Coeval[B]): Coeval[B] =
    this.flatMap(_ => that)

  /** Evaluates the underlying computation and returns the result.
    *
    * NOTE: this can throw exceptions.
    *
    * {{{
    *   // For didactic purposes, don't do shared vars at home :-)
    *   var i = 0
    *   val fa = Coeval { i += 1; i }
    *
    *   fa() // => 1
    *   fa() // => 2
    *   fa() // => 3
    * }}}
    *
    * $unsafeRun
    */
  @UnsafeBecauseImpure
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
    *
    * $unsafeRun
    */
  @UnsafeBecauseImpure
  def value(): A = apply()

  /** Evaluates the underlying computation, reducing this `Coeval`
    * to a [[Coeval.Eager]] value, with successful results being
    * signaled with [[Coeval.Now]] and failures with [[Coeval.Error]].
    *
    * {{{
    *   val fa = Coeval.eval(10 * 2)
    *
    *   fa.run match {
    *     case Coeval.Now(value) =>
    *       println("Success: " + value)
    *     case Coeval.Error(e) =>
    *       e.printStackTrace()
    *   }
    * }}}
    *
    * See [[runAttempt]] for working with [[scala.Either Either]]
    * values and [[runTry]] for working with [[scala.util.Try Try]]
    * values. See [[apply]] for a partial function (that may throw
    * exceptions in case of failure).
    *
    * $unsafeRun
    */
  @UnsafeBecauseImpure
  def run(): Coeval.Eager[A] =
    CoevalRunLoop.start(this)

  /** Evaluates the underlying computation and returns the result or
    * any triggered errors as a Scala `Either`, where `Right(_)` is
    * for successful values and `Left(_)` is for thrown errors.
    *
    * {{{
    *   val fa = Coeval(10 * 2)
    *
    *   fa.runAttempt match {
    *     case Right(value) =>
    *       println("Success: " + value)
    *     case Left(e) =>
    *       e.printStackTrace()
    *   }
    * }}}
    *
    * See [[run]] for working with [[Coeval.Eager]] values and
    * [[runTry]] for working with [[scala.util.Try Try]] values.
    * See [[apply]] for a partial function (that may throw exceptions
    * in case of failure).
    *
    * $unsafeRun
    */
  @UnsafeBecauseImpure
  def runAttempt(): Either[Throwable, A] =
    run() match {
      case Coeval.Now(a) => Right(a)
      case Coeval.Error(e) => Left(e)
    }

  /** Evaluates the underlying computation and returns the
    * result or any triggered errors as a `scala.util.Try`.
    *
    * {{{
    *   import scala.util._
    *
    *   val fa = Coeval(10 * 2)
    *
    *   fa.runTry match {
    *     case Success(value) =>
    *       println("Success: " + value)
    *     case Failure(e) =>
    *       e.printStackTrace()
    *   }
    * }}}
    *
    * See [[run]] for working with [[Coeval.Eager]] values and
    * [[runAttempt]] for working with [[scala.Either Either]] values.
    * See [[apply]] for a partial function (that may throw exceptions
    * in case of failure).
    *
    * $unsafeRun
    */
  @UnsafeBecauseImpure
  def runTry(): Try[A] =
    run().toTry

  /** Memoizes (caches) the result of the source and reuses it on
    * subsequent invocations of `value`.
    *
    * The resulting coeval will be idempotent, meaning that
    * evaluating the resulting coeval multiple times will have the
    * same effect as evaluating it once.
    *
    * $unsafeMemoize
    *
    * @see [[memoizeOnSuccess]] for a version that only caches
    *     successful results
    */
  @UnsafeBecauseImpure
  final def memoize: Coeval[A] =
    self match {
      case Now(_) | Error(_) =>
        self
      case Suspend(f: LazyVal[A @unchecked]) if f.cacheErrors =>
        self
      case _ =>
        Suspend(LazyVal(self, cacheErrors = true))
    }

  /** Memoizes (cache) the successful result of the source
    * and reuses it on subsequent invocations of `value`.
    * Thrown exceptions are not cached.
    *
    * The resulting coeval will be idempotent, but only if the
    * result is successful.
    *
    * $unsafeMemoize
    *
    * @see [[memoize]] for a version that caches both successful
    *     results and failures
    */
  @UnsafeBecauseImpure
  final def memoizeOnSuccess: Coeval[A] =
    self match {
      case Now(_) | Error(_) =>
        self
      case Suspend(_: LazyVal[A @unchecked]) =>
        self
      case _ =>
        Suspend(LazyVal(self, cacheErrors = false))
    }

  /** Creates a new [[Coeval]] that will expose any triggered error
    * from the source.
    *
    * {{{
    *   val fa: Coeval[Int] =
    *     Coeval.raiseError[Int](new RuntimeException("dummy"))
    *
    *   val fe: Coeval[Either[Throwable, Int]] =
    *     fa.attempt
    *
    *   fe.map {
    *     case Left(_) => Int.MaxValue
    *     case Right(v) => v
    *   }
    * }}}
    *
    * By exposing errors by lifting the `Coeval`'s result into an
    * `Either` value, we can handle those errors in `flatMap`
    * transformations.
    *
    * Also see [[materialize]] for working with Scala's
    * [[scala.util.Try Try]] or [[redeemWith]] for an alternative.
    */
  final def attempt: Coeval[Either[Throwable, A]] =
    FlatMap(this, AttemptCoeval.asInstanceOf[A => Coeval[Either[Throwable, A]]])

  /** Returns a task that treats the source as the acquisition of a resource,
    * which is then exploited by the `use` function and then `released`.
    *
    * The `bracket` operation is the equivalent of the
    * `try {} finally {}` statements from mainstream languages, installing
    * the necessary exception handler to release the resource in the event of
    * an exception being raised during the computation. If an exception is raised,
    * then `bracket` will re-raise the exception ''after'' performing the `release`.
    *
    * Example:
    *
    * {{{
    *   import java.io._
    *
    *   def readFile(file: File): Coeval[String] = {
    *     // Opening a file handle for reading text
    *     val acquire = Coeval.eval(new BufferedReader(
    *       new InputStreamReader(new FileInputStream(file), "utf-8")
    *     ))
    *
    *     acquire.bracket { in =>
    *       // Usage part
    *       Coeval.eval {
    *         // Yes, ugly Java, non-FP loop;
    *         // side-effects are suspended though
    *         var line: String = null
    *         val buff = new StringBuilder()
    *         do {
    *           line = in.readLine()
    *           if (line != null) buff.append(line)
    *         } while (line != null)
    *         buff.toString()
    *       }
    *     } { in =>
    *       // The release part
    *       Coeval.eval(in.close())
    *     }
    *   }
    * }}}
    *
    * $bracketErrorNote
    *
    * @see [[bracketCase]] and [[bracketE]]
    *
    * @param use is a function that evaluates the resource yielded by the source,
    *        yielding a result that will get generated by the task returned
    *        by this `bracket` function
    *
    * @param release is a function that gets called after `use` terminates,
    *        either normally or in error, receiving as input the resource that
    *        needs to be released
    */
  final def bracket[B](use: A => Coeval[B])(release: A => Coeval[Unit]): Coeval[B] =
    bracketE(use)((a, _) => release(a))

  /** Returns a new task that treats the source task as the
    * acquisition of a resource, which is then exploited by the `use`
    * function and then `released`, with the possibility of
    * distinguishing between successful completion and failure, such
    * that an appropriate release of resources can be executed.
    *
    * The `bracketCase` operation is the equivalent of
    * `try {} catch {} finally {}` statements from mainstream languages
    * when used for the acquisition and release of resources.
    *
    * The `bracketCase` operation installs the necessary exception handler
    * to release the resource in the event of an exception being raised
    * during the computation.
    *
    * In comparison with the simpler [[bracket]] version, this one
    * allows the caller to differentiate between normal termination and
    * termination in error via an `ExitCase` parameter.
    *
    * @see [[bracket]] and [[bracketE]]
    *
    * @param use is a function that evaluates the resource yielded by
    *        the source, yielding a result that will get generated by
    *        this function on evaluation
    *
    * @param release is a function that gets called after `use`
    *        terminates, either normally or in error, receiving as
    *        input the resource that needs that needs release, along
    *        with the result of `use`
    */
  final def bracketCase[B](use: A => Coeval[B])(release: (A, ExitCase[Throwable]) => Coeval[Unit]): Coeval[B] =
    CoevalBracket.exitCase(this, use, release)

  /** Returns a task that treats the source task as the acquisition of a resource,
    * which is then exploited by the `use` function and then `released`, with
    * the possibility of distinguishing between successful termination and
    * error,  such that an appropriate release of resources can be executed.
    *
    * The `bracket` operation is the equivalent of the
    * `try {} finally {}` statements from mainstream languages, installing
    * the necessary exception handler to release the resource in the event of
    * an exception being raised during the computation. If an exception is raised,
    * then `bracket` will re-raise the exception ''after'' performing the `release`.
    *
    * The `release` function receives as input:
    *
    *  - `Left(error)` in case `use` terminated with an error
    *  - `Right(b)` in case of success
    *
    * $bracketErrorNote
    *
    * @see [[bracket]] and [[bracketCase]]
    *
    * @param use is a function that evaluates the resource yielded by the source,
    *        yielding a result that will get generated by this function on
    *        evaluation
    *
    * @param release is a function that gets called after `use` terminates,
    *        either normally or in error, receiving as input the resource that
    *        needs that needs release, along with the result of `use`
    */
  final def bracketE[B](use: A => Coeval[B])(release: (A, Either[Throwable, B]) => Coeval[Unit]): Coeval[B] =
    CoevalBracket.either(this, use, release)

  /**
    * Executes the given `finalizer` when the source is finished,
    * either in success or in error, or if canceled.
    *
    * This variant of [[guaranteeCase]] evaluates the given `finalizer`
    * regardless of how the source gets terminated:
    *
    *  - normal completion
    *  - completion in error
    *  - cancellation
    *
    * As best practice, it's not a good idea to release resources
    * via `guaranteeCase` in polymorphic code. Prefer [[bracket]]
    * for the acquisition and release of resources.
    *
    * @see [[guaranteeCase]] for the version that can discriminate
    *      between termination conditions
    *
    * @see [[bracket]] for the more general operation
    */
  final def guarantee(finalizer: Coeval[Unit]): Coeval[A] =
    unit.bracket(_ => this)(_ => finalizer)

  /**
    * Executes the given `finalizer` when the source is finished,
    * either in success or in error, or if canceled, allowing
    * for differentiating between exit conditions.
    *
    * This variant of [[guarantee]] injects an ExitCase in
    * the provided function, allowing one to make a difference
    * between:
    *
    *  - normal completion
    *  - completion in error
    *  - cancellation
    *
    * As best practice, it's not a good idea to release resources
    * via `guaranteeCase` in polymorphic code. Prefer [[bracketCase]]
    * for the acquisition and release of resources.
    *
    * @see [[guarantee]] for the simpler version
    *
    * @see [[bracketCase]] for the more general operation
    */
  final def guaranteeCase(finalizer: ExitCase[Throwable] => Coeval[Unit]): Coeval[A] =
    unit.bracketCase(_ => this)((_, e) => finalizer(e))

  /** Returns a failed projection of this coeval.
    *
    * The failed projection is a `Coeval` holding a value of type `Throwable`,
    * emitting the error yielded by the source, in case the source fails,
    * otherwise if the source succeeds the result will fail with a
    * `NoSuchElementException`.
    */
  final def failed: Coeval[Throwable] =
    Coeval.FlatMap(this, Coeval.Failed)

  /** Creates a new `Coeval` by applying a function to the successful result
    * of the source, and returns a new instance equivalent
    * to the result of the function.
    *
    * The application of `flatMap` is always lazy and because of the
    * implementation it is memory safe and thus it can be used in
    * recursive loops.
    *
    * Sample:
    *
    * {{{
    *   import scala.util.Random
    *
    *   def randomEven: Coeval[Int] =
    *     Coeval(Random.nextInt()).flatMap { x =>
    *       if (x < 0 || x % 2 == 1)
    *         randomEven // retry
    *       else
    *         Coeval.now(x)
    *     }
    * }}}
    */
  final def flatMap[B](f: A => Coeval[B]): Coeval[B] =
    FlatMap(this, f)

  /** Given a source Coeval that emits another Coeval, this function
    * flattens the result, returning a Coeval equivalent to the emitted
    * Coeval by the source.
    *
    * This equivalence with [[flatMap]] always holds:
    *
    * `fa.flatten <-> fa.flatMap(x => x)`
    */
  final def flatten[B](implicit ev: A <:< Coeval[B]): Coeval[B] =
    flatMap(a => a)

  /** Returns a new task that upon evaluation will execute
    * the given function for the generated element,
    * transforming the source into a `Coeval[Unit]`.
    *
    * Similar in spirit with normal [[foreach]], but lazy,
    * as obviously nothing gets executed at this point.
    */
  final def foreachL(f: A => Unit): Coeval[Unit] =
    self.map { a =>
      f(a); ()
    }

  /** Triggers the evaluation of the source, executing
    * the given function for the generated element.
    *
    * The application of this function has strict
    * behavior, as the coeval is immediately executed.
    */
  final def foreach(f: A => Unit): Unit =
    foreachL(f).value()

  /** Returns a new `Coeval` that applies the mapping function to
    * the element emitted by the source.
    *
    * Can be used for specifying a (lazy) transformation to the result
    * of the source.
    *
    * This equivalence with [[flatMap]] always holds:
    *
    * `fa.map(f) <-> fa.flatMap(x => Coeval.pure(f(x)))`
    */
  final def map[B](f: A => B): Coeval[B] =
    this match {
      case Map(source, g, index) =>
        // Allowed to do a fixed number of map operations fused before
        // resetting the counter in order to avoid stack overflows;
        // See `monix.execution.internal.Platform` for details.
        if (index != fusionMaxStackDepth) Map(source, g.andThen(f), index + 1)
        else Map(this, f, 0)
      case _ =>
        Map(this, f, 0)
    }

  /** Creates a new [[Coeval]] that will expose any triggered error from
    * the source.
    *
    * Also see [[attempt]] for working with Scala's
    * [[scala.Either Either]] or [[redeemWith]] for an alternative.
    */
  final def materialize: Coeval[Try[A]] =
    FlatMap(this, MaterializeCoeval.asInstanceOf[A => Coeval[Try[A]]])

  /** Dematerializes the source's result from a `Try`.
    *
    * This equivalence always holds:
    *
    * `fa.materialize.dematerialize <-> fa`
    */
  final def dematerialize[B](implicit ev: A <:< Try[B]): Coeval[B] =
    self.asInstanceOf[Coeval[Try[B]]].flatMap(Eager.fromTry)

  /**
    * Converts the source [[Coeval]] into any `F[_]` that implements
    * `cats.effect.Sync`.
    *
    * For example it can work with `cats.effect.IO`:
    * {{{
    *   import cats._
    *   import cats.effect._
    *
    *   val source = Coeval { 1 + 1 }
    *
    *   val asIO: IO[Int]     = source.to[IO]
    *   val asEval: Eval[Int] = source.to[Eval]
    *   val asTask: Task[Int] = source.to[Task]
    * }}}
    */
  final def to[F[_]](implicit F: CoevalLift[F]): F[A @uV] =
    F(this)

  /**
    * Converts the source to any value that implements `cats.effect.Sync`.
    *
    * Prefer to use [[to]], this method is provided in order to force
    * the usage of `cats.effect.Sync` instances (instead of [[CoevalLift]]).
    */
  final def toSync[F[_]](implicit F: Sync[F]): F[A @uV] =
    CoevalLift.toSync[F].apply(this)

  /** Returns a new value that transforms the result of the source,
    * given the `recover` or `map` functions, which get executed depending
    * on whether the result is successful or if it ends in error.
    *
    * This is an optimization on usage of [[attempt]] and [[map]],
    * this equivalence being true:
    *
    * `coeval.redeem(recover, map) <-> coeval.attempt.map(_.fold(recover, map))`
    *
    * Usage of `redeem` subsumes [[onErrorHandle]] because:
    *
    * `coeval.redeem(fe, id) <-> coeval.onErrorHandle(fe)`
    *
    * @param recover is a function used for error recover in case the
    *        source ends in error
    * @param map is a function used for mapping the result of the source
    *        in case it ends in success
    */
  def redeem[B](recover: Throwable => B, map: A => B): Coeval[B] =
    Coeval.FlatMap(this, new Coeval.Redeem(recover, map))

  /** Returns a new value that transforms the result of the source,
    * given the `recover` or `bind` functions, which get executed depending
    * on whether the result is successful or if it ends in error.
    *
    * This is an optimization on usage of [[attempt]] and [[flatMap]],
    * this equivalence being available:
    *
    * `coeval.redeemWith(recover, bind) <-> coeval.attempt.flatMap(_.fold(recover, bind))`
    *
    * Usage of `redeemWith` subsumes [[onErrorHandleWith]] because:
    *
    * `coeval.redeemWith(fe, F.pure) <-> coeval.onErrorHandleWith(fe)`
    *
    * Usage of `redeemWith` also subsumes [[flatMap]] because:
    *
    * `coeval.redeemWith(Coeval.raiseError, fs) <-> coeval.flatMap(fs)`
    *
    * @param recover is the function that gets called to recover the source
    *        in case of error
    * @param bind is the function that gets to transform the source
    *        in case of success
    */
  def redeemWith[B](recover: Throwable => Coeval[B], bind: A => Coeval[B]): Coeval[B] =
    Coeval.FlatMap(this, new StackFrame.RedeemWith(recover, bind))

  /** Deprecated — use [[redeem]] instead.
    *
    * [[Coeval.redeem]] is the same operation, but with a different name and the
    * function parameters in an inverted order, to make it consistent with `fold`
    * on `Either` and others (i.e. the function for error recovery is at the left).
    */
  @deprecated("Please use `Coeval.redeem`", since = "3.0.0-RC2")
  final def transform[R](fa: A => R, fe: Throwable => R): Coeval[R] = {
    // $COVERAGE-OFF$
    redeem(fe, fa)
    // $COVERAGE-ON$
  }

  /** Deprecated — use [[redeemWith]] instead.
    *
    * [[Coeval.redeemWith]] is the same operation, but with a different name and the
    * function parameters in an inverted order, to make it consistent with `fold`
    * on `Either` and others (i.e. the function for error recovery is at the left).
    */
  @deprecated("Please use `Coeval.redeemWith`", since = "3.0.0-RC2")
  final def transformWith[R](fa: A => Coeval[R], fe: Throwable => Coeval[R]): Coeval[R] = {
    // $COVERAGE-OFF$
    redeemWith(fe, fa)
    // $COVERAGE-ON$
  }

  /** Given a predicate function, keep retrying the
    * coeval until the function returns true.
    */
  final def restartUntil(p: (A) => Boolean): Coeval[A] =
    self.flatMap(a => if (p(a)) Coeval.now(a) else self.restartUntil(p))

  /** Creates a new coeval that will try recovering from an error by
    * matching it with another coeval using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  final def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Coeval[B]]): Coeval[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, raiseConstructor))

  /** Creates a new coeval that will handle any matching throwable that
    * this coeval might emit by executing another coeval.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  final def onErrorHandleWith[B >: A](f: Throwable => Coeval[B]): Coeval[B] =
    FlatMap(this, new StackFrame.ErrorHandler(f, nowConstructor))

  /** Creates a new coeval that in case of error will fallback to the
    * given backup coeval.
    */
  final def onErrorFallbackTo[B >: A](that: Coeval[B]): Coeval[B] =
    onErrorHandleWith(_ => that)

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  final def onErrorRestart(maxRetries: Long): Coeval[A] =
    self.onErrorHandleWith(
      ex =>
        if (maxRetries > 0) self.onErrorRestart(maxRetries - 1)
        else Error(ex))

  /** Creates a new coeval that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  final def onErrorRestartIf(p: Throwable => Boolean): Coeval[A] =
    self.onErrorHandleWith(ex => if (p(ex)) self.onErrorRestartIf(p) else Error(ex))

  /** Creates a new coeval that will handle any matching throwable that
    * this coeval might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  final def onErrorHandle[U >: A](f: Throwable => U): Coeval[U] =
    onErrorHandleWith(f.andThen(nowConstructor))

  /** Creates a new coeval that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  final def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Coeval[U] =
    onErrorRecoverWith(pf.andThen(nowConstructor))

  /** On error restarts the source with a customizable restart loop.
    *
    * This operation keeps an internal `state`, with a start value, an internal
    * state that gets evolved and based on which the next step gets decided,
    * e.g. should it restart, or should it give up and rethrow the current error.
    *
    * Example that implements a simple retry policy that retries for a maximum
    * of 10 times before giving up:
    *
    * {{{
    *   import scala.util.Random
    *
    *   val fa = Coeval {
    *     if (Random.nextInt(20) > 10)
    *       throw new RuntimeException("boo")
    *     else 78
    *   }
    *
    *   fa.onErrorRestartLoop(10) { (err, maxRetries, retry) =>
    *     if (maxRetries > 0)
    *       // Do next retry please
    *       retry(maxRetries - 1)
    *     else
    *       // No retries left, rethrow the error
    *       Coeval.raiseError(err)
    *   }
    * }}}
    *
    * The given function injects the following parameters:
    *
    *  1. `error` reference that was thrown
    *  2. the current `state`, based on which a decision for the retry is made
    *  3. `retry: S => Task[B]` function that schedules the next retry
    *
    * @param initial is the initial state used to determine the next on error
    *        retry cycle
    * @param f is a function that injects the current error, state, a
    *        function that can signal a retry is to be made and returns
    *        the next coeval
    */
  final def onErrorRestartLoop[S, B >: A](initial: S)(f: (Throwable, S, S => Coeval[B]) => Coeval[B]): Coeval[B] =
    onErrorHandleWith(err => f(err, initial, state => (this: Coeval[B]).onErrorRestartLoop(state)(f)))

  /** Returns a new `Coeval` in which `f` is scheduled to be run on completion.
    * This would typically be used to release any resources acquired by this
    * `Coeval`.
    */
  final def doOnFinish(f: Option[Throwable] => Coeval[Unit]): Coeval[A] =
    redeemWith(
      e => f(Some(e)).flatMap(_ => Error(e)),
      a => f(None).map(_ => a)
    )

  /** Returns this coeval mapped to unit
    */
  final def void: Coeval[Unit] =
    this.map(_ => ())

  /** Zips the values of `this` and `that` coeval, and creates a new coeval
    * that will emit the tuple of their results.
    */
  final def zip[B](that: Coeval[B]): Coeval[(A, B)] =
    for (a <- this; b <- that) yield (a, b)

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  final def zipMap[B, C](that: Coeval[B])(f: (A, B) => C): Coeval[C] =
    for (a <- this; b <- that) yield f(a, b)

  override def toString: String = this match {
    case Now(a) => s"Coeval.Now($a)"
    case Error(e) => s"Coeval.Error($e)"
    case _ =>
      val n = this.getClass.getName.replaceFirst("^monix\\.eval\\.Coeval[$.]", "")
      s"Coeval.$n$$${System.identityHashCode(this)}"
  }
}

/** [[Coeval]] builders.
  *
  * @define attemptDeprecation This change happened in order to achieve
  *         naming consistency with the Typelevel ecosystem, where
  *         `Attempt[A]` is usually an alias for `Either[Throwable, A]`.
  *
  * @define unsafeMemoize '''UNSAFE''' — this operation allocates a shared,
  *         mutable reference, which can break in certain cases
  *         referential transparency, even if this operation guarantees
  *         idempotency (i.e. referential transparency implies idempotency,
  *         but idempotency does not imply referential transparency).
  *
  *         The allocation of a mutable reference is known to be a
  *         side effect, thus breaking referential transparency,
  *         even if calling this method does not trigger the evaluation
  *         of side effects suspended by the source.
  *
  *         Use with care. Sometimes it's easier to just keep a shared,
  *         memoized reference to some connection, but keep in mind
  *         it might be better to pass such a reference around as
  *         a parameter.
  */
object Coeval extends CoevalInstancesLevel0 {
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
    *
    * Guarantees thread-safe idempotency.
    *
    * $unsafeMemoize
    */
  def evalOnce[A](a: => A): Coeval[A] =
    Suspend(LazyVal(a _, cacheErrors = true))

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

  /**
    * Converts any value that has a [[CoevalLike]] instance into a `Coeval`.
    */
  def from[F[_], A](fa: F[A])(implicit F: CoevalLike[F]): Coeval[A] =
    F(fa)

  /**
    * Converts a Scala `Try` into a [[Coeval]].
    */
  def fromTry[A](a: Try[A]): Coeval[A] =
    Eager.fromTry(a)

  /**
    * Converts a Scala `Either` into a [[Coeval]].
    */
  def fromEither[E <: Throwable, A](a: Either[E, A]): Coeval[A] =
    a match {
      case Right(v) => Coeval.now(v)
      case Left(ex) => Coeval.raiseError(ex)
    }

  /**
    * Converts a Scala `Either` into a [[Coeval]].
    *
    * @param f is a function that knows how to convert into a `Throwable`
    *        in order to throw that error in the `MonadError` context.
    */
  def fromEither[E, A](f: E => Throwable)(a: Either[E, A]): Coeval[A] =
    a match {
      case Right(v) => Coeval.now(v)
      case Left(ex) => Coeval.raiseError(f(ex))
    }

  /** Keeps calling `f` until it returns a `Right` result.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[A, B](a: A)(f: A => Coeval[Either[A, B]]): Coeval[B] =
    Coeval.defer(f(a)).flatMap {
      case Left(continueA) => tailRecM(continueA)(f)
      case Right(b) => Coeval.now(b)
    }

  /** Transforms a `Iterable` of coevals into a coeval producing
    * the same collection of gathered results.
    *
    * It's a simple version of [[traverse]].
    */
  def sequence[A, M[X] <: Iterable[X]](sources: M[Coeval[A]])(
    implicit bf: BuildFrom[M[Coeval[A]], A, M[A]]): Coeval[M[A]] = {
    val init = eval(newBuilder(bf, sources))
    val r = sources.foldLeft(init)((acc, elem) => acc.zipMap(elem)(_ += _))
    r.map(_.result())
  }

  /** Transforms a `Iterable[A]` into a coeval of the same collection
    * using the provided function `A => Coeval[B]`.
    *
    * It's a generalized version of [[sequence]].
    */
  def traverse[A, B, M[X] <: Iterable[X]](sources: M[A])(f: A => Coeval[B])(
    implicit bf: BuildFrom[M[A], B, M[B]]): Coeval[M[B]] = {
    val init = eval(newBuilder(bf, sources))
    val r = sources.foldLeft(init)((acc, elem) => acc.zipMap(f(elem))(_ += _))
    r.map(_.result())
  }

  /** Zips together multiple [[Coeval]] instances. */
  def zipList[A](sources: Coeval[A]*): Coeval[List[A]] = {
    val init = eval(mutable.ListBuffer.empty[A])
    val r = sources.foldLeft(init)((acc, elem) => acc.zipMap(elem)(_ += _))
    r.map(_.toList)
  }

  /** Pairs 2 `Coeval` values, applying the given mapping function.
    *
    * Returns a new `Coeval` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * {{{
    *   val fa1 = Coeval(1)
    *   val fa2 = Coeval(2)
    *
    *   // Yields Success(3)
    *   Coeval.map2(fa1, fa2) { (a, b) =>
    *     a + b
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Coeval.map2(fa1, Coeval.raiseError[Int](new RuntimeException("boo"))) { (a, b) =>
    *     a + b
    *   }
    * }}}
    */
  def map2[A1, A2, R](fa1: Coeval[A1], fa2: Coeval[A2])(f: (A1, A2) => R): Coeval[R] =
    fa1.zipMap(fa2)(f)

  /** Pairs 3 `Coeval` values, applying the given mapping function.
    *
    * Returns a new `Coeval` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * {{{
    *   val fa1 = Coeval(1)
    *   val fa2 = Coeval(2)
    *   val fa3 = Coeval(3)
    *
    *   // Yields Success(6)
    *   Coeval.map3(fa1, fa2, fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Coeval.map3(fa1, Coeval.raiseError[Int](new RuntimeException("boo")), fa3) { (a, b, c) =>
    *     a + b + c
    *   }
    * }}}
    */
  def map3[A1, A2, A3, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3])(f: (A1, A2, A3) => R): Coeval[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3)
      yield f(a1, a2, a3)
  }

  /** Pairs 4 `Coeval` values, applying the given mapping function.
    *
    * Returns a new `Coeval` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * {{{
    *   val fa1 = Coeval(1)
    *   val fa2 = Coeval(2)
    *   val fa3 = Coeval(3)
    *   val fa4 = Coeval(4)
    *
    *   // Yields Success(10)
    *   Coeval.map4(fa1, fa2, fa3, fa4) { (a, b, c, d) =>
    *     a + b + c + d
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Coeval.map4(fa1, Coeval.raiseError[Int](new RuntimeException("boo")), fa3, fa4) {
    *     (a, b, c, d) => a + b + c + d
    *   }
    * }}}
    */
  def map4[A1, A2, A3, A4, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4])(
    f: (A1, A2, A3, A4) => R): Coeval[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4)
      yield f(a1, a2, a3, a4)
  }

  /** Pairs 5 `Coeval` values, applying the given mapping function.
    *
    * Returns a new `Coeval` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * {{{
    *   val fa1 = Coeval(1)
    *   val fa2 = Coeval(2)
    *   val fa3 = Coeval(3)
    *   val fa4 = Coeval(4)
    *   val fa5 = Coeval(5)
    *
    *   // Yields Success(15)
    *   Coeval.map5(fa1, fa2, fa3, fa4, fa5) { (a, b, c, d, e) =>
    *     a + b + c + d + e
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Coeval.map5(fa1, Coeval.raiseError[Int](new RuntimeException("boo")), fa3, fa4, fa5) {
    *     (a, b, c, d, e) => a + b + c + d + e
    *   }
    * }}}
    */
  def map5[A1, A2, A3, A4, A5, R](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3], fa4: Coeval[A4], fa5: Coeval[A5])(
    f: (A1, A2, A3, A4, A5) => R): Coeval[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4; a5 <- fa5)
      yield f(a1, a2, a3, a4, a5)
  }

  /** Pairs 6 `Coeval` values, applying the given mapping function.
    *
    * Returns a new `Coeval` reference that completes with the result
    * of mapping that function to their successful results, or in
    * failure in case either of them fails.
    *
    * {{{
    *   val fa1 = Coeval(1)
    *   val fa2 = Coeval(2)
    *   val fa3 = Coeval(3)
    *   val fa4 = Coeval(4)
    *   val fa5 = Coeval(5)
    *   val fa6 = Coeval(6)
    *
    *   // Yields Success(21)
    *   Coeval.map6(fa1, fa2, fa3, fa4, fa5, fa6) { (a, b, c, d, e, f) =>
    *     a + b + c + d + e + f
    *   }
    *
    *   // Yields Failure(e), because the second arg is a failure
    *   Coeval.map6(fa1, Coeval.raiseError[Int](new RuntimeException("boo")), fa3, fa4, fa5, fa6) {
    *     (a, b, c, d, e, f) => a + b + c + d + e + f
    *   }
    * }}}
    */
  def map6[A1, A2, A3, A4, A5, A6, R](
    fa1: Coeval[A1],
    fa2: Coeval[A2],
    fa3: Coeval[A3],
    fa4: Coeval[A4],
    fa5: Coeval[A5],
    fa6: Coeval[A6])(f: (A1, A2, A3, A4, A5, A6) => R): Coeval[R] = {

    for (a1 <- fa1; a2 <- fa2; a3 <- fa3; a4 <- fa4; a5 <- fa5; a6 <- fa6)
      yield f(a1, a2, a3, a4, a5, a6)
  }

  /** Pairs two [[Coeval]] instances. */
  def zip2[A1, A2, R](fa1: Coeval[A1], fa2: Coeval[A2]): Coeval[(A1, A2)] =
    fa1.zipMap(fa2)((_, _))

  /** Pairs three [[Coeval]] instances. */
  def zip3[A1, A2, A3](fa1: Coeval[A1], fa2: Coeval[A2], fa3: Coeval[A3]): Coeval[(A1, A2, A3)] =
    map3(fa1, fa2, fa3)((a1, a2, a3) => (a1, a2, a3))

  /** Pairs four [[Coeval]] instances. */
  def zip4[A1, A2, A3, A4](
    fa1: Coeval[A1],
    fa2: Coeval[A2],
    fa3: Coeval[A3],
    fa4: Coeval[A4]): Coeval[(A1, A2, A3, A4)] =
    map4(fa1, fa2, fa3, fa4)((a1, a2, a3, a4) => (a1, a2, a3, a4))

  /** Pairs five [[Coeval]] instances. */
  def zip5[A1, A2, A3, A4, A5](
    fa1: Coeval[A1],
    fa2: Coeval[A2],
    fa3: Coeval[A3],
    fa4: Coeval[A4],
    fa5: Coeval[A5]): Coeval[(A1, A2, A3, A4, A5)] =
    map5(fa1, fa2, fa3, fa4, fa5)((a1, a2, a3, a4, a5) => (a1, a2, a3, a4, a5))

  /** Pairs six [[Coeval]] instances. */
  def zip6[A1, A2, A3, A4, A5, A6](
    fa1: Coeval[A1],
    fa2: Coeval[A2],
    fa3: Coeval[A3],
    fa4: Coeval[A4],
    fa5: Coeval[A5],
    fa6: Coeval[A6]): Coeval[(A1, A2, A3, A4, A5, A6)] =
    map6(fa1, fa2, fa3, fa4, fa5, fa6)((a1, a2, a3, a4, a5, a6) => (a1, a2, a3, a4, a5, a6))

  /**
    * Generates `Coeval ~> F` (`FunctionK`) values for converting from `Coeval`
    * to supporting types (for which we have a [[CoevalLift]] instance).
    *
    * See [[https://typelevel.org/cats/datatypes/functionk.html the documentation]].
    *
    * {{{
    *   import cats.effect._
    *   import monix.eval._
    *   import java.io._
    *
    *   def open(file: File) =
    *     Resource[Coeval, InputStream](Coeval {
    *       val in = new FileInputStream(file)
    *       (in, Coeval(in.close()))
    *     })
    *
    *   // Lifting to a Resource of Task
    *   val res: Resource[Task, InputStream] =
    *     open(new File("sample")).mapK(Coeval.liftTo[Task])
    *
    *   // This was needed in order to process the resource
    *   // with a Task, instead of a Coeval
    *   res.use { in =>
    *     Task {
    *       in.read()
    *     }
    *   }
    * }}}
    */
  def liftTo[F[_]](implicit F: CoevalLift[F]): (Coeval ~> F) = F

  /**
    * Generates `Coeval ~> F` function values (`FunctionK`) for converting
    * from `Coeval` to types for which we have a `cats.effect.Sync` instance.
    *
    * @see [[https://typelevel.org/cats/datatypes/functionk.html cats.arrow.FunctionK]]
    * @see [[https://typelevel.org/cats-effect/typeclasses/sync.html cats.effect.Sync]]
    *
    * Prefer to use [[liftTo]], this alternative is provided in order to
    * force the usage of `cats.effect.Sync`, since [[CoevalLift]] is lawless.
    */
  def liftToSync[F[_]](implicit F: Sync[F]): (Coeval ~> F) =
    CoevalLift.toSync[F]

  /**
    * Returns a `F ~> Coeval` (`FunctionK`) for transforming any
    * supported data-type into [[Coeval]].
    *
    * Useful for `mapK` transformations, for example when working
    * with `Resource` or `Iterant`:
    *
    * {{{
    *   import cats.effect._
    *   import monix.eval._
    *   import java.io._
    *
    *   def open(file: File) =
    *     Resource[SyncIO, InputStream](SyncIO {
    *       val in = new FileInputStream(file)
    *       (in, SyncIO(in.close()))
    *     })
    *
    *   // Lifting to a Resource of Coeval
    *   val res: Resource[Coeval, InputStream] =
    *     open(new File("sample")).mapK(Coeval.liftFrom[SyncIO])
    * }}}
    *
    * See [[https://typelevel.org/cats/datatypes/functionk.html cats.arrow.FunctionK]].
    */
  def liftFrom[F[_]](implicit F: CoevalLike[F]): (F ~> Coeval) = F

  /**
    * Deprecated operations, described as extension methods.
    */
  implicit final class DeprecatedExtensions[+A](val self: Coeval[A]) extends AnyVal with CoevalDeprecated.Extensions[A]

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

    /** Converts this `Eager` value into a [[scala.util.Try]]. */
    final def toTry: Try[A] =
      this match {
        case Now(a) => Success(a)
        case Error(ex) => Failure(ex)
      }

    /** Converts this `Eager` value into a [[scala.Either]]. */
    final def toEither: Either[Throwable, A] =
      this match {
        case Now(a) => Right(a)
        case Error(ex) => Left(ex)
      }
  }

  object Eager {
    /** Promotes a non-strict value to a [[Coeval.Eager]]. */
    def apply[A](f: => A): Eager[A] =
      try Now(f)
      catch {
        case ex if NonFatal(ex) => Error(ex)
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
  final case class Now[+A](a: A) extends Eager[A] {
    override def value(): A = a
    override def apply(): A = a
    override def run(): Now[A] = this
    override def runAttempt(): Right[Nothing, A] = Right(a)
    override def runTry(): Success[A] = Success(a)
  }

  /** Constructs an eager [[Coeval]] instance for
    * a result that represents an error.
    */
  final case class Error(error: Throwable) extends Eager[Nothing] {
    override def apply(): Nothing = throw error
    override def run(): Error = this
    override def runAttempt(): Either[Throwable, Nothing] = Left(error)
    override def runTry(): Try[Nothing] = Failure(error)
  }

  /** Constructs a lazy [[Coeval]] instance.
    *
    * This type can be used for "lazy" values. In some sense it is
    * equivalent to using a Function0 value.
    */
  final case class Always[+A](f: () => A) extends Coeval[A] {
    override def apply(): A = f()

    override def run(): Eager[A] =
      try Now(f())
      catch { case e if NonFatal(e) => Error(e) }
    override def runAttempt(): Either[Throwable, A] =
      try Right(f())
      catch { case e if NonFatal(e) => Left(e) }
    override def runTry(): Try[A] =
      try Success(f())
      catch { case e if NonFatal(e) => Failure(e) }
  }

  /** Internal state, the result of [[Coeval.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Coeval[A]) extends Coeval[A]
  /** Internal [[Coeval]] state that is the result of applying `flatMap`. */
  private[eval] final case class FlatMap[S, A](source: Coeval[S], f: S => Coeval[A]) extends Coeval[A]

  /** Internal [[Coeval]] state that is the result of applying `map`. */
  private[eval] final case class Map[S, +A](source: Coeval[S], f: S => A, index: Int)
    extends Coeval[A] with (S => Coeval[A]) {

    def apply(value: S): Coeval[A] =
      new Now(f(value))
    override def toString: String =
      super[Coeval].toString
  }

  private val nowConstructor: (Any => Coeval[Nothing]) =
    ((a: Any) => new Now(a)).asInstanceOf[Any => Coeval[Nothing]]
  private val raiseConstructor: (Throwable => Coeval[Nothing]) =
    (e: Throwable) => new Error(e)

  /** Used as optimization by [[Coeval.failed]]. */
  private object Failed extends StackFrame[Any, Coeval[Throwable]] {
    def apply(a: Any): Coeval[Throwable] =
      Error(new NoSuchElementException("failed"))
    def recover(e: Throwable): Coeval[Throwable] =
      Now(e)
  }

  /** Used as optimization by [[Coeval.redeem]]. */
  private final class Redeem[A, B](fe: Throwable => B, fs: A => B) extends StackFrame[A, Coeval[B]] {

    def apply(a: A): Coeval[B] = Coeval.Now(fs(a))
    def recover(e: Throwable): Coeval[B] = Coeval.Now(fe(e))
  }

  /** Used as optimization by [[Coeval.attempt]]. */
  private object AttemptCoeval extends StackFrame[Any, Coeval[Either[Throwable, Any]]] {
    override def apply(a: Any): Coeval[Either[Throwable, Any]] =
      new Now(Right(a))
    override def recover(e: Throwable): Coeval[Either[Throwable, Any]] =
      new Now(Left(e))
  }

  /** Used as optimization by [[Coeval.materialize]]. */
  private object MaterializeCoeval extends StackFrame[Any, Coeval[Try[Any]]] {
    override def apply(a: Any): Coeval[Try[Any]] =
      new Now(Success(a))
    override def recover(e: Throwable): Coeval[Try[Any]] =
      new Now(Failure(e))
  }

  /** Instance of Cats type classes for [[Coeval]], implementing
    * `cats.effect.Sync` (which implies `Applicative`, `Monad`, `MonadError`)
    * and `cats.CoflatMap`.
    */
  implicit def catsSync: CatsSyncForCoeval =
    CatsSyncForCoeval

  /** Given an `A` type that has a `cats.Monoid[A]` implementation,
    * then this provides the evidence that `Coeval[A]` also has
    * a `Monoid[Coeval[A]]` implementation.
    */
  implicit def catsMonoid[A](implicit A: Monoid[A]): Monoid[Coeval[A]] =
    new CatsMonadToMonoid[Coeval, A]()(CatsSyncForCoeval, A)
}

private[eval] abstract class CoevalInstancesLevel0 extends CoevalDeprecated.Companion {
  /** Given an `A` type that has a `cats.Semigroup[A]` implementation,
    * then this provides the evidence that `Coeval[A]` also has
    * a `Semigroup[Coeval[A]]` implementation.
    *
    * This has a lower-level priority than [[Coeval.catsMonoid]]
    * in order to avoid conflicts.
    */
  implicit def catsSemigroup[A](implicit A: Semigroup[A]): Semigroup[Coeval[A]] =
    new CatsMonadToSemigroup[Coeval, A]()(Coeval.catsSync, A)
}
