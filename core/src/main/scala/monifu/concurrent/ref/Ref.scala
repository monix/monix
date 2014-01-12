package monifu.concurrent.ref

import util.control.NonFatal
import monifu.concurrent.locks.NaiveReadWriteLock
import Ref._


@specialized final class Ref[T] private (initialValue: T)(implicit ctx: Context) {
  def apply(): T = 
    ctx.readLock(_tmpValue getOrElse _currentValue)

  def get: T = apply()

  def old: Option[T] = 
    ctx.readLock(_oldValue)

  def update(value: T)(implicit ev: WritePermission): Unit = {
    ctx.registerWrite(this, ev)
    _tmpValue = Some(value)
  }

  def `:=`(value: T)(implicit ev: WritePermission): Unit =
    update(value)

  private[ref] def commit(ev: WritePermission): Unit = 
    for (tmp <- _tmpValue) {
      ctx.checkPermission(ev)
      _oldValue = Some(_currentValue)
      _currentValue = tmp
      _tmpValue = None
    }

  private[ref] def rollback(ev: WritePermission): Unit = 
    if (_tmpValue != None) {
      ctx.checkPermission(ev)
      _tmpValue = None
    }

  private[this] var _currentValue: T = initialValue;
  private[this] var _tmpValue = Option.empty[T]
  private[this] var _oldValue = Option.empty[T]
}

object Ref {

  def apply[T](initialValue: T)(implicit ctx: Context): Ref[T] =
    new Ref(initialValue)

  sealed trait WritePermission {
    private[ref] def isValid(ctx: Context): Boolean
    private[ref] def invalidate()
  }

  sealed trait Context {
    def writes[T](cb: WritePermission => T): T

    private[ref] def readLock[T](cb: => T): T
    private[ref] def registerWrite(ref: Ref[_], ev: WritePermission): Unit
    private[ref] def checkPermission(ev: WritePermission): Unit
  }

  private[ref] object WritePermission {
    def apply(ctx: Context): WritePermission = new WritePermission {
      private[this] val _owner = ctx
      private[this] val _localCheck = {
        val bool = new ThreadLocal[Boolean] { override def initialValue = false }
        bool.set(true)
        bool
      }

      def isValid(ctx: Context) = 
        ctx == _owner && _localCheck.get

      def invalidate(): Unit = 
        _localCheck.set(false)
    }
  }

  object Context {
    def apply(): Context = new Context {
      def readLock[T](cb: => T): T = 
        lock.readLock(cb)

      def writes[T](cb: WritePermission => T): T = 
        lock.writeLock {
          val ev = WritePermission(this)
          try { 
            val ret = cb(ev)
            commit(ev)
            ret
          }
          catch {
            case NonFatal(ex) =>
              rollback(ev)
              throw ex
          }
          finally { 
            ev.invalidate() 
          }
        }

      def registerWrite(ref: Ref[_], ev: WritePermission): Unit = {
        checkPermission(ev)
        registeredRefs = registeredRefs + ref
      }

      def checkPermission(ev: WritePermission): Unit = {
        if (!ev.isValid(this)) throw new IllegalStateException(
          "Cannot write in this variable, the WritePermission has escaped its context")
      }

      private[this] def commit(ev: WritePermission): Unit =
        for (ref <- registeredRefs) {
          ref.commit(ev)
        }

      private[this] def rollback(ev: WritePermission): Unit =
        for (ref <- registeredRefs) {
          ref.rollback(ev)
        }

      private[this] var registeredRefs = Set.empty[Ref[_]] 
      private[this] val lock = new NaiveReadWriteLock()
    }
  }
}