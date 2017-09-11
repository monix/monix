package monix.execution.tracing

import monix.execution.misc.{TracingContext, TracingContextCompanion}

/**
  * A tracing context that allows to propagate a correlation id
  * through an execution.
  * @param id the correlation id.
  */
case class CorrelationId(id: String) extends TracingContext {

  def asCurrent[T](f: => T): T = CorrelationId.let(Some(this))(f)
}


object CorrelationId extends TracingContextCompanion[CorrelationId]
