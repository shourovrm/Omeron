package com.omeron.data.remote.api.reddit

import com.omeron.data.model.Sort
import com.omeron.data.model.TimeSorting
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class SortingConverterFactory : Converter.Factory() {

    override fun stringConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, String>? {
        return when (type) {
            Sort::class.java -> {
                Converter<Sort, String> { it.type }
            }
            TimeSorting::class.java -> {
                Converter<TimeSorting, String> { it.type }
            }
            else -> {
                super.stringConverter(type, annotations, retrofit)
            }
        }
    }
}
