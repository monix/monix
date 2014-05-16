package monifu.rx.subjects

import monifu.rx.{Observable, Observer}

trait Subject[T] extends Observable[T] with Observer[T]
