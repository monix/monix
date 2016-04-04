package monix.cats

import monix.cats.tests.SequenceableTests
import monix.reactive.Observable

object ObservableLawsSuite extends BaseLawsSuite {
  checkAll("Sequenceable[Observable]", SequenceableTests[Observable].sequenceable[Int,Int,Int])
}