/*
 * Copyright (c) 2014-2017 by The Tonix Project Developers.
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

package monix.reactive.subjects

import monix.execution._
import monix.reactive._
import monix.reactive.observers._

import scala.concurrent.Future

/** `StateSubject` is a `Subject` that processes all values it observes as
  * transformations to a value that is - in turn - observed by others.
  *
  * @see `Subject`
  *
  * Example:
  * <pre>
  *   <code>
  *     sealed trait StackTransform
  *
  *     case class  Push[T](x: T) extends StackTransform
  *     case object Pop           extends StackTransform
  *
  *     val stack = StateSubject[StackTransform,List[Int]](List.empty) {
  *       case (xs, Push(x: Int)) => x :: xs
  *       case (xs, Pop)          => xs drop 1
  *     }
  *
  *     val watcher = stack foreach (println _)
  *
  *     stack onNext Push(1)
  *     stack onNext Push(2)
  *     stack onNext Push(3)
  *     stack onNext Pop
  *     stack onNext Pop
  *   </code>
  * </pre>
  * The above should print out:
  * <pre>
  *     List()
  *     List(1)
  *     List(2, 1)
  *     List(3, 2, 1)
  *     List(2, 1)
  *     List(1)
  * </pre>
  */
abstract class StateSubject[T, A](initial: A) extends Subject[T, A] {
  val observer: Subject[T, T] = PublishSubject[T]()

  // adhere to the observer contract
  def onNext(m: T): Future[Ack] = observer.onNext(m)
  def onComplete(): Unit = observer.onComplete()
  def onError(ex: Throwable): Unit = observer.onError(ex)

  // adhere to Subject trait
  def size: Int = observer.size

  // function to be implemented by instance
  def update(a: A, t: T): A

  // however, ignore the observable side of our subject, and use this instead...
  val observable: Observable[A] = initial +: observer.scan(initial)(update)

  // subscribe to the transformed result
  def unsafeSubscribeFn(s: Subscriber[A]): Cancelable = observable.subscribe(s)
}

object StateSubject {
  /** Create a new `StateSubject` with a `PartialFunction` update.
    */
  def apply[T, A](initial: A)(pf: PartialFunction[(A, T), A]): StateSubject[T, A] =
    new StateSubject[T, A](initial) {
      def update(a: A, t: T): A = if (pf.isDefinedAt((a, t))) pf((a, t)) else a
    }
}
