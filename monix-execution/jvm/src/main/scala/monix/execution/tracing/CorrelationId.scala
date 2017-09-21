package monix.execution.tracing

import monix.execution.misc.{TracingContext, TracingContextCompanion}

/**
  * A [[CorrelationId]] is a [[monix.execution.misc.TracingContext TracingContext]] that allows
  * to propagate a correlation id through an execution.
  *
  * {{{
  *   import monix.execution.Scheduler.Implicits.traced
  *
  *   def getId: Task[Option[String]] = Task.pure(CorrelationId.current.map(_.id))
  *
  *   // Should yield Some(123456789)
  *   val res = CorrelationId("123456789").asCurrent {
  *     getId.runAsync
  *   }
  * }}}
  * @param id the correlation id.
  */
final case class CorrelationId(id: String) extends TracingContext {

  def asCurrent[T](f: => T): T = CorrelationId.let(Some(this))(f)
}


object CorrelationId extends TracingContextCompanion[CorrelationId]
