package monifu.concurrent

object Implicits {
  /**
   * Default scheduler. Only initialized when used.
   */
  implicit lazy val defaultScheduler =
    Scheduler("monifu-default-scheduler")
}
