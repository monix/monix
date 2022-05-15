/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import scala.concurrent.Future
import scala.concurrent.duration._

import minitest.SimpleTestSuite
import monix.execution.{ BufferCapacity, Scheduler }
import monix.execution.exceptions.DummyException
import monix.execution.misc.Local
import cats.implicits._
import monix.catnap.{ ConcurrentChannel, ConsumerF }

object TaskLocalSuite extends SimpleTestSuite {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global
  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation

  testAsync("TaskLocal.apply") {
    val test =
      for {
        local <- TaskLocal(0)
        v1    <- local.read
        _     <- Task.now(assertEquals(v1, 0))
        _     <- local.write(100)
        _     <- Task.shift
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
        _     <- local.clear
        _     <- Task.shift
        v3    <- local.read
        _     <- Task.now(assertEquals(v3, 0))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal.wrap") {
    val local = Local(0)
    val test =
      for {
        local <- TaskLocal.wrap(Task(local))
        v1    <- local.read
        _     <- Task.now(assertEquals(v1, 0))
        _     <- local.write(100)
        _     <- Task.shift
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
        _     <- local.clear
        _     <- Task.shift
        v3    <- local.read
        _     <- Task.now(assertEquals(v3, 0))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal.defaultLazy") {
    var i = 0

    val test =
      for {
        local <- TaskLocal.lazyDefault(Coeval { i += 1; i })
        v1    <- local.read
        _     <- Task.now(assertEquals(v1, 1))
        _     <- local.write(100)
        _     <- Task.shift
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
        _     <- local.clear
        _     <- Task.shift
        v3    <- local.read
        _     <- Task.now(assertEquals(v3, 2))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal!.bind") {
    val test =
      for {
        local <- TaskLocal(0)
        _     <- local.write(100)
        _     <- Task.shift
        v1    <- local.bind(200)(local.read.map(_ * 2))
        _     <- Task.now(assertEquals(v1, 400))
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal!.bindL") {
    val test =
      for {
        local <- TaskLocal(0)
        _     <- local.write(100)
        _     <- Task.shift
        v1    <- local.bindL(Task.eval(200))(local.read.map(_ * 2))
        _     <- Task.now(assertEquals(v1, 400))
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal!.bindClear") {
    val test =
      for {
        local <- TaskLocal(200)
        _     <- local.write(100)
        _     <- Task.shift
        v1    <- local.bindClear(local.read.map(_ * 2))
        _     <- Task.now(assertEquals(v1, 400))
        v2    <- local.read
        _     <- Task.now(assertEquals(v2, 100))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal canceled") {
    import scala.concurrent.duration._

    val test: Task[Unit] = for {
      local  <- TaskLocal[String]("Good")
      forked <- Task.sleep(1.second).start
      _      <- local.bind("Bad!")(forked.cancel).start
      _      <- Task.sleep(1.second)
      s      <- local.read
      _      <- Task.now(assertEquals(s, "Good"))
    } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocal!.local") {
    val test =
      for {
        taskLocal <- TaskLocal(200)
        local     <- taskLocal.local
        v1        <- taskLocal.read
        _         <- Task.now(assertEquals(local.get, v1))
        _         <- taskLocal.write(100)
        _         <- Task.now(assertEquals(local.get, 100))
        _         <- Task.now(local.update(200))
        v2        <- taskLocal.read
        _         <- Task.now(assertEquals(v2, 200))
        _         <- Task.shift
        v3        <- taskLocal.bindClear(Task.now(local.get * 2))
        _         <- Task.now(assertEquals(v3, 400))
        v4        <- taskLocal.read
        _         <- Task.now(assertEquals(v4, local.get))
      } yield ()

    test.runToFutureOpt
  }

  testAsync("TaskLocals get restored in Task.create on error") {
    val dummy = DummyException("dummy")
    val task = Task.create[Int] { (_, cb) =>
      ec.execute(() => cb.onError(dummy))
    }

    val t = for {
      local <- TaskLocal(0)
      _     <- local.write(10)
      i     <- task.onErrorRecover { case `dummy` => 10 }
      l     <- local.read
      _     <- Task.eval(assertEquals(i, 10))
      _     <- Task.eval(assertEquals(l, 10))
    } yield ()

    t.runToFutureOpt
  }

  testAsync("TaskLocal should work with bracket") {
    val t = for {
      local <- TaskLocal(0)
      _ <- Task.unit.executeAsync
        .guarantee(local.write(10))
      value <- local.read
      _     <- Task.eval(assertEquals(value, 10))
    } yield ()

    t.runToFutureOpt
  }

  testAsync("TaskLocal.isolate should prevent context changes") {
    val t = for {
      local <- TaskLocal(0)
      inc = local.read.map(_ + 1).flatMap(local.write)
      _    <- inc
      res1 <- local.read
      _    <- Task(assertEquals(res1, 1))
      _    <- TaskLocal.isolate(inc)
      res2 <- local.read
      _    <- Task(assertEquals(res1, res2))
    } yield ()

    t.runToFutureOpt
  }

  testAsync("TaskLocal interop with future via deferFutureAction") {
    val t = for {
      local  <- TaskLocal(0)
      unsafe <- local.local
      _      <- local.write(1)
      _ <- Task.deferFutureAction { implicit ec =>
        Future {
          assertEquals(unsafe.get, 1)
        }.map { _ =>
          unsafe := 50
        }
      }
      x <- local.read
      _ <- Task(assertEquals(x, 50))
    } yield ()

    t.runToFutureOpt
  }

  testAsync("TaskLocal.bind scoping works") {
    val tl = TaskLocal(999)
    val t = Task
      .map3(tl, tl, tl) { (l1, l2, l3) =>
        def setAll(x: Int) = Task.traverse(List(l1, l2, l3))(_.write(x))
        l1.bind(0) {
          setAll(0) >> l2.bind(1) {
            setAll(1)
          }
        } >> List(l1, l2, l3).traverse(_.read).map(assertEquals(_, List(999, 0, 1)))
      }
      .flatten
      .map(_ => ())
    t.runToFutureOpt
  }

  testAsync("TaskLocal.bind actually isolates reads") {
    val t = for {
      l1 <- TaskLocal(0)
      l2 <- TaskLocal(0)
      f  <- l1.bind(0)(Task.sleep(1.second) *> (l1.read, l2.read).tupled).start
      _  <- l1.write(5)
      _  <- l2.write(5)
      r  <- f.join
      _ = assertEquals(r._1, 0)
      _ = assertEquals(r._2, 5)
    } yield ()

    t.runToFutureOpt
  }

  testAsync("TaskLocal.isolate with ConcurrentChannel") {
    val bufferSize = 16

    class Test(
      l: TaskLocal[String],
      ch: ConcurrentChannel[Task, Unit, Int]
    ) {
      private[this] def produceLoop(n: Int): Task[Unit] =
        if (n == 0) Task.unit
        else
          ch.push(n) >> l.read.flatMap { s =>
            Task(assertEquals(s, "producer"))
          } >> produceLoop(n - 1)

      def produce: Task[Unit] =
        for {
          _ <- ch.awaitConsumers(1)
          _ <- l.write("producer")
          _ <- produceLoop(bufferSize * 2)
          _ <- ch.halt(())
        } yield ()

      def consume: Task[Unit] = ch.consume.use { c =>
        c.pull
          .delayResult(1.milli)
          .flatMap { x =>
            l.write(x.toString).as(x)
          }
          .iterateWhile(_.isRight)
          .void
      }
    }

    val t = for {
      tl <- TaskLocal("undefined")
      ch <- ConcurrentChannel[Task].withConfig[Unit, Int](
        ConsumerF.Config(
          capacity = BufferCapacity.Bounded(bufferSize).some
        )
      )
      test = new Test(tl, ch)
      _ <- TaskLocal.isolate(test.produce) &> TaskLocal.isolate(test.consume)
    } yield ()

    t.runToFutureOpt
  }
}
