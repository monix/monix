/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.kernel.{ Async, Fiber, Poll }
import monix.eval.internal.IOFiber
import monix.eval.instances.CatsAsyncForIO
import monix.execution.{ Callback, Cancelable, CancelableFuture, Scheduler }

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.Promise
import scala.util.control.NonFatal

/** A lazy description of an effect, interpreted by [[internal.IOFiber]].
  *
  * Constructors and combinators build a small set of nodes rather than evaluating
  * user code. The fiber run-loop owns traversal of those nodes, its continuation
  * stack, and the cancellation mask. Asynchronous callbacks publish another `IO`
  * node through the fiber's serialized resumption protocol.
  */
sealed abstract class IO[+A] {
  private[eval] def tag: Byte
  private[eval] def accept[AA >: A, R](visitor: IO.Visitor[AA, R]): R

  def flatMap[B](f: A => IO[B]): IO[B] =
    IO.FlatMap(this, f)

  def map[B](f: A => B): IO[B] =
    flatMap(a => IO.pure(f(a)))

  def handleErrorWith[AA >: A](f: Throwable => IO[AA]): IO[AA] =
    IO.HandleErrorWith(this, f)

  def onCancel(fin: IO[Unit]): IO[A] =
    IO.OnCancel(this, fin)

  def start: IO[Fiber[IO, Throwable, A @uncheckedVariance]] =
    IO.Start(this)

  /** Starts a new fiber and returns its effectful cancellation action.
    * Evaluating the returned token requests cancellation and waits for finalizers;
    * merely obtaining it does not cancel the fiber.
    */
  def unsafeRunAsync(cb: Callback[Throwable, A])(implicit s: Scheduler): CancelToken[IO] = {
    val fiber = new IOFiber[A](this, Callback.safe(cb))(s)
    s.execute(fiber)
    fiber.cancel
  }

  def unsafeRunAndForget()(implicit s: Scheduler): Unit =
    unsafeRunAsync(Callback.empty)

  /** Starts this IO and exposes its success/error channel as a `CancelableFuture`.
    * Future cancellation evaluates the fiber's effectful cancel token in a separate
    * run. Fiber cancellation has no callback value, so the canceled future remains
    * incomplete.
    */
  def unsafeRunToFuture()(implicit s: Scheduler): CancelableFuture[A] = {
    val p = Promise[A]()
    val cancel = unsafeRunAsync(Callback.fromPromise(p))
    CancelableFuture(p.future, Cancelable(cancel.unsafeRunAndForget _))
  }
}

object IO {

  implicit lazy val catsEffectAsyncForIO: Async[IO] =
    new CatsAsyncForIO

  def pure[A](a: A): IO[A] = Pure(a)

  def delay[A](thunk: => A): IO[A] =
    // Encoding delay as a bind keeps thunk evaluation inside the ordinary run-loop
    // exception boundary, without requiring a dedicated algebra node.
    FlatMap[Unit, A](
      Pure(()),
      _ => {
        try
          Pure(thunk)
        catch {
          case NonFatal(e) =>
            RaiseError(e)
        }
      }
    )

  def defer[A](thunk: => IO[A]): IO[A] =
    delay(thunk).flatMap(identity)

  def raiseError[A](e: Throwable): IO[A] =
    RaiseError(e)

  def never[A]: IO[A] =
    AsyncSimple((_, _) => ())

  val canceled: IO[Unit] =
    Cancelled

  def uncancelable[A](body: Poll[IO] => IO[A]): IO[A] =
    Uncancelable(body)

  def async[A](register: Callback[Throwable, A] => Unit): IO[A] =
    AsyncSimple((sc, cb) => protectRegistration(sc, cb)(register))

  def async0[A](register: (Scheduler, Callback[Throwable, A]) => Unit): IO[A] =
    AsyncSimple((sc, cb) => protectRegistration(sc, cb)(safe => register(sc, safe)))

  def cont[S, A](cont: (Callback[Throwable, S], IO[S]) => IO[A]): IO[A] =
    AsyncCont[S, A]((_, cb, get) => cont(cb, get))

  def cont0[S, A](cont: (Scheduler, Callback[Throwable, S], IO[S]) => IO[A]): IO[A] =
    AsyncCont(cont)

  private def protectRegistration[A](
    scheduler: Scheduler,
    callback: Callback[Throwable, A]
  )(register: Callback[Throwable, A] => Unit): Unit = {
    // A registration exception is an effect error only while no result has won.
    // Once the callback has completed, the exception is late and can only be
    // reported to the scheduler.
    val safe = Callback.safe(callback)(scheduler)
    try register(safe)
    catch {
      case NonFatal(error) =>
        if (!safe.tryOnError(error))
          scheduler.reportFailure(error)
    }
  }

