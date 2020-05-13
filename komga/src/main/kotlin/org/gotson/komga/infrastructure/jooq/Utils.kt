package org.gotson.komga.infrastructure.jooq

import org.jooq.Field
import org.jooq.SortField
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

fun LocalDateTime.toUTC(): LocalDateTime =
  atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

fun Pageable.toOrderBy(sorts: Map<String, Field<out Any>>): List<SortField<out Any>> =
  sort.mapNotNull {
    val f = sorts[it.property]
    if (it.isAscending) f?.asc() else f?.desc()
  }
