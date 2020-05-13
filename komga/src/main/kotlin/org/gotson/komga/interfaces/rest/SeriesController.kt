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
import org.gotson.komga.application.tasks.TaskReceiver
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.swagger.PageableAsQueryParam
import org.gotson.komga.infrastructure.swagger.PageableWithoutSortAsQueryParam
import org.gotson.komga.interfaces.rest.dto.BookDto
import org.gotson.komga.interfaces.rest.dto.SeriesDto
import org.gotson.komga.interfaces.rest.dto.SeriesMetadataUpdateDto
import org.gotson.komga.interfaces.rest.dto.restrictUrl
import org.gotson.komga.interfaces.rest.dto.toDto
import org.gotson.komga.interfaces.rest.persistence.BookDtoRepository
import org.gotson.komga.interfaces.rest.persistence.BookSearch
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
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
import org.springframework.web.server.ResponseStatusException
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("api/v1/series", produces = [MediaType.APPLICATION_JSON_VALUE])
class SeriesController(
  private val seriesRepository: SeriesRepository,
  private val bookDtoRepository: BookDtoRepository,
  private val bookController: BookController,
  private val taskReceiver: TaskReceiver
) {

  @GetMapping
  fun getAllSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String?,
    @RequestParam(name = "library_id", required = false) libraryIds: List<Long>?,
    @RequestParam(name = "status", required = false) metadataStatus: List<SeriesMetadata.Status>?,
    @ParameterObject page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.titleSort").ignoreCase())
    )

    return mutableListOf<Specification<Series>>().let { specs ->
      when {
        // limited user & libraryIds are specified: filter on provided libraries intersecting user's authorized libraries
        !principal.user.sharedAllLibraries && !libraryIds.isNullOrEmpty() -> {
          val authorizedLibraryIDs = libraryIds.intersect(principal.user.sharedLibrariesIds)
          if (authorizedLibraryIDs.isEmpty()) return@let Page.empty<Series>(pageRequest)
          else specs.add(Series::library.toJoin().where(Library::id).`in`(authorizedLibraryIDs))
        }

        // limited user: filter on user's authorized libraries
        !principal.user.sharedAllLibraries -> specs.add(Series::library.toJoin().where(Library::id).`in`(principal.user.sharedLibrariesIds))

        // non-limited user: filter on provided libraries
        !libraryIds.isNullOrEmpty() -> {
          specs.add(Series::library.toJoin().where(Library::id).`in`(libraryIds))
        }
      }

      if (!searchTerm.isNullOrEmpty()) {
        specs.add(Series::metadata.toJoin().where(SeriesMetadata::title).likeLower("%$searchTerm%"))
      }

      if (!metadataStatus.isNullOrEmpty()) {
        specs.add(Series::metadata.toJoin().where(SeriesMetadata::status).`in`(metadataStatus))
      }

      if (specs.isNotEmpty()) {
        seriesRepository.findAll(specs.reduce { acc, spec -> acc.and(spec)!! }, pageRequest)
      } else {
        seriesRepository.findAll(pageRequest)
      }
    }.map { it.toDto(includeUrl = principal.user.roleAdmin) }
  }

  @Operation(description = "Return recently added or updated series.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/latest")
  fun getLatestSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    )

    return if (principal.user.sharedAllLibraries) {
      seriesRepository.findAll(pageRequest)
    } else {
      seriesRepository.findByLibraryIdIn(principal.user.sharedLibrariesIds, pageRequest)
    }.map { it.toDto(includeUrl = principal.user.roleAdmin) }
  }

  @Operation(description = "Return newly added series.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/new")
  fun getNewSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "createdDate")
    )

    return if (principal.user.sharedAllLibraries) {
      seriesRepository.findAll(pageRequest)
    } else {
      seriesRepository.findByLibraryIdIn(principal.user.sharedLibrariesIds, pageRequest)
    }.map { it.toDto(includeUrl = principal.user.roleAdmin) }
  }

  @Operation(description = "Return recently updated series, but not newly added ones.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/updated")
  fun getUpdatedSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    )

    return if (principal.user.sharedAllLibraries) {
      seriesRepository.findRecentlyUpdated(pageRequest)
    } else {
      seriesRepository.findRecentlyUpdatedByLibraryIdIn(principal.user.sharedLibrariesIds, pageRequest)
    }.map { it.toDto(includeUrl = principal.user.roleAdmin) }
  }

  @GetMapping("{seriesId}")
  fun getOneSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") id: Long
  ): SeriesDto =
    seriesRepository.findByIdOrNull(id)?.let {
      if (!principal.user.canAccessSeries(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      it.toDto(includeUrl = principal.user.roleAdmin)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @ApiResponse(content = [Content(schema = Schema(type = "string", format = "binary"))])
  @GetMapping(value = ["{seriesId}/thumbnail"], produces = [MediaType.IMAGE_JPEG_VALUE])
  fun getSeriesThumbnail(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") id: Long
  ): ResponseEntity<ByteArray> =
    seriesRepository.findByIdOrNull(id)?.let { series ->
      if (!principal.user.canAccessSeries(series)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

      series.books.minBy { it.metadata.numberSort }?.let { firstBook ->
        bookController.getBookThumbnail(principal, firstBook.id)
      } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @PageableAsQueryParam
  @GetMapping("{seriesId}/books")
  fun getAllBooksBySeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") id: Long,
    @RequestParam(name = "media_status", required = false) mediaStatus: List<Media.Status>?,
    @Parameter(hidden = true) page: Pageable
  ): Page<BookDto> {
    seriesRepository.findByIdOrNull(id)?.let {
      if (!principal.user.canAccessSeries(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.numberSort"))
    )

    return bookDtoRepository.findAll(
      BookSearch(
        seriesIds = listOf(id),
        mediaStatus = mediaStatus ?: emptyList()
      ),
      pageRequest
    ).map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @PostMapping("{seriesId}/analyze")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun analyze(@PathVariable seriesId: Long) {
    seriesRepository.findByIdOrNull(seriesId)?.let { series ->
      series.books.forEach { taskReceiver.analyzeBook(it) }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  }

  @PostMapping("{seriesId}/metadata/refresh")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun refreshMetadata(@PathVariable seriesId: Long) {
    seriesRepository.findByIdOrNull(seriesId)?.let { series ->
      series.books.forEach { taskReceiver.refreshBookMetadata(it) }
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  }

  @PatchMapping("{seriesId}/metadata")
  @PreAuthorize("hasRole('ADMIN')")
  fun updateMetadata(
    @PathVariable seriesId: Long,
    @Parameter(description = "Metadata fields to update. Set a field to null to unset the metadata. You can omit fields you don't want to update.")
    @Valid @RequestBody newMetadata: SeriesMetadataUpdateDto
  ): SeriesDto =
    seriesRepository.findByIdOrNull(seriesId)?.let { series ->
      with(newMetadata) {
        status?.let { series.metadata.status = it }
        statusLock?.let { series.metadata.statusLock = it }
        title?.let { series.metadata.title = it }
        titleLock?.let { series.metadata.titleLock = it }
        titleSort?.let { series.metadata.titleSort = it }
        titleSortLock?.let { series.metadata.titleSortLock = it }
      }
      seriesRepository.save(series).toDto(includeUrl = true)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

}
