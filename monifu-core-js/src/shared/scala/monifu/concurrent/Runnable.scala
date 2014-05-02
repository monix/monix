package monifu.concurrent

object Runnable {
  def apply(cb: => Unit) = new Runnable {
    def run(): Unit = cb
  }
}
