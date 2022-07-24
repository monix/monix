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

package monix.execution
import monix.execution.BaseTestSuite.IgnoreException
import monix.execution.schedulers.TestScheduler
import munit.{ FunSuite, Location }

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

abstract class TestSuite[S] extends FunSuite with Checkers with ArbitraryInstances {
  final val fixture: FunFixture[S] = FunFixture[S](_ => setup(), tearDown)

  override def test(name: String)(testBody: => Any)(implicit loc: Location): Unit = {
    super.test(name)(
      try {
        testBody match {
          case x: Future[Any] => x.recover { case _: IgnoreException => () }(global)
          case x => x
        }
      } catch {
        case _: IgnoreException =>
      }
    )
  }

  def setup(): S
  def tearDown(env: S): Unit

  def ignore(msg: String = "")(implicit loc: Location): Unit = {
    assume(false, msg)
  }

  def fail()(implicit loc: Location): Unit = {
    super.fail("failed test")
  }
}

abstract class BaseTestSuite extends TestSuite[TestScheduler] {

  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }

}

object BaseTestSuite {
  case class IgnoreException(msg: String) extends RuntimeException(msg)

  def ignore(msg: String = ""): Unit = {
    throw IgnoreException(msg)
  }
}
