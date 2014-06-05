package monifu.reactive.api

sealed trait BufferPolicy

object BufferPolicy {
  case object Unbounded extends BufferPolicy
  case class OverflowTriggered(bufferSize: Int) extends BufferPolicy
  case class BackPressured(bufferSize: Int) extends BufferPolicy
}
