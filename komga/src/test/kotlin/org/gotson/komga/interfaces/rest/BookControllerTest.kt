package org.gotson.komga.interfaces.rest

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.makeBook
import org.gotson.komga.domain.model.makeLibrary
import org.gotson.komga.domain.model.makeSeries
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.domain.service.SeriesLifecycle
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcResultMatchersDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.random.Random

@ExtendWith(SpringExtension::class)
@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class BookControllerTest(
  @Autowired private val seriesRepository: SeriesRepository,
  @Autowired private val seriesLifecycle: SeriesLifecycle,
  @Autowired private val libraryRepository: LibraryRepository,
  @Autowired private val bookRepository: BookRepository,
  @Autowired private val mockMvc: MockMvc
) {

  lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  fun setDataSource(dataSource: DataSource) {
    this.jdbcTemplate = JdbcTemplate(dataSource)
  }

  private var library = makeLibrary()

  @BeforeAll
  fun `setup library`() {
    jdbcTemplate.execute("ALTER SEQUENCE hibernate_sequence RESTART WITH 1")

    library = libraryRepository.insert(library)
  }

  @AfterAll
  fun `teardown library`() {
    libraryRepository.deleteAll()
  }

  @AfterEach
  fun `clear repository`() {
    bookRepository.deleteAll()
    seriesRepository.deleteAll()
  }

  @Nested
  inner class LimitedUser {
    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [1])
    fun `given user with access to a single library when getting books then only gets books from this library`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val otherLibrary = libraryRepository.insert(makeLibrary("other"))
      val otherSeries = makeSeries(name = "otherSeries").also { it.libraryId = otherLibrary.id }
      val otherBooks = listOf(makeBook("2")).also { list -> list.forEach { it.libraryId = otherLibrary.id } }
      seriesLifecycle.createSeries(otherSeries, otherBooks)

      mockMvc.get("/api/v1/books")
        .andExpect {
          status { isOk }
          jsonPath("$.content.length()") { value(1) }
          jsonPath("$.content[0].name") { value("1") }
        }

    }
  }

  @Nested
  inner class UserWithoutLibraryAccess {
    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [])
    fun `given user with no access to any library when getting specific book then returns unauthorized`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}")
        .andExpect { status { isUnauthorized } }
    }

    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [])
    fun `given user with no access to any library when getting specific book thumbnail then returns unauthorized`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/thumbnail")
        .andExpect { status { isUnauthorized } }
    }

    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [])
    fun `given user with no access to any library when getting specific book file then returns unauthorized`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/file")
        .andExpect { status { isUnauthorized } }
    }

    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [])
    fun `given user with no access to any library when getting specific book pages then returns unauthorized`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/pages")
        .andExpect { status { isUnauthorized } }
    }

    @Test
    @WithMockCustomUser(sharedAllLibraries = false, sharedLibraries = [])
    fun `given user with no access to any library when getting specific book page then returns unauthorized`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/pages/1")
        .andExpect { status { isUnauthorized } }
    }
  }

  @Nested
  inner class MediaNotReady {
    @Test
    @WithMockCustomUser
    fun `given book without thumbnail when getting book thumbnail then returns not found`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/thumbnail")
        .andExpect { status { isNotFound } }
    }

    @Test
    @WithMockCustomUser
    fun `given book without file when getting book file then returns not found`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/file")
        .andExpect { status { isNotFound } }
    }

    @ParameterizedTest
    @EnumSource(value = Media.Status::class, names = ["READY"], mode = EnumSource.Mode.EXCLUDE)
    @WithMockCustomUser
    fun `given book with media status not ready when getting book pages then returns not found`(status: Media.Status) {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1").also { it.media.status = status }).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/pages")
        .andExpect { status { isNotFound } }
    }

    @ParameterizedTest
    @EnumSource(value = Media.Status::class, names = ["READY"], mode = EnumSource.Mode.EXCLUDE)
    @WithMockCustomUser
    fun `given book with media status not ready when getting specific book page then returns not found`(status: Media.Status) {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1").also { it.media.status = status }).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      mockMvc.get("/api/v1/books/${book.id}/pages/1")
        .andExpect { status { isNotFound } }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["25", "-5", "0"])
  @WithMockCustomUser
  fun `given book with pages when getting non-existent page then returns bad request`(page: String) {
    val series = makeSeries(name = "series").also { it.libraryId = library.id }
    val books = listOf(makeBook("1").also {
      it.media.pages = listOf(BookPage("file", "image/jpeg"))
      it.media.status = Media.Status.READY
    }
    ).also { list -> list.forEach { it.libraryId = library.id } }
    seriesLifecycle.createSeries(series, books)

    val book = bookRepository.findAll().first()

    mockMvc.get("/api/v1/books/${book.id}/pages/$page")
      .andExpect { status { isBadRequest } }
  }

  @Nested
  inner class DtoUrlSanitization {
    @Test
    @WithMockCustomUser
    fun `given regular user when getting books then full url is hidden`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1.cbr")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      val validation: MockMvcResultMatchersDsl.() -> Unit = {
        status { isOk }
        jsonPath("$.content[0].url") { value("1.cbr") }
      }

      mockMvc.get("/api/v1/books")
        .andExpect(validation)

      mockMvc.get("/api/v1/books/latest")
        .andExpect(validation)

      mockMvc.get("/api/v1/series/${series.id}/books")
        .andExpect(validation)

      mockMvc.get("/api/v1/books/${book.id}")
        .andExpect {
          status { isOk }
          jsonPath("$.url") { value("1.cbr") }
        }
    }

    @Test
    @WithMockCustomUser(roles = ["ADMIN"])
    fun `given admin user when getting books then full url is available`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1.cbr")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      val url = "/1.cbr"
      val validation: MockMvcResultMatchersDsl.() -> Unit = {
        status { isOk }
        jsonPath("$.content[0].url") { value(url) }
      }

      mockMvc.get("/api/v1/books")
        .andExpect(validation)

      mockMvc.get("/api/v1/books/latest")
        .andExpect(validation)

      mockMvc.get("/api/v1/series/${series.id}/books")
        .andExpect(validation)

      mockMvc.get("/api/v1/books/${book.id}")
        .andExpect {
          status { isOk }
          jsonPath("$.url") { value(url) }
        }
    }
  }

  @Nested
  inner class HttpCache {
    @Test
    @WithMockCustomUser
    fun `given request with cache headers when getting thumbnail then returns 304 not modified`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1.cbr").also {
        it.media.thumbnail = Random.nextBytes(100)
      }).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()


      val url = "/api/v1/books/${book.id}/thumbnail"

      val response = mockMvc.get(url)
        .andReturn().response

      mockMvc.get(url) {
        headers {
          ifNoneMatch = listOf(response.getHeader(HttpHeaders.ETAG)!!)
        }
      }.andExpect {
        status { isNotModified }
      }
    }

    @Test
    @WithMockCustomUser
    fun `given request with If-Modified-Since headers when getting page then returns 304 not modified`() {
      val series = makeSeries(name = "series").also { it.libraryId = library.id }
      val books = listOf(makeBook("1.cbr")).also { list -> list.forEach { it.libraryId = library.id } }
      seriesLifecycle.createSeries(series, books)

      val book = bookRepository.findAll().first()

      val url = "/api/v1/books/${book.id}/pages/1"

      val lastModified = mockMvc.get(url)
        .andReturn().response.getHeader(HttpHeaders.LAST_MODIFIED)

      mockMvc.get(url) {
        headers {
          set(HttpHeaders.IF_MODIFIED_SINCE, lastModified!!)
        }
      }.andExpect {
        status { isNotModified }
      }
    }
  }

  //Not part of the above @Nested class because @Transactional fails
  @Test
  @WithMockCustomUser
  @Transactional
  fun `given request with cache headers and modified resource when getting thumbnail then returns 200 ok`() {
    val series = makeSeries(name = "series").also { it.libraryId = library.id }
    val books = listOf(makeBook("1.cbr").also {
      it.media.thumbnail = Random.nextBytes(1)
    }).also { list -> list.forEach { it.libraryId = library.id } }
    seriesLifecycle.createSeries(series, books)

    val book = bookRepository.findAll().first()

    val url = "/api/v1/books/${book.id}/thumbnail"

    val response = mockMvc.get(url)
      .andReturn().response

    Thread.sleep(100)
    book.media.thumbnail = Random.nextBytes(1)
    bookRepository.saveAndFlush(book)

    mockMvc.get(url) {
      headers {
        ifNoneMatch = listOf(response.getHeader(HttpHeaders.ETAG)!!)
      }
    }.andExpect {
      status { isOk }
    }
  }

  @Nested
  inner class MetadataUpdate {
    @Test
    @WithMockCustomUser
    fun `given non-admin user when updating metadata then raise forbidden`() {
      mockMvc.patch("/api/v1/books/1/metadata") {
        contentType = MediaType.APPLICATION_JSON
        content = "{}"
      }.andExpect {
        status { isForbidden }
      }
    }

    @ParameterizedTest
    @ValueSource(strings = [
      """{"title":""}""",
      """{"number":""}""",
      """{"authors":"[{"name":""}]"}""",
      """{"ageRating":-1}"""
    ])
    @WithMockCustomUser(roles = ["ADMIN"])
    fun `given invalid json when updating metadata then raise validation error`(jsonString: String) {
      mockMvc.patch("/api/v1/books/1/metadata") {
        contentType = MediaType.APPLICATION_JSON
        content = jsonString
      }.andExpect {
        status { isBadRequest }
      }
    }
  }

  //Not part of the above @Nested class because @Transactional fails
  @Test
  @Transactional
  @WithMockCustomUser(roles = ["ADMIN"])
  fun `given valid json when updating metadata then fields are updated`() {
    val series = makeSeries(name = "series").also { it.libraryId = library.id }
    val books = listOf(makeBook("1.cbr")).also { list -> list.forEach { it.libraryId = library.id } }
    seriesLifecycle.createSeries(series, books)

    val bookId = bookRepository.findAll().first().id

    val jsonString = """
        {
          "title":"newTitle",
          "titleLock":true,
          "summary":"newSummary",
          "summaryLock":true,
          "number":"newNumber",
          "numberLock":true,
          "numberSort": 1.0,
          "numberSortLock":true,
          "readingDirection":"LEFT_TO_RIGHT",
          "readingDirectionLock":true,
          "publisher":"newPublisher",
          "publisherLock":true,
          "ageRating":12,
          "ageRatingLock":true,
          "releaseDate":"2020-01-01",
          "releaseDateLock":true,
          "authors":[
            {
              "name":"newAuthor",
              "role":"newAuthorRole"
            },
            {
              "name":"newAuthor2",
              "role":"newAuthorRole2"
            }
          ],
          "authorsLock":true
        }
      """.trimIndent()

    mockMvc.patch("/api/v1/books/${bookId}/metadata") {
      contentType = MediaType.APPLICATION_JSON
      content = jsonString
    }.andExpect {
      status { isOk }
    }

    val updatedBook = bookRepository.findByIdOrNull(bookId)
    with(updatedBook!!.metadata) {
      assertThat(title).isEqualTo("newTitle")
      assertThat(summary).isEqualTo("newSummary")
      assertThat(number).isEqualTo("newNumber")
      assertThat(numberSort).isEqualTo(1F)
      assertThat(readingDirection).isEqualTo(BookMetadata.ReadingDirection.LEFT_TO_RIGHT)
      assertThat(publisher).isEqualTo("newPublisher")
      assertThat(ageRating).isEqualTo(12)
      assertThat(releaseDate).isEqualTo(LocalDate.of(2020, 1, 1))
      assertThat(authors)
        .hasSize(2)
        .extracting("name", "role")
        .containsExactlyInAnyOrder(
          tuple("newAuthor", "newauthorrole"),
          tuple("newAuthor2", "newauthorrole2")
        )

      assertThat(titleLock).isEqualTo(true)
      assertThat(summaryLock).isEqualTo(true)
      assertThat(numberLock).isEqualTo(true)
      assertThat(numberSortLock).isEqualTo(true)
      assertThat(readingDirectionLock).isEqualTo(true)
      assertThat(publisherLock).isEqualTo(true)
      assertThat(ageRatingLock).isEqualTo(true)
      assertThat(releaseDateLock).isEqualTo(true)
      assertThat(authorsLock).isEqualTo(true)
    }
  }

  //Not part of the above @Nested class because @Transactional fails
  @Test
  @Transactional
  @WithMockCustomUser(roles = ["ADMIN"])
  fun `given json with null fields when updating metadata then fields with null are unset`() {
    val testDate = LocalDate.of(2020, 1, 1)

    val series = makeSeries(name = "series").also { it.libraryId = library.id }
    val books = listOf(makeBook("1.cbr").also {
      it.metadata.ageRating = 12
      it.metadata.readingDirection = BookMetadata.ReadingDirection.LEFT_TO_RIGHT
      it.metadata.authors.add(Author("Author", "role"))
      it.metadata.releaseDate = testDate
    }).also { list -> list.forEach { it.libraryId = library.id } }
    seriesLifecycle.createSeries(series, books)

    val bookId = bookRepository.findAll().first().id

    val initialBook = bookRepository.findByIdOrNull(bookId)
    with(initialBook!!.metadata) {
      assertThat(readingDirection).isEqualTo(BookMetadata.ReadingDirection.LEFT_TO_RIGHT)
      assertThat(ageRating).isEqualTo(12)
      assertThat(authors).hasSize(1)
      assertThat(releaseDate).isEqualTo(testDate)
    }

    val jsonString = """
        {
          "readingDirection":null,
          "ageRating":null,
          "authors":null,
          "releaseDate":null
        }
      """.trimIndent()

    mockMvc.patch("/api/v1/books/${bookId}/metadata") {
      contentType = MediaType.APPLICATION_JSON
      content = jsonString
    }.andExpect {
      status { isOk }
    }

    val updatedBook = bookRepository.findByIdOrNull(bookId)
    with(updatedBook!!.metadata) {
      assertThat(readingDirection).isNull()
      assertThat(ageRating).isNull()
      assertThat(authors).isEmpty()
      assertThat(releaseDate).isNull()
    }
  }

  //Not part of the above @Nested class because @Transactional fails
  @Test
  @Transactional
  @WithMockCustomUser(roles = ["ADMIN"])
  fun `given json without fields when updating metadata then existing fields are untouched`() {
    val testDate = LocalDate.of(2020, 1, 1)

    val series = makeSeries(name = "series").also { it.libraryId = library.id }
    val books = listOf(makeBook("1.cbr").also {
      with(it.metadata)
      {
        ageRating = 12
        readingDirection = BookMetadata.ReadingDirection.LEFT_TO_RIGHT
        authors.add(Author("Author", "role"))
        releaseDate = testDate
        summary = "summary"
        number = "number"
        numberLock = true
        numberSort = 2F
        numberSortLock = true
        publisher = "publisher"
        title = "title"
      }
    }).also { list -> list.forEach { it.libraryId = library.id } }
    seriesLifecycle.createSeries(series, books)


    val bookId = bookRepository.findAll().first().id

    val jsonString = """
        {
        }
      """.trimIndent()

    mockMvc.patch("/api/v1/books/${bookId}/metadata") {
      contentType = MediaType.APPLICATION_JSON
      content = jsonString
    }.andExpect {
      status { isOk }
    }

    val updatedBook = bookRepository.findByIdOrNull(bookId)
    with(updatedBook!!.metadata) {
      assertThat(readingDirection).isEqualTo(BookMetadata.ReadingDirection.LEFT_TO_RIGHT)
      assertThat(ageRating).isEqualTo(12)
      assertThat(authors).hasSize(1)
      assertThat(releaseDate).isEqualTo(testDate)
      assertThat(summary).isEqualTo("summary")
      assertThat(number).isEqualTo("number")
      assertThat(numberSort).isEqualTo(2F)
      assertThat(publisher).isEqualTo("publisher")
      assertThat(title).isEqualTo("title")
    }
  }
}
