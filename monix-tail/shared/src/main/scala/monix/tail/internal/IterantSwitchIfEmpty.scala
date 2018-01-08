package monix.tail.internal

import cats.syntax.functor._
import cats.effect.Sync
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantSwitchIfEmpty {
  def apply[F[_], A](primary: Iterant[F, A], backup: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {
    def loop(source: Iterant[F, A]): Iterant[F, A] =
      source match {
        case Suspend(rest, stop) => Suspend(rest.map(loop), stop)
        case NextBatch(batch, rest, stop) if batch.cursor().isEmpty =>
          Suspend(rest.map(loop), stop)
        case NextCursor(cursor, rest, stop) if cursor.isEmpty =>
          Suspend(rest.map(loop), stop)

        case Halt(None) =>
          Suspend(primary.earlyStop.as(backup), F.unit)

        case _ =>
          Suspend(backup.earlyStop.as(source), F.unit)
      }

    loop(primary)
  }
}
