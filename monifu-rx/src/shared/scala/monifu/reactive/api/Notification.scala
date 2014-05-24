package monifu.reactive.api

/**
 * Used by [[monifu.reactive.Observable.materialize Observable.materialize]].
 */
trait Notification[+T]

object Notification {
  case class OnNext[+T](elem: T) extends Notification[T]

  case class OnError(ex: Throwable) extends Notification[Nothing]

  case object OnComplete extends Notification[Nothing]
}
