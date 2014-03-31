package monifu.rx

sealed trait FoldState[+T] {
  def value: T
}

object FoldState {
  final case class Cont[+T](value: T) extends FoldState[T]
  final case class Emit[+T](value: T) extends FoldState[T]
}