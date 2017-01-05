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

package monix.eval

import monix.types.tests._

object TypeClassLawsForNondetTaskSuite extends BaseLawsSuite
  with MemoizableLawsSuite[Task,Int,Long,Short]
  with MonadErrorLawsSuite[Task,Int,Long,Short,Throwable]
  with CobindLawsSuite[Task,Int,Long,Short]
  with MonadRecLawsSuite[Task,Int,Long,Short] {

  override def F: Task.TypeClassInstances =
    Task.nondeterminism

  applicativeEvalErrorCheck("Task.nondeterminism")
  memoizableCheck("Task.nondeterminism", includeSupertypes = true)
  monadErrorCheck("Task.nondeterminism", includeSupertypes = false)
  monadRecCheck("Task.nondeterminism", includeSupertypes = false)
  cobindCheck("Task.nondeterminism", includeSupertypes = false)
}
