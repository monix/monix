package monifu.rx.sync.subjects

import monifu.rx.sync.{Observer, Observable}

trait Subject[T] extends Observable[T] with Observer[T]
