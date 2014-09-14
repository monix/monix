package monifu.concurrent

object Implicits {
  implicit lazy val scheduler: Scheduler = Scheduler.global
}
