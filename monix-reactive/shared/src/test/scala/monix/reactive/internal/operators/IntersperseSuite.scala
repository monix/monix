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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}

import scala.concurrent.duration._

object IntersperseSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Option {
    val sample = Observable.range(0, sourceCount).intersperse(1L)
    Sample(sample, count(sourceCount), sum(sourceCount), 0.seconds, 0.seconds)
  }

  def count(sourceCount: Int) = sourceCount*2 - 1
  def sum(sourceCount: Int) = (sourceCount*(sourceCount - 1))/2 + sourceCount - 1

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val sample = createObservableEndingInError(Observable.range(0, sourceCount), ex).intersperse(0L)
    Sample(sample, count(sourceCount), sum(sourceCount), 0.seconds, 0.seconds)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample = Observable.range(0, 10).delayOnNext(1.second).intersperse(0L)
    Seq(Sample(sample, 0, 0, 0.seconds, 0.seconds))
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("start is the first emitted element") { implicit s =>
    val obs = PublishSubject[Int]()

    var received = Vector.empty[Int]
    var wasCompleted = false

    obs.intersperse(start = -1, separator = -2, end = -3).subscribe(new Observer[Int] {
      def onNext(elem: Int) = {
        received :+= elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs.onNext(1); s.tick()
    assertEquals(received.headOption, Some(-1))

    obs.onComplete(); s.tick()
    assert(wasCompleted)
  }

  test("end is the last emitted element") { implicit s =>
    val obs = PublishSubject[Int]()

    var received = Vector.empty[Int]
    var wasCompleted = false

    obs.intersperse(start = -1, separator = -2, end = -3).unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = {
        received :+= elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs.onNext(1); s.tick()
    obs.onNext(2); s.tick()
    obs.onComplete(); s.tick()
    assertEquals(received.lastOption, Some(-3))
    assert(wasCompleted)
  }

  test("separator is paired with emitted elements") { implicit s =>
    val obs = PublishSubject[Int]()

    var received = Vector.empty[Int]
    var wasCompleted = false

    obs.intersperse(-1,0,-1).unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = {
        received :+= elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs.onNext(1); s.tick()
    obs.onNext(2); s.tick()
    obs.onNext(3); s.tick()
    obs.onNext(4); s.tick()
    obs.onNext(5); s.tick()
    obs.onComplete(); s.tick()

    assertEquals(received, Vector(-1,1,0,2,0,3,0,4,0,5,-1))
    assert(wasCompleted)
  }

  test("do not emit if upstream emits nothing") { implicit s =>
    val obs = PublishSubject[Int]()

    var received = Vector.empty[Int]
    var wasCompleted = false

    obs.intersperse(start = -1, separator = -2, end = -3).unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int) = {
        received :+= elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    obs.onComplete(); s.tick()
    assertEquals(received, List())
    assert(wasCompleted)
  }
}