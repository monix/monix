package monifu.concurrent.misc

final class OneTimeAction[+R] private (cb: => R) extends (() => R) {
  private[this] lazy val action = cb
  def apply() = action
}

object OneTimeAction {
  def apply[R](cb: => R): OneTimeAction[R] =
    new OneTimeAction[R](cb)
}
