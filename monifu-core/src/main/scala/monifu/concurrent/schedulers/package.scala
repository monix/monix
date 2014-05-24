package monifu.concurrent

import java.util.concurrent.{ThreadFactory, Executors}

package object schedulers {
  private[concurrent] lazy val defaultScheduledExecutor =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val th = new Thread(r)
        th.setDaemon(true)
        th.setName("monifu-scheduler")
        th
      }
    })
}
