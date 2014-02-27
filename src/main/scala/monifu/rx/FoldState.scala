package monifu.rx

sealed trait FoldState[+T] {
  def value: T
}

object FoldState {
  case class Cont[+T](value: T) extends FoldState[T]
  case class Emit[+T](value: T) extends FoldState[T]
}