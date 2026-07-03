package com.omeron.util.extension

val Long.isPast: Boolean
    get() = System.currentTimeMillis() >= this
