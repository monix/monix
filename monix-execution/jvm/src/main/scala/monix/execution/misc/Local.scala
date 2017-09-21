package monix.execution.misc

object Local {

  type Context = scala.collection.immutable.Map[Key, Option[LocalContext]]

  trait LocalContext

  class Key

  private[this] def context(k: Key, v: Option[LocalContext]): Context =
    scala.collection.immutable.Map(k -> v)

  private[this] val localContext = ThreadLocal[Context]()

  private def register(): Key =
    new Key

  /**
    * Return the state of the current Local state.
    */
  def getContext(): Context =
    localContext.get

  /**
    * Restore the Local state to a given Context.
    */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /**
    * Clear the Local state.
    */
  def clearContext(): Unit =
    localContext.set(null)

  /**
    * Execute a block of code using the specified state of Local
    * Context and restore the current state when complete
    */
  def withContext[T](ctx: Context)(f: => T): T = {
    val saved = getContext()
    setContext(ctx)
    try f
    finally setContext(saved)
  }

  /**
    * Execute a block of code with a clear state of Local
    * Context and restore the current state when complete
    */
  def withClearContext[T](f: => T): T = withContext(null)(f)

  private def save(key: Key, tctx: Option[LocalContext]): Unit = {
    val newCtx = Option(localContext.get) match {
      case Some(ctx) =>
        ctx + (key -> tctx)
      case None =>
        context(key, tctx)
    }
    localContext.set(newCtx)
  }

  private def get(key: Key): Option[_ <: LocalContext] = {
    Option(localContext.get).flatMap(_.get(key).flatten)
  }

  private def clear(key: Key): Unit =
    Option(localContext.get).foreach { m =>
      localContext.set(m - key)
    }

}

final class Local[T <: Local.LocalContext] {
  private[this] val key: Local.Key = Local.register()

  def update(value: T): Unit =
    set(Some(value))

  private def set(value: Option[T]): Unit =
    Local.save(key, value)

  def apply(): Option[T] =
    Local.get(key).asInstanceOf[Option[T]]

  def withContext[U](value: T)(f: => U): U = {
    val saved = apply()
    set(Some(value))
    try f
    finally set(saved)
  }

  def withClearContext[U](f: => U): U = {
    val saved = apply()
    clear()
    try f
    finally set(saved)
  }

  def clear(): Unit =
    Local.clear(key)
}