  private[eval] final case class Pure[+A](a: A) extends IO[A] {
    def tag: Byte = Pure.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Pure {
    final val TAG: Byte = 0
  }

  private[eval] final case class RaiseError(e: Throwable) extends IO[Nothing] {
    def tag: Byte = RaiseError.TAG
    def accept[AA >: Nothing, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object RaiseError {
    final val TAG: Byte = 1
  }

  private[eval] final case class FlatMap[A, +B](source: IO[A], f: A => IO[B]) extends IO[B] {
    def tag: Byte = FlatMap.TAG
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object FlatMap {
    final val TAG: Byte = 2
  }

  private[eval] final case class HandleErrorWith[A, +B](source: IO[A], f: Throwable => IO[B])
    extends IO[B] {
    def tag: Byte = HandleErrorWith.TAG

    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object HandleErrorWith {
    final val TAG: Byte = 3
  }

  private[eval] final case class OnCancel[+A](source: IO[A], onCancel: IO[Unit]) extends IO[A] {
    def tag: Byte = OnCancel.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object OnCancel {
    final val TAG: Byte = 4
  }

  /** An asynchronous registration together with its run-loop boundary policy.
    *
    * `boundaryBefore` controls how registration starts; `boundaryAfter` controls
    * how its callback re-enters the fiber. [[internal.IORestartCallback]] interprets
    * both policies and is reused across successive asynchronous nodes.
    */
  private[eval] final case class AsyncSimple[+A](
    register: (Scheduler, Callback[Throwable, A]) => Unit,
    boundaryBefore: AsyncSimple.BoundaryPolicy = AsyncSimple.AsyncShifted,
    boundaryAfter: AsyncSimple.BoundaryPolicy = AsyncSimple.AsyncTrampolined,
  ) extends IO[A] {
    def tag: Byte = AsyncSimple.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object AsyncSimple {
    // Byte encoding keeps the policy in the node without another allocation.
    type BoundaryPolicy = Byte
    final val TAG: Byte = 5
    final val AsyncTrampolined: BoundaryPolicy = 0.toByte
    final val AsyncShifted: BoundaryPolicy = 1.toByte
    final val Synchronous: BoundaryPolicy = 2.toByte
  }

  /** Primitive continuation handshake used to implement Cats Effect `Async.cont`.
    *
    * The callback and `get` effect may be observed in either order. The fiber joins
    * them through [[internal.IOCallbackIndirection]], retaining an early result until
    * `get` is evaluated.
    */
  private[eval] final case class AsyncCont[A, +B](
    cont: (Scheduler, Callback[Throwable, A], IO[A]) => IO[B]
  ) extends IO[B] {
    def tag: Byte = AsyncCont.TAG
    def accept[BB >: B, R](visitor: Visitor[BB, R]): R = visitor.visit(this)
  }

  private[eval] object AsyncCont {
    final val TAG: Byte = 6
  }

  private[eval] case object Cancelled extends IO[Nothing] {
    final val TAG: Byte = 7
    def tag: Byte = TAG
    def accept[AA >: Nothing, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] final case class Uncancelable[+A](body: Poll[IO] => IO[A]) extends IO[A] {
    def tag: Byte = Uncancelable.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Uncancelable {
    final val TAG: Byte = 8
  }

  /** A request to lower one cancellation mask level.
    *
    * The `owner` must be the interpreting fiber, and `id` must match its current
    * mask depth.
    * This prevents a token from lowering another fiber or a different current level;
    * as required by `Poll`, callers must keep the token within its creating body.
    */
  private[eval] final case class PollRegion[+A](source: IO[A], id: Int, owner: AnyRef) extends IO[A] {
    def tag: Byte = PollRegion.TAG
    def accept[AA >: A, R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object PollRegion {
    final val TAG: Byte = 9
  }

  private[eval] final case class Start[A](source: IO[A]) extends IO[Fiber[IO, Throwable, A]] {
    def tag: Byte = Start.TAG
    def accept[AA >: Fiber[IO, Throwable, A], R](visitor: Visitor[AA, R]): R = visitor.visit(this)
  }

  private[eval] object Start {
    final val TAG: Byte = 10
  }

  /** Visitor used by the runtime. Tags let the hot run-loop dispatch selected nodes
    * directly while retaining typed handlers for the remaining nodes.
    */
  private[eval] trait Visitor[A, +R] {
    def visit(ref: Pure[A]): R
    def visit(ref: RaiseError): R
    def visit[S](ref: FlatMap[S, A]): R
    def visit[S](ref: HandleErrorWith[S, A]): R
    def visit(ref: OnCancel[A]): R
    def visit[S](ref: AsyncCont[S, A]): R
    def visit(ref: AsyncSimple[A]): R
    def visit(ref: Cancelled.type): R
    def visit(ref: Uncancelable[A]): R
    def visit(ref: PollRegion[A]): R
    def visit[S](ref: Start[S]): R
  }
}
