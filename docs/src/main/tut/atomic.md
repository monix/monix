---
layout: page
title: Atomic References
permalink: /tut/atomic.html
---

Sample:

```tut:book
import monix.base.atomic.Atomic

val atomic = Atomic(1)
atomic.transformAndGet(_+1)
```