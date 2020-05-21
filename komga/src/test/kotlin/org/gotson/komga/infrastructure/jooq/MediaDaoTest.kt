package org.gotson.komga.infrastructure.jooq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.makeBook
import org.gotson.komga.domain.model.makeLibrary
import org.gotson.komga.domain.model.makeSeries
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime
import kotlin.random.Random

@ExtendWith(SpringExtension::class)
@SpringBootTest
@AutoConfigureTestDatabase
class MediaDaoTest(
  @Autowired private val mediaDao: MediaDao,
  @Autowired private val bookRepository: BookRepository,
  @Autowired private val seriesRepository: SeriesRepository,
  @Autowired private val libraryRepository: LibraryRepository
) {
  private var library = makeLibrary()
  private var series = makeSeries("Series")
  private var book = makeBook("Book")

  @BeforeAll
  fun setup() {
    library = libraryRepository.insert(library)

    series.libraryId = library.id
    series = seriesRepository.insert(series)

    book.libraryId = library.id
    book.seriesId = series.id
    book = bookRepository.insert(book)
  }

  @AfterEach
  fun deleteMedia() {
    bookRepository.findAll().forEach {
      mediaDao.delete(it.id)
    }
  }

  @AfterAll
  fun tearDown() {
    bookRepository.deleteAll()
    seriesRepository.deleteAll()
    libraryRepository.deleteAll()
  }

  @Test
  fun `given a media when inserting then it is persisted`() {
    val now = LocalDateTime.now()
    val media = Media(
      status = Media.Status.READY,
      mediaType = "application/zip",
      thumbnail = Random.nextBytes(1),
      pages = listOf(BookPage(
        fileName = "1.jpg",
        mediaType = "image/jpeg"
      )),
      files = listOf("ComicInfo.xml"),
      comment = "comment"
    ).also {
      it.bookId = book.id
    }

    Thread.sleep(5)

    val created = mediaDao.insert(media)

    assertThat(created.bookId).isEqualTo(book.id)
    assertThat(created.createdDate).isAfter(now)
    assertThat(created.lastModifiedDate).isAfter(now)
    assertThat(created.status).isEqualTo(media.status)
    assertThat(created.mediaType).isEqualTo(media.mediaType)
    assertThat(created.thumbnail).isEqualTo(media.thumbnail)
    assertThat(created.comment).isEqualTo(media.comment)
    assertThat(created.pages).hasSize(1)
    with(created.pages.first()) {
      assertThat(fileName).isEqualTo(media.pages.first().fileName)
      assertThat(mediaType).isEqualTo(media.pages.first().mediaType)
    }
    assertThat(created.files).hasSize(1)
    assertThat(created.files.first()).isEqualTo(media.files.first())
  }

  @Test
  fun `given a minimum media when inserting then it is persisted`() {
    val media = Media().also { it.bookId = book.id }

    val created = mediaDao.insert(media)

    assertThat(created.bookId).isEqualTo(book.id)
    assertThat(created.status).isEqualTo(Media.Status.UNKNOWN)
    assertThat(created.mediaType).isNull()
    assertThat(created.thumbnail).isNull()
    assertThat(created.comment).isNull()
    assertThat(created.pages).isEmpty()
    assertThat(created.files).isEmpty()
  }

  @Test
  fun `given existing media when updating then it is persisted`() {
    val media = Media(
      status = Media.Status.READY,
      mediaType = "application/zip",
      thumbnail = Random.nextBytes(1),
      pages = listOf(BookPage(
        fileName = "1.jpg",
        mediaType = "image/jpeg"
      )),
      files = listOf("ComicInfo.xml"),
      comment = "comment"
    ).also {
      it.bookId = book.id
    }
    val created = mediaDao.insert(media)

    Thread.sleep(5)

    val modificationDate = LocalDateTime.now()

    with(created) {
      status = Media.Status.ERROR
      mediaType = "application/rar"
      thumbnail = Random.nextBytes(1)
      pages = listOf(BookPage(
        fileName = "2.png",
        mediaType = "image/png"
      ))
      files = listOf("id.txt")
      comment = "comment2"
    }

    mediaDao.update(created)
    val modified = mediaDao.findById(created.bookId)

    assertThat(modified.bookId).isEqualTo(created.bookId)
    assertThat(modified.createdDate).isEqualTo(created.createdDate)
    assertThat(modified.lastModifiedDate)
      .isAfterOrEqualTo(modificationDate)
      .isNotEqualTo(created.lastModifiedDate)
    assertThat(modified.status).isEqualTo(created.status)
    assertThat(modified.mediaType).isEqualTo(created.mediaType)
    assertThat(modified.thumbnail).isEqualTo(created.thumbnail)
    assertThat(modified.comment).isEqualTo(created.comment)
    assertThat(modified.pages.first().fileName).isEqualTo(created.pages.first().fileName)
    assertThat(modified.pages.first().mediaType).isEqualTo(created.pages.first().mediaType)
    assertThat(modified.files.first()).isEqualTo(created.files.first())
  }

  @Test
  fun `given existing media when finding by id then media is returned`() {
    val media = Media(
      status = Media.Status.READY,
      mediaType = "application/zip",
      thumbnail = Random.nextBytes(1),
      pages = listOf(BookPage(
        fileName = "1.jpg",
        mediaType = "image/jpeg"
      )),
      files = listOf("ComicInfo.xml"),
      comment = "comment"
    ).also {
      it.bookId = book.id
    }
    val created = mediaDao.insert(media)

    val found = catchThrowable { mediaDao.findById(created.bookId) }

    assertThat(found).doesNotThrowAnyException()
  }

  @Test
  fun `given non-existing media when finding by id then exception is thrown`() {
    val found = catchThrowable { mediaDao.findById(128742) }

    assertThat(found).isInstanceOf(Exception::class.java)
  }
}
