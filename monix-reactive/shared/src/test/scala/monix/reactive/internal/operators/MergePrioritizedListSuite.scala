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

package monix.reactive.internal.operators

import monix.eval.Task
import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.execution.Ack.{ Continue, Stop }
import monix.reactive.observers.Subscriber
import monix.reactive.{ Observable, Observer }

import scala.concurrent.Future
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._

class MergePrioritizedListSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) =
    Some {
      val sources = (1 to sourceCount).map(i => (i, Observable.fromIterable(Seq.fill(4)(i.toLong))))
      val o = Observable.mergePrioritizedList(sources: _*)
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }

  def count(sourceCount: Int) =
    4 * sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val o = Observable.range(0L, sourceCount.toLong).mergeMap(_ => Observable.raiseError(ex))
    Some(Sample(o, 0, 0, Zero, Zero))
  }

  def sum(sourceCount: Int) = {
    4L * sourceCount * (sourceCount + 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Sample] = None

  override def cancelableObservables(): Seq[Sample] = {
    val sources1 = (1 to 100).map(i => (i, Observable.range(0, 100).delayExecution(2.second)))
    val sample1 = Observable.mergePrioritizedList(sources1: _*)
    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 0, 0, 1.seconds, 0.seconds)
    )
  }

  fixture.test("should return Observable.empty if sources empty") { implicit s =>
    assertEquals(Observable.mergePrioritizedList(), Observable.empty)
  }

  fixture.test("should pick items in priority order") { implicit s =>
    val sources = (1 to 10).map(i => (i, Observable.now(i * 1L)))
    val source = Observable.mergePrioritizedList(sources: _*)
    var last = 0L
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        Task
          .sleep(FiniteDuration(1, SECONDS)) // sleep so that other source observables' items queued before next call
          .map { _ =>
            last = elem
            Continue
          }
          .runToFuture
      }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = ()
    })

    (10L to 1L by -1).foreach { p =>
      s.tick(1.seconds)
      assertEquals(last, p)
    }
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  fixture.test("should push all items downstream before calling onComplete") { implicit s =>
    val source =
      Observable.mergePrioritizedList((1, Observable.now(1L)), (1, Observable.now(1L)), (1, Observable.now(1L)))
    var count = 0L
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        Task
          .sleep(FiniteDuration(1, SECONDS))
          .map { _ =>
            count += elem
            Continue
          }
          .runToFuture
      }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = assertEquals(count, 3L)
    })

    s.tick(1.seconds)
    assertEquals(count, 1L)
    s.tick(1.seconds)
    assertEquals(count, 2L)
    s.tick(1.seconds)
    assertEquals(count, 3L)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  fixture.test("should complete all upstream onNext promises when downstream stops early") { implicit s =>
    val sources = (1 to 10).map(i => (i, new OnNextExposingObservable(i * 1L)))
    val source = Observable.mergePrioritizedList(sources: _*)

    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        Task
          .sleep(FiniteDuration(1, SECONDS)) // sleep so that other source observables' items queued before next call
          .map { _ =>
            Stop
          }
          .runToFuture
      }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = ()
    })

    s.tick(1.seconds)
    sources.foreach(src => assert(src._2.onNextRes.exists(_.isCompleted), "source promise completed"))
  }

  fixture.test("should complete all upstream onNext promises when downstream errors early") { implicit s =>
    val sources = (1 to 10).map(i => (i, new OnNextExposingObservable(i * 1L)))
    val source = Observable.mergePrioritizedList(sources: _*)

    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        Task
          .sleep(FiniteDuration(1, SECONDS)) // sleep so that other source observables' items queued before next call
          .map { _ =>
            throw new RuntimeException("downstream failed")
          }
          .runToFuture
      }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = ()
    })

    s.tick(1.seconds)
    sources.foreach(src => assert(src._2.onNextRes.exists(_.isCompleted), "source promise completed"))
  }

  // Enables verification that MergePrioritizedListObservable completes
  // upstream onNext promises when downstream stops early
  private class OnNextExposingObservable[+A](elem: A)(implicit s: Scheduler) extends Observable[A] {
    var onNextRes: Option[Future[Ack]] = None

    def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
      onNextRes = Some(subscriber.onNext(elem))
      subscriber.onComplete()
      Cancelable.empty
    }
  }
}
