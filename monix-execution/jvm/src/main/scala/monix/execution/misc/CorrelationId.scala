package monix.execution.misc

import monix.execution.misc.LocalContext.TracingContext

case class CorrelationId(id: String) extends TracingContext {

  def asCurrent[T](f: => T): T = CorrelationId.let(Some(this))(f)
}


object CorrelationId {

  val local: LocalContext[CorrelationId] = new LocalContext[CorrelationId]

  def current: Option[CorrelationId] =
    local()//Contexts.broadcast.getOrElse(clientIdCtx, NoClientFn)

  /**
    * See [[CorrelationId.asCurrent]]
    */
  private[monix] def let[R](correlationId: Option[CorrelationId])(f: => R): R = {
    correlationId match {
      case Some(cid) => local.withContext(cid)(f)
      case None => local.withClearContext(f)
    }
  }
}