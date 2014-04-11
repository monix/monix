package monifu.rx.sync.observers

/**
 * Exception thrown using an anonymous Observer without
 * a supplied `onError` handler.
 */
final class OnErrorRuntimeException(msg: String, ex: Throwable)
  extends RuntimeException(msg, ex) {

  def this(ex: Throwable) = this(ex.getMessage, ex)
}