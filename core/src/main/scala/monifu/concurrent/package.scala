package monifu

package object concurrent {
  def Runnable(cb: => Unit) = new Runnable {
    def run(): Unit = cb
  }
}
