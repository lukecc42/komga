package org.gotson.komga.interfaces.rest

import com.github.klinq.jpaspec.`in`
import com.github.klinq.jpaspec.likeLower
import com.github.klinq.jpaspec.toJoin
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import mu.KotlinLogging
import org.gotson.komga.application.service.BookLifecycle
import org.gotson.komga.application.tasks.TaskReceiver
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.ImageConversionException
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaNotReadyException
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.swagger.PageableAsQueryParam
import org.gotson.komga.infrastructure.swagger.PageableWithoutSortAsQueryParam
import org.gotson.komga.interfaces.rest.dto.BookDto
import org.gotson.komga.interfaces.rest.dto.BookMetadataUpdateDto
import org.gotson.komga.interfaces.rest.dto.PageDto
import org.gotson.komga.interfaces.rest.dto.toDto
import org.gotson.komga.interfaces.rest.persistence.BookDtoRepository
import org.gotson.komga.interfaces.rest.persistence.BookSearch
import org.springframework.core.io.FileSystemResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.persistence.criteria.JoinType
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class BookController(
  private val bookRepository: BookRepository,
  private val bookDtoRepository: BookDtoRepository,
  private val bookLifecycle: BookLifecycle,
  private val taskReceiver: TaskReceiver
) {

  @PageableAsQueryParam
  @GetMapping("api/v1/books")
  fun getAllBooks(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String?,
    @RequestParam(name = "library_id", required = false) libraryIds: List<Long>?,
    @RequestParam(name = "media_status", required = false) mediaStatus: List<Media.Status>?,
    @Parameter(hidden = true) page: Pageable
  ): Page<BookDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.title").ignoreCase())
    )

    return mutableListOf<Specification<Book>>().let { specs ->
      when {
        // limited user & libraryIds are specified: filter on provided libraries intersecting user's authorized libraries
        !principal.user.sharedAllLibraries && !libraryIds.isNullOrEmpty() -> {
          val authorizedLibraryIDs = libraryIds.intersect(principal.user.sharedLibrariesIds)
          if (authorizedLibraryIDs.isEmpty()) return@let Page.empty<Book>(pageRequest)
          else specs.add(Book::series.toJoin().join(Series::library, JoinType.INNER).where(Library::id).`in`(authorizedLibraryIDs))
        }

        // limited user: filter on user's authorized libraries
        !principal.user.sharedAllLibraries -> specs.add(Book::series.toJoin().join(Series::library, JoinType.INNER).where(Library::id).`in`(principal.user.sharedLibrariesIds))

        // non-limited user: filter on provided libraries
        !libraryIds.isNullOrEmpty() -> {
          specs.add(Book::series.toJoin().join(Series::library, JoinType.INNER).where(Library::id).`in`(libraryIds))
        }
      }

      if (!searchTerm.isNullOrEmpty()) {
        specs.add(Book::metadata.toJoin().where(BookMetadata::title).likeLower("%$searchTerm%"))
      }

      if (!mediaStatus.isNullOrEmpty()) {
        specs.add(Book::media.toJoin().where(Media::status).`in`(mediaStatus))
      }

      if (specs.isNotEmpty()) {
        bookRepository.findAll(specs.reduce { acc, spec -> acc.and(spec)!! }, pageRequest)
      } else {
        bookRepository.findAll(pageRequest)
      }
    }.map { it.toDto(includeFullUrl = principal.user.roleAdmin) }
  }

  @PageableAsQueryParam
  @GetMapping("api/v1/books2")
  fun getAllBooks2(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String?,
    @RequestParam(name = "library_id", required = false) libraryIds: List<Long>?,
    @RequestParam(name = "media_status", required = false) mediaStatus: List<Media.Status>?,
    @Parameter(hidden = true) page: Pageable
  ): Page<BookDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.title").ignoreCase())
    )

    val filterLibraryIds = when {
      // limited user & libraryIds are specified: filter on provided libraries intersecting user's authorized libraries
      !principal.user.sharedAllLibraries && !libraryIds.isNullOrEmpty() -> libraryIds.intersect(principal.user.sharedLibrariesIds)

      // limited user: filter on user's authorized libraries
      !principal.user.sharedAllLibraries -> principal.user.sharedLibrariesIds

      // non-limited user: filter on provided libraries
      !libraryIds.isNullOrEmpty() -> libraryIds

      else -> emptyList()
    }

    val bookSearch = BookSearch(
      libraryIds = filterLibraryIds,
      searchTerm = searchTerm,
      mediaStatus = mediaStatus ?: emptyList(),
      includeFullUrl = principal.user.roleAdmin
    )

    return bookDtoRepository.findAll(bookSearch, pageRequest)
  }


  @Operation(description = "Return newly added or updated books.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("api/v1/books/latest")
  fun getLatestSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<BookDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    )

    return if (principal.user.sharedAllLibraries) {
      bookRepository.findAll(pageRequest)
    } else {
      bookRepository.findBySeriesLibraryIdIn(principal.user.sharedLibrariesIds, pageRequest)
    }.map { it.toDto(includeFullUrl = principal.user.roleAdmin) }
  }


  @GetMapping("api/v1/books/{bookId}")
  fun getOneBook(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): BookDto =
    bookRepository.findByIdOrNull(bookId)?.let {
      if (!principal.user.canAccessBook(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      it.toDto(includeFullUrl = principal.user.roleAdmin)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @GetMapping("api/v1/books/{bookId}/previous")
  fun getBookSiblingPrevious(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): BookDto =
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

      val previousBook = book.series.books
        .sortedByDescending { it.metadata.numberSort }
        .find { it.metadata.numberSort < book.metadata.numberSort }

      previousBook?.toDto(includeFullUrl = principal.user.roleAdmin)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @GetMapping("api/v1/books/{bookId}/next")
  fun getBookSiblingNext(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): BookDto =
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

      val nextBook = book.series.books
        .sortedBy { it.metadata.numberSort }
        .find { it.metadata.numberSort > book.metadata.numberSort }

      nextBook?.toDto(includeFullUrl = principal.user.roleAdmin) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)


  @ApiResponse(content = [Content(schema = Schema(type = "string", format = "binary"))])
  @GetMapping(value = [
    "api/v1/books/{bookId}/thumbnail",
    "opds/v1.2/books/{bookId}/thumbnail"
  ], produces = [MediaType.IMAGE_JPEG_VALUE])
  fun getBookThumbnail(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): ResponseEntity<ByteArray> =
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      if (book.media.thumbnail != null) {
        ResponseEntity.ok()
          .setCachePrivate()
          .body(book.media.thumbnail)
      } else throw ResponseStatusException(HttpStatus.NOT_FOUND)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @Operation(description = "Download the book file.")
  @GetMapping(value = [
    "api/v1/books/{bookId}/file",
    "api/v1/books/{bookId}/file/*",
    "opds/v1.2/books/{bookId}/file/*"
  ], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
  fun getBookFile(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): ResponseEntity<FileSystemResource> =
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      try {
        with(FileSystemResource(book.path())) {
          if (!exists()) throw FileNotFoundException(path)
          ResponseEntity.ok()
            .headers(HttpHeaders().apply {
              contentDisposition = ContentDisposition.builder("attachment")
                .filename(book.fileName())
                .build()
            })
            .contentType(getMediaTypeOrDefault(book.media.mediaType))
            .body(this)
        }
      } catch (ex: FileNotFoundException) {
        logger.warn(ex) { "File not found: $book" }
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found, it may have moved")
      }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)


  @GetMapping("api/v1/books/{bookId}/pages")
  fun getBookPages(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable bookId: Long
  ): List<PageDto> =
    bookRepository.findByIdOrNull((bookId))?.let {
      if (!principal.user.canAccessBook(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      if (it.media.status == Media.Status.UNKNOWN) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Book has not been analyzed yet")
      if (it.media.status in listOf(Media.Status.ERROR, Media.Status.UNSUPPORTED)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Book analysis failed")

      it.media.pages.mapIndexed { index, s -> PageDto(index + 1, s.fileName, s.mediaType) }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @ApiResponse(content = [Content(
    mediaType = "image/*",
    schema = Schema(type = "string", format = "binary")
  )])
  @GetMapping(value = [
    "api/v1/books/{bookId}/pages/{pageNumber}",
    "opds/v1.2/books/{bookId}/pages/{pageNumber}"
  ])
  fun getBookPage(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    request: WebRequest,
    @PathVariable bookId: Long,
    @PathVariable pageNumber: Int,
    @Parameter(description = "Convert the image to the provided format.", schema = Schema(allowableValues = ["jpeg", "png"]))
    @RequestParam(value = "convert", required = false) convertTo: String?,
    @Parameter(description = "If set to true, pages will start at index 0. If set to false, pages will start at index 1.")
    @RequestParam(value = "zero_based", defaultValue = "false") zeroBasedIndex: Boolean
  ): ResponseEntity<ByteArray> =
    bookRepository.findByIdOrNull((bookId))?.let { book ->
      if (request.checkNotModified(getBookLastModified(book))) {
        return@let ResponseEntity
          .status(HttpStatus.NOT_MODIFIED)
          .setNotModified(book)
          .body(ByteArray(0))
      }
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      try {
        val convertFormat = when (convertTo?.toLowerCase()) {
          "jpeg" -> ImageType.JPEG
          "png" -> ImageType.PNG
          "", null -> null
          else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversion format: $convertTo")
        }

        val pageNum = if (zeroBasedIndex) pageNumber + 1 else pageNumber

        val pageContent = bookLifecycle.getBookPage(book, pageNum, convertFormat)

        ResponseEntity.ok()
          .contentType(getMediaTypeOrDefault(pageContent.mediaType))
          .setNotModified(book)
          .body(pageContent.content)
      } catch (ex: IndexOutOfBoundsException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number does not exist")
      } catch (ex: ImageConversionException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
      } catch (ex: MediaNotReadyException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Book analysis failed")
      } catch (ex: NoSuchFileException) {
        logger.warn(ex) { "File not found: $book" }
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found, it may have moved")
      }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @ApiResponse(content = [Content(schema = Schema(type = "string", format = "binary"))])
  @GetMapping(
    value = ["api/v1/books/{bookId}/pages/{pageNumber}/thumbnail"],
    produces = [MediaType.IMAGE_JPEG_VALUE]
  )
  fun getBookPageThumbnail(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    request: WebRequest,
    @PathVariable bookId: Long,
    @PathVariable pageNumber: Int
  ): ResponseEntity<ByteArray> =
    bookRepository.findByIdOrNull((bookId))?.let { book ->
      if (request.checkNotModified(getBookLastModified(book))) {
        return@let ResponseEntity
          .status(HttpStatus.NOT_MODIFIED)
          .setNotModified(book)
          .body(ByteArray(0))
      }
      if (!principal.user.canAccessBook(book)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      try {
        val pageContent = bookLifecycle.getBookPage(book, pageNumber, resizeTo = 300)

        ResponseEntity.ok()
          .contentType(getMediaTypeOrDefault(pageContent.mediaType))
          .setNotModified(book)
          .body(pageContent.content)
      } catch (ex: IndexOutOfBoundsException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number does not exist")
      } catch (ex: ImageConversionException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
      } catch (ex: MediaNotReadyException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Book analysis failed")
      } catch (ex: NoSuchFileException) {
        logger.warn(ex) { "File not found: $book" }
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found, it may have moved")
      }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @PostMapping("api/v1/books/{bookId}/analyze")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun analyze(@PathVariable bookId: Long) {
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      taskReceiver.analyzeBook(book)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  }

  @PostMapping("api/v1/books/{bookId}/metadata/refresh")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun refreshMetadata(@PathVariable bookId: Long) {
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      taskReceiver.refreshBookMetadata(book)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  }

  @PatchMapping("api/v1/books/{bookId}/metadata")
  @PreAuthorize("hasRole('ADMIN')")
  fun updateMetadata(
    @PathVariable bookId: Long,
    @Parameter(description = "Metadata fields to update. Set a field to null to unset the metadata. You can omit fields you don't want to update.")
    @Valid @RequestBody newMetadata: BookMetadataUpdateDto
  ): BookDto =
    bookRepository.findByIdOrNull(bookId)?.let { book ->
      with(newMetadata) {
        title?.let { book.metadata.title = it }
        titleLock?.let { book.metadata.titleLock = it }
        summary?.let { book.metadata.summary = it }
        summaryLock?.let { book.metadata.summaryLock = it }
        number?.let { book.metadata.number = it }
        numberLock?.let { book.metadata.numberLock = it }
        numberSort?.let { book.metadata.numberSort = it }
        numberSortLock?.let { book.metadata.numberSortLock = it }
        if (isSet("readingDirection")) book.metadata.readingDirection = newMetadata.readingDirection
        readingDirectionLock?.let { book.metadata.readingDirectionLock = it }
        publisher?.let { book.metadata.publisher = it }
        publisherLock?.let { book.metadata.publisherLock = it }
        if (isSet("ageRating")) book.metadata.ageRating = newMetadata.ageRating
        ageRatingLock?.let { book.metadata.ageRatingLock = it }
        if (isSet("releaseDate")) {
          book.metadata.releaseDate = newMetadata.releaseDate
        }
        releaseDateLock?.let { book.metadata.releaseDateLock = it }
        if (isSet("authors")) {
          if (authors != null) {
            book.metadata.authors = authors!!.map {
              Author(it.name ?: "", it.role ?: "")
            }.toMutableList()
          } else book.metadata.authors = mutableListOf()
        }
        authorsLock?.let { book.metadata.authorsLock = it }
      }
      bookRepository.save(book).toDto(includeFullUrl = true)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  private fun ResponseEntity.BodyBuilder.setCachePrivate() =
    this.cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS)
      .cachePrivate()
      .mustRevalidate()
    )

  private fun ResponseEntity.BodyBuilder.setNotModified(book: Book) =
    this.setCachePrivate().lastModified(getBookLastModified(book))

  private fun getBookLastModified(book: Book) =
    book.media.lastModifiedDate!!.toInstant(ZoneOffset.UTC).toEpochMilli()


  private fun getMediaTypeOrDefault(mediaTypeString: String?): MediaType {
    mediaTypeString?.let {
      try {
        return MediaType.parseMediaType(mediaTypeString)
      } catch (ex: Exception) {
      }
    }
    return MediaType.APPLICATION_OCTET_STREAM
  }
}

