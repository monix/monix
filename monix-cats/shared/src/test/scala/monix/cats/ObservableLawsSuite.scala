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

package monix.cats

import cats.laws.discipline.{CoflatMapTests, MonadCombineTests, MonadErrorTests}
import monix.reactive.Observable
import org.scalacheck.Test.Parameters

object ObservableLawsSuite extends BaseLawsSuite {
  // https://github.com/typelevel/cats/issues/1329
  override lazy val checkConfig: Parameters =
    slowCheckConfig

  checkAll("MonadError[Observable[Int]]", MonadErrorTests[Observable, Throwable].flatMap[Int,Int,Int])
  checkAll("CoflatMap[Observable[Int]]", CoflatMapTests[Observable].coflatMap[Int,Int,Int])
  checkAll("MonadCombine[Observable[Int]]", MonadCombineTests[Observable].monadCombine[Int,Int,Int])
}
