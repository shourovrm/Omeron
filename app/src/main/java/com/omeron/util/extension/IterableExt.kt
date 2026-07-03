package com.omeron.util.extension

fun <T> Iterable<Iterable<T>>.interlace(): List<T> {
    val result = ArrayList<T>()

    // maxOf throws on an empty receiver (e.g. an empty multireddit -> no source lists).
    val max = this.maxOfOrNull { it.count() } ?: return result

    for (i in 0..max) {
        this
            .mapNotNull { it.elementAtOrNull(i) }
            .let { result.addAll(it) }
    }

    return result
}
