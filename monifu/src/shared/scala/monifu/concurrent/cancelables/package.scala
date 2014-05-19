package monifu.concurrent

/**
 * Cancelables represent asynchronous units of work or other things scheduled for
 * execution and whose execution can be canceled.
 *
 * One use-case is the scheduling done by [[monifu.concurrent.Scheduler]], in which
 * the scheduling methods return a `Cancelable`, allowing the canceling of the
 * scheduling.
 *
 * Example:
 * {{{
 *   val s = ConcurrentScheduler()
 *   val task = s.scheduleRepeated(10.seconds, 50.seconds, {
 *     println("Hello")
 *   })
 *
 *   // later, cancels the scheduling ...
 *   task.cancel()
 * }}}
 */
package object cancelables {}
