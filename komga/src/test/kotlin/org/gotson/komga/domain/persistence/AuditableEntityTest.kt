package org.gotson.komga.domain.persistence

import org.assertj.core.api.Assertions.assertThat
import org.gotson.komga.domain.model.makeBook
import org.gotson.komga.domain.model.makeLibrary
import org.gotson.komga.domain.model.makeSeries
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
class AuditableEntityTest(
  @Autowired private val seriesRepository: SeriesRepository,
  @Autowired private val libraryRepository: LibraryRepository
) {

  private var library = makeLibrary()

  @BeforeAll
  fun `setup library`() {
    library = libraryRepository.insert(library)
  }

  @AfterAll
  fun `teardown library`() {
    libraryRepository.deleteAll()
  }

  @AfterEach
  fun `clear repository`() {
    seriesRepository.deleteAll()
  }

  @Test
  fun `given series with book when saving then created and modified date is also saved`() {
    // given
    val series = makeSeries(name = "series", books = listOf(makeBook("book1"))).also { it.library = library }

    // when
    seriesRepository.save(series)

    // then
    assertThat(series.createdDate).isBefore(LocalDateTime.now())
    assertThat(series.lastModifiedDate).isBefore(LocalDateTime.now())
    assertThat(series.books.first().createdDate).isBefore(LocalDateTime.now())
    assertThat(series.books.first().lastModifiedDate).isBefore(LocalDateTime.now())
  }

  @Test
  fun `given existing series with book when updating series only then created date is kept and modified date is changed for series only`() {
    // given
    val series = makeSeries(name = "series", books = listOf(makeBook("book1"))).also { it.library = library }

    seriesRepository.save(series)

    val creationTimeApprox = LocalDateTime.now()

    Thread.sleep(1000)

    // when
    series.name = "seriesUpdated"
    seriesRepository.saveAndFlush(series)

    val modificationTimeApprox = LocalDateTime.now()

    // then
    assertThat(series.createdDate)
      .isBefore(creationTimeApprox)
      .isNotEqualTo(series.lastModifiedDate)
    assertThat(series.lastModifiedDate)
      .isAfter(creationTimeApprox)
      .isBefore(modificationTimeApprox)

    assertThat(series.books.first().createdDate)
      .isBefore(creationTimeApprox)
      .isEqualTo(series.books.first().lastModifiedDate)
  }

  @Test
  fun `given existing series with book when updating book only then created date is kept and modified date is changed for book only`() {
    // given
    val series = makeSeries(name = "series", books = listOf(makeBook("book1"))).also { it.library = library }

    seriesRepository.save(series)

    val creationTimeApprox = LocalDateTime.now()

    Thread.sleep(1000)

    // when
    series.books.first().name = "bookUpdated"
    seriesRepository.saveAndFlush(series)

    val modificationTimeApprox = LocalDateTime.now()

    // then
    assertThat(series.createdDate)
      .isBefore(creationTimeApprox)
      .isEqualTo(series.lastModifiedDate)

    assertThat(series.books.first().createdDate)
      .isBefore(creationTimeApprox)
      .isNotEqualTo(series.books.first().lastModifiedDate)
    assertThat(series.books.first().lastModifiedDate)
      .isAfter(creationTimeApprox)
      .isBefore(modificationTimeApprox)
  }

  @Test
  fun `given existing book with media when updating media only then created date is kept and modified date is changed for media only`() {
    // given
    val series = makeSeries(name = "series", books = listOf(makeBook("book1"))).also { it.library = library }

    seriesRepository.save(series)

    val creationTimeApprox = LocalDateTime.now()

    Thread.sleep(1000)

    // when
    series.books.first().media.comment = "mediaUpdated"
    seriesRepository.saveAndFlush(series)

    val modificationTimeApprox = LocalDateTime.now()

    // then
    assertThat(series.createdDate)
      .isBefore(creationTimeApprox)
      .isEqualTo(series.lastModifiedDate)

    assertThat(series.books.first().createdDate)
      .isBefore(creationTimeApprox)
      .isEqualTo(series.books.first().lastModifiedDate)

    assertThat(series.books.first().media.createdDate)
      .isBefore(creationTimeApprox)
      .isNotEqualTo(series.books.first().media.lastModifiedDate)

    assertThat(series.books.first().media.lastModifiedDate)
      .isAfter(creationTimeApprox)
      .isBefore(modificationTimeApprox)
  }
}
