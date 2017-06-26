package monix.eval.internal

import cats.effect.IO
import monix.eval.Task
import monix.execution.Scheduler
import scala.util.{Failure, Success}

private[monix] object TaskIOConversions {
  /** Implementation for `Task#toIO`. */
  def taskToIO[A](source: Task[A])(implicit s: Scheduler): IO[A] =
    source match {
      case Task.Now(v) => IO.pure(v)
      case Task.Error(e) => IO.raiseError(e)
      case Task.Eval(thunk) => IO(thunk())
      case _ => IO.suspend {
        val f = source.runAsync(s)
        f.value match {
          case Some(tryA) => tryA match {
            case Success(v) => IO.pure(v)
            case Failure(e) => IO.raiseError(e)
          }
          case None => IO.async { cb =>
            f.onComplete {
              case Success(v) => cb(Right(v))
              case Failure(e) => cb(Left(e))
            }
          }
        }
      }
    }

  /** Implementation for `Task#fromIO`. */
  def taskFromIO[A](io: IO[A]): Task[A] =
    io.to[Task]
}
