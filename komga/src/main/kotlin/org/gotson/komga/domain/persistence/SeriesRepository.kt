package org.gotson.komga.domain.persistence

import org.gotson.komga.domain.model.Series
import org.hibernate.annotations.QueryHints.CACHEABLE
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository
import java.net.URL
import javax.persistence.QueryHint

@Repository
interface SeriesRepository : JpaRepository<Series, Long>, JpaSpecificationExecutor<Series> {
  @QueryHints(QueryHint(name = CACHEABLE, value = "true"))
  override fun findAll(pageable: Pageable): Page<Series>

  @QueryHints(QueryHint(name = CACHEABLE, value = "true"))
  fun findByLibraryIdIn(libraries: Collection<Long>, sort: Sort): List<Series>

  @QueryHints(QueryHint(name = CACHEABLE, value = "true"))
  fun findByLibraryId(libraryId: Long, sort: Sort): List<Series>

  fun findByLibraryIdAndUrlNotIn(libraryId: Long, urls: Collection<URL>): List<Series>
  fun findByLibraryIdAndUrl(libraryId: Long, url: URL): Series?
  fun deleteByLibraryId(libraryId: Long)

}
