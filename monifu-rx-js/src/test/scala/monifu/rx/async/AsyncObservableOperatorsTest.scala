package monifu.rx.async

import monifu.concurrent.Scheduler.Implicits.global

object AsyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[Observable](Observable.Builder)