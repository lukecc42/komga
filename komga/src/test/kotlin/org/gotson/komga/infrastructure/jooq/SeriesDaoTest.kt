package org.gotson.komga.infrastructure.jooq

import org.assertj.core.api.Assertions.assertThat
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.makeLibrary
import org.gotson.komga.domain.persistence.LibraryRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.net.URL
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@SpringBootTest
@AutoConfigureTestDatabase
class SeriesDaoTest(
  @Autowired private val seriesDao: SeriesDao,
  @Autowired private val libraryRepository: LibraryRepository
) {

  private var library = makeLibrary()

  @BeforeAll
  fun setup() {
    library = libraryRepository.insert(library)
  }

  @AfterEach
  fun deleteSeries() {
    seriesDao.deleteAll()
    assertThat(seriesDao.count()).isEqualTo(0)
  }

  @AfterAll
  fun tearDown() {
    libraryRepository.deleteAll()
  }


  @Test
  fun `given a series when inserting then it is persisted`() {
    val now = LocalDateTime.now()
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = now
    ).also { it.libraryId = library.id }

    Thread.sleep(5)

    val created = seriesDao.insert(series)

    assertThat(created.id).isNotEqualTo(0)
    assertThat(created.createdDate).isAfter(now)
    assertThat(created.lastModifiedDate).isAfter(now)
    assertThat(created.name).isEqualTo(series.name)
    assertThat(created.url).isEqualTo(series.url)
    assertThat(created.fileLastModified).isEqualTo(series.fileLastModified)
  }

  @Test
  fun `given a series when deleting then it is deleted`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }

    val created = seriesDao.insert(series)
    assertThat(seriesDao.count()).isEqualTo(1)

    seriesDao.delete(created.id)

    assertThat(seriesDao.count()).isEqualTo(0)
  }

  @Test
  fun `given series when deleting all then all are deleted`() {
    val now = LocalDateTime.now()
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = now
    ).also { it.libraryId = library.id }

    val series2 = Series(
      name = "Series2",
      url = URL("file://series2"),
      fileLastModified = now
    ).also { it.libraryId = library.id }

    seriesDao.insert(series)
    seriesDao.insert(series2)
    assertThat(seriesDao.count()).isEqualTo(2)

    seriesDao.deleteAll()

    assertThat(seriesDao.count()).isEqualTo(0)
  }

  @Test
  fun `given series when finding all then all are returned`() {
    val now = LocalDateTime.now()
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = now
    ).also { it.libraryId = library.id }

    val series2 = Series(
      name = "Series2",
      url = URL("file://series2"),
      fileLastModified = now
    ).also { it.libraryId = library.id }

    seriesDao.insert(series)
    seriesDao.insert(series2)

    val all = seriesDao.findAll()

    assertThat(all).hasSize(2)
    assertThat(all.map { it.name }).containsExactlyInAnyOrder("Series", "Series2")
  }

  @Test
  fun `given existing series when finding by id then series is returned`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }

    val created = seriesDao.insert(series)

    val found = seriesDao.findByIdOrNull(created.id)

    assertThat(found).isNotNull
    assertThat(found?.name).isEqualTo("Series")
  }

  @Test
  fun `given non-existing series when finding by id then null is returned`() {
    val found = seriesDao.findByIdOrNull(1287746)

    assertThat(found).isNull()
  }

  @Test
  fun `given existing series when finding by libraryId then series are returned`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }
    seriesDao.insert(series)

    val found = seriesDao.findByLibraryId(library.id)

    assertThat(found).hasSize(1)
    assertThat(found.first().name).isEqualTo("Series")
  }

  @Test
  fun `given existing series when finding by other libraryId then empty list is returned`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }
    seriesDao.insert(series)

    val found = seriesDao.findByLibraryId(library.id + 1)

    assertThat(found).hasSize(0)
  }

  @Test
  fun `given existing series when finding by libraryId and Url not in list then results are returned`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }
    seriesDao.insert(series)

    val found = seriesDao.findByLibraryIdAndUrlNotIn(library.id, listOf(URL("file://series2")))
    val notFound = seriesDao.findByLibraryIdAndUrlNotIn(library.id, listOf(URL("file://series")))

    assertThat(found).hasSize(1)
    assertThat(found.first().name).isEqualTo("Series")

    assertThat(notFound).hasSize(0)
  }

  @Test
  fun `given existing series when finding by libraryId and Url in list then results are returned`() {
    val series = Series(
      name = "Series",
      url = URL("file://series"),
      fileLastModified = LocalDateTime.now()
    ).also { it.libraryId = library.id }
    seriesDao.insert(series)

    val found = seriesDao.findByLibraryIdAndUrl(library.id, URL("file://series"))
    val notFound1 = seriesDao.findByLibraryIdAndUrl(library.id, URL("file://series2"))
    val notFound2 = seriesDao.findByLibraryIdAndUrl(library.id + 1, URL("file://series"))
    val notFound3 = seriesDao.findByLibraryIdAndUrl(library.id + 1, URL("file://series2"))

    assertThat(found).isNotNull
    assertThat(found?.name).isEqualTo("Series")

    assertThat(notFound1).isNull()
    assertThat(notFound2).isNull()
    assertThat(notFound3).isNull()
  }
}
