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

import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.reactive.{BaseTestSuite, Observable, Observer}
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

object ObservableOpsReturningTaskSuite extends BaseTestSuite {
  def first[A](obs: Observable[A])(implicit s: Scheduler): Future[Try[Option[A]]] = {
    val p = Promise[Try[Option[A]]]()
    obs.unsafeSubscribeFn(new Observer.Sync[A] {
      def onNext(elem: A) = { p.trySuccess(Success(Some(elem))); Stop }
      def onError(ex: Throwable): Unit = p.tryFailure(ex)
      def onComplete(): Unit = p.trySuccess(Success(None))
    })
    p.future
  }

  test("runAsyncGetFirst works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      obs.runAsyncGetFirst.materialize <-> first(obs)
    }
  }

  test("runAsyncGetLast works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      obs.runAsyncGetLast.materialize <-> first(obs.lastF)
    }
  }

  test("countL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Long]]] =
        obs.countL.map(Some.apply).materialize.runAsync

      result <-> Future.successful(Success(Some(list.length)))
    }
  }

  test("countL is equivalent with countF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Long]]] =
        obs.countL.map(Some.apply).materialize.runAsync

      result <-> first(obs.countF)
    }
  }

  test("existsL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Boolean]]] =
        obs.existsL(_ % 3 == 0).map(Some.apply).materialize.runAsync

      result <-> Future.successful(Success(Some(list.exists(_ % 3 == 0))))
    }
  }

  test("existsL is equivalent with existsF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Boolean]]] =
        obs.existsL(_ % 3 == 0).map(Some.apply).materialize.runAsync

      result <-> first(obs.existsF(_ % 3 == 0))
    }
  }

  test("findL is equivalent with findF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Int]]] =
        obs.findL(_ % 3 == 0).materialize.runAsync

      result <-> first(obs.findF(_ % 3 == 0))
    }
  }

  test("foldLeftL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Int]]] =
        obs.foldLeftL(0)(_+_).map(Some.apply).materialize.runAsync

      result <-> Future.successful(Success(Some(list.sum)))
    }
  }

  test("foldWhileL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val sum1 = obs.foldLeftL(0)(_+_)
      val sum2 = obs.foldWhileLeftL(0)((acc,e) => Left(acc + e))
      sum1 <-> sum2
    }
  }

  test("forAllL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Boolean]]] =
        obs.forAllL(_ >= 0).map(Some.apply).materialize.runAsync

      result <-> Future.successful(Success(Some(list.forall(_ >= 0))))
    }
  }

  test("forAllL is equivalent with forAllF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Boolean]]] =
        obs.forAllL(_ >= 0).map(Some.apply).materialize.runAsync

      result <-> first(obs.forAllF(_ >= 0))
    }
  }

  test("firstOptionL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.firstOptionL
      result <-> Task.now(list.headOption)
    }
  }

  test("firstL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.firstL.onErrorHandle(_ => -101)
      result <-> Task.now(list.headOption.getOrElse(-101))
    }
  }

  test("headL is equivalent with firstL") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      obs.headL.onErrorHandle(_ => -101) <-> obs.firstL.onErrorHandle(_ => -101)
    }
  }

  test("headOptionL is equivalent with firstOptionL") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      obs.headOptionL <-> obs.firstOptionL
    }
  }

  test("headOrElseL is equivalent with firstOrElseL") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      obs.map(Some.apply).headOrElseL(None) <->
        obs.map(Some.apply).firstOrElseL(None)
    }
  }

  test("lastOptionL is equivalent with lastF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Int]]] =
        obs.lastOptionL.materialize.runAsync

      result <-> first(obs.lastF)
    }
  }

  test("lastOrElseL is equivalent with lastF") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result: Future[Try[Option[Int]]] =
        obs.map(Some.apply).lastOrElseL(None)
          .materialize.runAsync

      result <-> first(obs.lastF)
    }
  }

  test("lastL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.lastL.onErrorHandle(_ => -101)
      result <-> Task.now(list.lastOption.getOrElse(-101))
    }
  }

  test("isEmptyL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.isEmptyL
      result <-> Task.now(list.isEmpty)
    }
  }

  test("nonEmptyL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.nonEmptyL
      result <-> Task.now(list.nonEmpty)
    }
  }

  test("maxL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.maxL.map(_.getOrElse(-101))
      result <-> Task.now(Try(list.max).getOrElse(-101))
    }
  }

  test("maxByL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.maxByL(identity).map(_.getOrElse(-101))
      result <-> Task.now(Try(list.max).getOrElse(-101))
    }
  }

  test("minL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.minL.map(_.getOrElse(-101))
      result <-> Task.now(Try(list.min).getOrElse(-101))
    }
  }

  test("minByL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.minByL(identity).map(_.getOrElse(-101))
      result <-> Task.now(Try(list.min).getOrElse(-101))
    }
  }

  test("sumL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.sumL
      result <-> Task.now(list.sum)
    }
  }

  test("toListL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val result = obs.toListL
      result <-> Task.now(list)
    }
  }

  test("foreachL works") { implicit s =>
    check1 { (list: List[Int]) =>
      val obs = Observable.fromIterable(list)
      val sumRef = Atomic(0)
      val result: Future[Int] = obs.foreachL(sumRef.increment).runAsync.map(_ => sumRef.get)
      result <-> Future.successful(list.sum)
    }
  }
}
