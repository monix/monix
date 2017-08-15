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

package monix.tail
package internal

import cats.effect.{Effect, IO}
import cats.syntax.all._
import monix.eval.instances.CatsAsyncInstances
import monix.eval.{Callback, Task}
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.misc.NonFatal
import monix.execution.rstreams.Subscription
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import org.reactivestreams.{Publisher, Subscriber}

private[tail] object IterantToReactivePublisher {
  /**
    * Implementation of `Iterant.toReactivePublisher`
    */
  def apply[F[_], A](self: Iterant[F, A])
    (implicit F: Effect[F], ec: Scheduler): Publisher[A] =
    new IterantPublisher[F, A](self)

  private final class IterantPublisher[F[_], A](source: Iterant[F, A])
    (implicit F: Effect[F], ec: Scheduler)
    extends Publisher[A] {

    override def subscribe(out: Subscriber[_ >: A]): Unit = {
      if (out == null) throw null

      runAsync(reduceToNext(source, out), new Callback[Iterant[F, A]] {
        def onSuccess(start: Iterant[F, A]): Unit =
          if (start ne null) {
            val sub: Subscription = new IterantSubscription[F, A](start, out)
            out.onSubscribe(sub)
          }

        def onError(ex: Throwable): Unit =
          out.onError(ex)
      })
    }

    private def reduceToNext(source: Iterant[F, A], out: Subscriber[_ >: A]): F[Iterant[F, A]] =
      source match {
        case Suspend(rest, _) =>
          rest
        case Halt(opt) =>
          out.onSubscribe(Subscription.empty)
          opt.fold(out.onComplete())(out.onError)
          F.pure(null)
        case other =>
          F.pure(other)
      }

    private val runAsync: (F[Iterant[F, A]], Callback[Iterant[F, A]]) => Unit = {
      import cats.effect.IO.ioEffect

      F.asInstanceOf[Any] match {
        case _: CatsAsyncInstances.ForTask =>
          (fa, cb) => fa.asInstanceOf[Task[Iterant[F, A]]].runAsync(cb)
        case `ioEffect` =>
          (fa, cb) => fa.asInstanceOf[IO[Iterant[F, A]]].unsafeRunAsync(r => cb(r))
        case _ =>
          val cb = Callback.empty[Unit]
          val ecb = (e: Either[Throwable, Unit]) => cb(e)
          (fa, cb) => F.runAsync(fa) { r => cb(r); IO.unit }.unsafeRunAsync(ecb)
      }
    }
  }

  private final class IterantSubscription[F[_], A](source: Iterant[F, A], out: Subscriber[_ >: A])
    (implicit F: Effect[F], ec: Scheduler)
    extends Subscription {

    // Keeps track of the currently requested items
    private[this] val requested = Atomic.withPadding(0L, LeftRight128)
    // MUST BE set before `requested` gets decremented
    private[this] var cursor: F[Iterant[F, A]] = F.pure(source)

    private[this] val concurrentEndSignal =
      Atomic.withPadding(null : Option[Throwable], LeftRight128)

    private val runAsync: F[Unit] => Unit = {
      import cats.effect.IO.ioEffect

      F.asInstanceOf[Any] match {
        case `ioEffect` =>
          val cb = Callback.empty[Unit]
          val ecb: Either[Throwable, Unit] => Unit = r => cb(r)
          fa => fa.asInstanceOf[IO[Unit]].unsafeRunAsync(ecb)
        case _: CatsAsyncInstances.ForTask =>
          val cb = Callback.empty[Unit]
          fa => fa.asInstanceOf[Task[Unit]].runAsync(cb)
        case _ =>
          val cb = Callback.empty[Unit]
          val ecbIO: Either[Throwable, Unit] => IO[Unit] = r => { cb(r); IO.unit }
          val ecb: Either[Throwable, Unit] => Unit = r => cb(r)
          fa => F.runAsync(fa)(ecbIO).unsafeRunAsync(ecb)
      }
    }

    private def goNext(rest: F[Iterant[F, A]], stop: F[Unit], requested: Long, processed: Int): F[Unit] = {
      // Fast-path, avoids doing any volatile operations
      val isInfinite = requested == Long.MaxValue
      if (isInfinite || processed < requested) {
        val n2 = if (!isInfinite) processed else 0
        rest.flatMap(loop(requested, n2))
      }
      else {
        // Happens-before relationship with the `requested` decrement!
        cursor = rest
        // Remaining items to process
        val n2 = this.requested.decrementAndGet(processed)

        // In case of zero, the loop needs to stop
        // due to no more requests from downstream
        if (n2 != 0)
          rest.flatMap(loop(n2, 0))
        else
          F.unit
      }
    }

    private def processCursor(source: NextCursor[F, A], requested: Long, processed: Int): F[Unit] = {
      val NextCursor(ref, rest, stop) = source
      val toTake = math.min(ref.recommendedBatchSize, requested - processed).toInt
      val modulus = ec.executionModel.batchedExecutionModulus
      var isActive = true
      var i = 0

      while (isActive && i < toTake && ref.hasNext()) {
        val a = ref.next()
        i += 1
        out.onNext(a)

        // Check if still active, but in batches, because reading
        // from a volatile is an expensive operation
        if ((i & modulus) == 0) isActive = this.concurrentEndSignal.get ne null
      }

      val next = if (ref.hasNext()) F.pure(source : Iterant[F, A]) else rest
      goNext(next, stop, requested, processed + i)
    }

    private def loop(requested: Long, processed: Int)(source: Iterant[F, A]): F[Unit] = {
      val concurrentEndSignal = this.concurrentEndSignal.get

      if (concurrentEndSignal != null) {
        // Subscription was cancelled, triggering early stop
        cursor = F.pure(Halt(concurrentEndSignal))
        this.requested.set(0)
        val next = source.earlyStop

        concurrentEndSignal match {
          case None => next
          case Some(e) =>
            source.earlyStop.map(_ => out.onError(e))
        }
      }
      else if (requested > processed) {
        // Guard for protocol violations. In case we've sent a final
        // `onComplete` or `onError`, then we can no longer stream
        // errors by means of `onError`, since that would violate
        // the protocol.
        var streamErrors = true
        try source match {
          case Next(a, rest, stop) =>
            out.onNext(a)
            goNext(rest, stop, requested, processed + 1)

          case ref @ NextCursor(_, _, _) =>
            processCursor(ref, requested, processed)

          case NextBatch(ref, rest, stop) =>
            processCursor(NextCursor(ref.cursor(), rest, stop), requested, processed)

          case Suspend(rest, stop) =>
            goNext(rest, stop, requested, processed)

          case Last(a) =>
            out.onNext(a)
            streamErrors = false
            out.onComplete()
            F.unit

          case Halt(errorOpt) =>
            streamErrors = false
            errorOpt match {
              case None => out.onComplete()
              case Some(e) => out.onError(e)
            }
            F.unit
        }
        catch {
          case NonFatal(e) =>
            source.earlyStop.map { _ =>
              if (streamErrors) out.onError(e)
              else ec.reportFailure(e)
            }
        }
      } else {
        // Retry
        goNext(F.pure(source), source.earlyStop, requested, processed)
      }
    }

    private val startLoop: (Long => Unit) =
      ec.executionModel match {
        case AlwaysAsyncExecution =>
          n => ec.executeAsync(() => runAsync(cursor.flatMap(loop(n, 0))))
        case _ =>
          n => ec.executeTrampolined(() => runAsync(cursor.flatMap(loop(n, 0))))
      }

    def request(n: Long): Unit = {
      if (n <= 0) {
        stopWithSignal(Some(
          new IllegalArgumentException(
            "n must be strictly positive, according to " +
            "the Reactive Streams contract, rule 3.9"
          )))
      }
      else {
        val prev = requested.getAndTransform { nr => if (nr < 0) nr else nr + n }
        if (prev == 0) startLoop(n)
      }
    }

    def cancel(): Unit = {
      stopWithSignal(None)
    }

    private def stopWithSignal(e: Option[Throwable]): Unit = {
      concurrentEndSignal.compareAndSet(null, e)
      startLoop(0)
    }
  }
}
