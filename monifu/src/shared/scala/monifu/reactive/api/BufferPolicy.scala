package monifu.reactive.api

sealed trait BufferPolicy

object BufferPolicy {
  case object Unbounded extends BufferPolicy
  case class TriggerOverflow(bufferSize: Int) extends BufferPolicy
}
