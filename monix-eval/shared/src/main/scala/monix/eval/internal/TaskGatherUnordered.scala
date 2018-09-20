/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval.internal

import monix.eval.Task.Async
import monix.eval.{Callback, Task}
import monix.execution.Scheduler
import monix.execution.atomic.{Atomic, AtomicAny}
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.cancelables.{CompositeCancelable, StackedCancelable}
import scala.util.control.NonFatal

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

private[eval] object TaskGatherUnordered {
  /**
    * Implementation for `Task.gatherUnordered`
    */
  def apply[A](in: TraversableOnce[Task[A]]): Task[List[A]] = {
    Async(
      new Register(in),
      trampolineBefore = true,
      trampolineAfter = true,
      restoreLocals = true)
  }

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class Register[A](in: TraversableOnce[Task[A]])
    extends ForkedRegister[List[A]] {

    def maybeSignalFinal(
      ref: AtomicAny[State[A]],
      currentState: State[A],
      mainConn: StackedCancelable,
      finalCallback: Callback[List[A]])
      (implicit s: Scheduler): Unit = {

      currentState match {
        case State.Active(list, 0) =>
          ref.lazySet(State.Complete)
          mainConn.pop()
          if (list ne Nil)
            finalCallback.onSuccess(list)
          else {
            // Needs to force async execution in case we had no tasks,
            // due to the contract of ForkedStart
            s.executeAsync(() => finalCallback.onSuccess(list))
          }
        case _ =>
          () // invalid state
      }
    }

    def reportError(
      stateRef: AtomicAny[State[A]],
      mainConn: StackedCancelable,
      ex: Throwable,
      finalCallback: Callback[List[A]])(implicit s: Scheduler): Unit = {

      val currentState = stateRef.getAndSet(State.Complete)
      if (currentState != State.Complete) {
        mainConn.pop().cancel()
        finalCallback.onError(ex)
      } else {
        s.reportFailure(ex)
      }
    }

    def apply(context: TaskContext, finalCallback: Callback[List[A]]): Unit = {
      @tailrec def activate(
        stateRef: AtomicAny[State[A]],
        count: Int,
        conn: StackedCancelable,
        finalCallback: Callback[List[A]])
        (implicit s: Scheduler): Unit = {

        stateRef.get match {
          case current @ State.Initializing(_,_) =>
            val update = current.activate(count)
            if (!stateRef.compareAndSet(current, update))
              activate(stateRef, count, conn, finalCallback)(s)
            else
              maybeSignalFinal(stateRef, update, conn, finalCallback)(s)

          case _ =>
            () // do nothing
        }
      }

      implicit val s = context.scheduler
      // Shared state for synchronization
      val stateRef = Atomic.withPadding(State.empty[A], LeftRight128)

      try {
        // Represents the collection of cancelables for all started tasks
        val composite = CompositeCancelable()
        val mainConn = context.connection
        mainConn.push(composite)

        // Collecting all cancelables in a buffer, because adding
        // cancelables one by one in our `CompositeCancelable` is
        // expensive, so we do it at the end
        val allCancelables = ListBuffer.empty[StackedCancelable]
        val batchSize = s.executionModel.recommendedBatchSize
        val cursor = in.toIterator

        var continue = true
        var count = 0

        // The `isActive` check short-circuits the process in case
        // we have a synchronous task that just completed in error
        while (cursor.hasNext && continue) {
          val task = cursor.next()
          count += 1
          continue = count % batchSize != 0 || stateRef.get.isActive

          val stacked = StackedCancelable()
          val childCtx = context.withConnection(stacked)
          allCancelables += stacked

          // Light asynchronous boundary
          Task.unsafeStartEnsureAsync(task, childCtx,
            new Callback[A] {
              @tailrec
              def onSuccess(value: A): Unit = {
                val current = stateRef.get
                if (current.isActive) {
                  val update = current.enqueue(value)
                  if (!stateRef.compareAndSet(current, update))
                    onSuccess(value) // retry
                  else
                    maybeSignalFinal(stateRef, update, context.connection, finalCallback)
                }
              }

              def onError(ex: Throwable): Unit =
                reportError(stateRef, mainConn, ex, finalCallback)
            })
        }

        // Note that if an error happened, this should cancel all
        // other active tasks.
        composite ++= allCancelables
        // We are done triggering tasks, now we can allow the final
        // callback to be triggered
        activate(stateRef, count, mainConn, finalCallback)(s)
      } catch {
        case ex if NonFatal(ex) =>
          reportError(stateRef, context.connection, ex, finalCallback)
      }
    }
  }

  private sealed abstract class State[+A] {
    def isActive: Boolean
    def enqueue[B >: A](value: B): State[B]
  }

  private object State {
    def empty[A]: State[A] =
      Initializing(List.empty, 0)

    case object Complete extends State[Nothing] {
      def isActive = false

      override def enqueue[B >: Nothing](value: B): State[B] =
        this
    }

    final case class Initializing[+A](list: List[A], remaining: Int)
      extends State[A] {

      def isActive = true
      def enqueue[B >: A](value: B): Initializing[B] =
        Initializing(value :: list, remaining - 1)

      def activate(totalCount: Int): Active[A] =
        Active(list, remaining + totalCount)
    }

    final case class Active[+A](list: List[A], remaining: Int)
      extends State[A] {

      def isActive = true
      def enqueue[B >: A](value: B): Active[B] =
        Active(value :: list, remaining - 1)
    }
  }
}
