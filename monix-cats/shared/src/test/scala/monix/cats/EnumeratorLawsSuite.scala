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
import monix.eval.{CoevalStream, TaskStream}

object EnumeratorLawsSuite extends BaseLawsSuite {
  checkAll("MonadError[TaskStream[Int]]", MonadErrorTests[TaskStream, Throwable].monadError[Int,Int,Int])
  checkAll("CoflatMap[TaskStream[Int]]", CoflatMapTests[TaskStream].coflatMap[Int,Int,Int])
  checkAll("MonadCombine[TaskStream[Int]]", MonadCombineTests[TaskStream].monadCombine[Int,Int,Int])

  checkAll("MonadError[CoevalStream[Int]]", MonadErrorTests[CoevalStream, Throwable].monadError[Int,Int,Int])
  checkAll("CoflatMap[CoevalStream[Int]]", CoflatMapTests[CoevalStream].coflatMap[Int,Int,Int])
  checkAll("MonadCombine[CoevalStream[Int]]", MonadCombineTests[CoevalStream].monadCombine[Int,Int,Int])
}

