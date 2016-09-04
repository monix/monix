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

import monix.execution.internal.Platform

object StreamFromSeqSuite extends BaseTestSuite {
  test("TaskStream.fromSeq") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromSeq(list.toVector).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.fromSeq (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = TaskStream.fromSeq(list.toVector).mapEval(x => Task(x)).toListL
      result === Task.now(list)
    }
  }

  test("TaskStream.fromSeq (long)") { implicit s =>
    val range = 0 until (Platform.recommendedBatchSize * 3)
    val result = TaskStream.fromSeq(range.toVector).toListL
    check(result === Task.now(range.toList))
  }

  test("CoevalStream.fromSeq") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromSeq(list.toVector).toListL
      result === Coeval.now(list)
    }
  }

  test("CoevalStream.fromSeq (async)") { implicit s =>
    check1 { (list: List[Int]) =>
      val result = CoevalStream.fromSeq(list.toVector).mapEval(x => Coeval(x)).toListL
      result === Coeval.now(list)
    }
  }

  test("Coeval.fromSeq (long)") { implicit s =>
    val range = 0 until (Platform.recommendedBatchSize * 3)
    val result = CoevalStream.fromSeq(range.toVector).toListL
    check(result === Coeval.now(range.toList))
  }
}
