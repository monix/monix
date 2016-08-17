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

package monix.scalaz

import minitest.SimpleTestSuite
import monix.eval.{Coeval, Task}
import monix.reactive.Observable
import monix.types._
import monix.types.shims._

object InstancesVisibilitySuite extends SimpleTestSuite {
  test("Functor instance is visible") {
    implicit val monixInstance: Functor[Task] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
  }

  test("Applicative instance is visible") {
    implicit val monixInstance: Applicative[Task] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Task]]
    assert(scalazApplicative != null)
  }

  test("Monad instance is visible") {
    implicit val monixInstance: Monad[Task] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Task]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Task]]
    assert(scalazMonad != null)
  }

  test("MonadError instance is visible") {
    implicit val monixInstance: MonadError[Task,Throwable] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Task]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Task]]
    assert(scalazMonad != null)
    val scalazMonadError = implicitly[_root_.scalaz.MonadError[Task,Throwable]]
    assert(scalazMonadError != null)
  }

  test("Cobind instance is visible") {
    implicit val monixInstance: CoflatMap[Task] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Task]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Task]]
    assert(scalazMonad != null)
    val scalazMonadError = implicitly[_root_.scalaz.MonadError[Task,Throwable]]
    assert(scalazMonadError != null)
    val scalazCobind = implicitly[_root_.scalaz.Cobind[Task]]
    assert(scalazCobind != null)
  }

  test("Comonad instance is visible") {
    implicit val monixInstance: Comonad[Coeval] = Coeval.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Coeval]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Coeval]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Coeval]]
    assert(scalazMonad != null)
    val scalazMonadError = implicitly[_root_.scalaz.MonadError[Coeval,Throwable]]
    assert(scalazMonadError != null)
    val scalazCobind = implicitly[_root_.scalaz.Cobind[Coeval]]
    assert(scalazCobind != null)
    val scalazComonad = implicitly[_root_.scalaz.Comonad[Coeval]]
    assert(scalazComonad != null)
  }

  test("MonadPlus instance is visible") {
    implicit val monixInstance: MonadPlus[Observable] = Observable.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Observable]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Observable]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Observable]]
    assert(scalazMonad != null)
    val scalazMonadError = implicitly[_root_.scalaz.MonadError[Observable,Throwable]]
    assert(scalazMonadError != null)
    val scalazCobind = implicitly[_root_.scalaz.Cobind[Observable]]
    assert(scalazCobind != null)
    val scalazPlusEmpty = implicitly[_root_.scalaz.PlusEmpty[Observable]]
    assert(scalazPlusEmpty != null)
    val scalazMonadPlus = implicitly[_root_.scalaz.MonadPlus[Observable]]
    assert(scalazMonadPlus != null)
  }

  test("Evaluable is convertible") {
    implicit val monixInstance: Evaluable[Task] = Task.typeClassInstances

    val scalazFunctor = implicitly[_root_.scalaz.Functor[Task]]
    assert(scalazFunctor != null)
    val scalazApplicative = implicitly[_root_.scalaz.Applicative[Task]]
    assert(scalazApplicative != null)
    val scalazMonad = implicitly[_root_.scalaz.Monad[Task]]
    assert(scalazMonad != null)
    val scalazMonadError = implicitly[_root_.scalaz.MonadError[Task,Throwable]]
    assert(scalazMonadError != null)
    val scalazCobind = implicitly[_root_.scalaz.Cobind[Task]]
    assert(scalazCobind != null)
  }
}
