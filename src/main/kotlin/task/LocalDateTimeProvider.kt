package task

import java.time.LocalDateTime
import java.time.ZoneOffset


interface LocalDateTimeProvider {
    companion object {
        val CurrentUTC = object : LocalDateTimeProvider {
            override fun getCurrentLocalDateTime(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
        }
    }

    fun getCurrentLocalDateTime(): LocalDateTime
}
