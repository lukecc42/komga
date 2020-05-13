package org.gotson.komga.interfaces.opds

import com.github.klinq.jpaspec.`in`
import com.github.klinq.jpaspec.likeLower
import com.github.klinq.jpaspec.toJoin
import mu.KotlinLogging
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.opds.dto.OpdsAuthor
import org.gotson.komga.interfaces.opds.dto.OpdsEntryAcquisition
import org.gotson.komga.interfaces.opds.dto.OpdsEntryNavigation
import org.gotson.komga.interfaces.opds.dto.OpdsFeed
import org.gotson.komga.interfaces.opds.dto.OpdsFeedAcquisition
import org.gotson.komga.interfaces.opds.dto.OpdsFeedNavigation
import org.gotson.komga.interfaces.opds.dto.OpdsLinkFeedNavigation
import org.gotson.komga.interfaces.opds.dto.OpdsLinkFileAcquisition
import org.gotson.komga.interfaces.opds.dto.OpdsLinkImage
import org.gotson.komga.interfaces.opds.dto.OpdsLinkImageThumbnail
import org.gotson.komga.interfaces.opds.dto.OpdsLinkPageStreaming
import org.gotson.komga.interfaces.opds.dto.OpdsLinkRel
import org.gotson.komga.interfaces.opds.dto.OpdsLinkSearch
import org.gotson.komga.interfaces.opds.dto.OpenSearchDescription
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.servlet.ServletContext

private val logger = KotlinLogging.logger {}

private const val ROUTE_BASE = "/opds/v1.2/"
private const val ROUTE_CATALOG = "catalog"
private const val ROUTE_SERIES_ALL = "series"
private const val ROUTE_SERIES_LATEST = "series/latest"
private const val ROUTE_LIBRARIES_ALL = "libraries"
private const val ROUTE_SEARCH = "search"

private const val ID_SERIES_ALL = "allSeries"
private const val ID_SERIES_LATEST = "latestSeries"
private const val ID_LIBRARIES_ALL = "allLibraries"

@RestController
@RequestMapping(value = [ROUTE_BASE], produces = [MediaType.APPLICATION_ATOM_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE])
class OpdsController(
  servletContext: ServletContext,
  private val seriesRepository: SeriesRepository,
  private val libraryRepository: LibraryRepository
) {

  private val routeBase = "${servletContext.contextPath}$ROUTE_BASE"

  private val komgaAuthor = OpdsAuthor("Komga", URI("https://github.com/gotson/komga"))
  private val linkStart = OpdsLinkFeedNavigation(OpdsLinkRel.START, "$routeBase$ROUTE_CATALOG")
  private val linkSearch = OpdsLinkSearch("$routeBase$ROUTE_SEARCH")

  private val decimalFormat = DecimalFormat("0.#")

  private val opdsPseSupportedFormats = listOf("image/jpeg", "image/png", "image/gif")

  private val feedCatalog = OpdsFeedNavigation(
    id = "root",
    title = "Komga OPDS catalog",
    updated = ZonedDateTime.now(),
    author = komgaAuthor,
    links = listOf(
      OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "$routeBase$ROUTE_CATALOG"),
      linkStart,
      linkSearch
    ),
    entries = listOf(
      OpdsEntryNavigation(
        title = "All series",
        updated = ZonedDateTime.now(),
        id = ID_SERIES_ALL,
        content = "Browse by series",
        link = OpdsLinkFeedNavigation(OpdsLinkRel.SUBSECTION, "$routeBase$ROUTE_SERIES_ALL")
      ),
      OpdsEntryNavigation(
        title = "Latest series",
        updated = ZonedDateTime.now(),
        id = ID_SERIES_LATEST,
        content = "Browse latest series",
        link = OpdsLinkFeedNavigation(OpdsLinkRel.SUBSECTION, "$routeBase$ROUTE_SERIES_LATEST")
      ),
      OpdsEntryNavigation(
        title = "All libraries",
        updated = ZonedDateTime.now(),
        id = ID_LIBRARIES_ALL,
        content = "Browse by library",
        link = OpdsLinkFeedNavigation(OpdsLinkRel.SUBSECTION, "$routeBase$ROUTE_LIBRARIES_ALL")
      )
    )
  )

  private val openSearchDescription = OpenSearchDescription(
    shortName = "Search",
    description = "Search for series",
    url = OpenSearchDescription.OpenSearchUrl("$routeBase$ROUTE_SERIES_ALL?search={searchTerms}")
  )

  @GetMapping(ROUTE_CATALOG)
  fun getCatalog(): OpdsFeed = feedCatalog

  @GetMapping(ROUTE_SEARCH)
  fun getSearch(): OpenSearchDescription = openSearchDescription

  @GetMapping(ROUTE_SERIES_ALL)
  fun getAllSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestParam("search") searchTerm: String?
  ): OpdsFeed {
    val sort = Sort.by(Sort.Order.asc("metadata.titleSort").ignoreCase())
    val series =
      mutableListOf<Specification<Series>>().let { specs ->
        if (!principal.user.sharedAllLibraries) {
          specs.add(Series::libraryId.`in`(principal.user.sharedLibrariesIds))
        }

        if (!searchTerm.isNullOrEmpty()) {
          specs.add(Series::metadata.toJoin().where(SeriesMetadata::title).likeLower("%$searchTerm%"))
        }

        if (specs.isNotEmpty()) {
          seriesRepository.findAll(specs.reduce { acc, spec -> acc.and(spec)!! }, sort)
        } else {
          seriesRepository.findAll(sort)
        }
      }

    return OpdsFeedNavigation(
      id = ID_SERIES_ALL,
      title = "All series",
      updated = ZonedDateTime.now(),
      author = komgaAuthor,
      links = listOf(
        OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "$routeBase$ROUTE_SERIES_ALL"),
        linkStart
      ),
      entries = series.map { it.toOpdsEntry() }
    )
  }

  @GetMapping(ROUTE_SERIES_LATEST)
  fun getLatestSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal
  ): OpdsFeed {
    val sort = Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    val series =
      if (principal.user.sharedAllLibraries) {
        seriesRepository.findAll(sort)
      } else {
        seriesRepository.findByLibraryIdIn(principal.user.sharedLibrariesIds, sort)
      }

    return OpdsFeedNavigation(
      id = ID_SERIES_LATEST,
      title = "Latest series",
      updated = ZonedDateTime.now(),
      author = komgaAuthor,
      links = listOf(
        OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "$routeBase$ROUTE_SERIES_LATEST"),
        linkStart
      ),
      entries = series.map { it.toOpdsEntry() }
    )
  }

  @GetMapping(ROUTE_LIBRARIES_ALL)
  fun getLibraries(
    @AuthenticationPrincipal principal: KomgaPrincipal
  ): OpdsFeed {
    val libraries =
      if (principal.user.sharedAllLibraries) {
        libraryRepository.findAll()
      } else {
        libraryRepository.findAllById(principal.user.sharedLibrariesIds)
      }
    return OpdsFeedNavigation(
      id = ID_LIBRARIES_ALL,
      title = "All libraries",
      updated = ZonedDateTime.now(),
      author = komgaAuthor,
      links = listOf(
        OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "$routeBase$ROUTE_LIBRARIES_ALL"),
        linkStart
      ),
      entries = libraries.map { it.toOpdsEntry() }
    )
  }

  @GetMapping("series/{id}")
  fun getOneSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestHeader(name = HttpHeaders.USER_AGENT, required = false, defaultValue = "") userAgent: String,
    @PathVariable id: Long
  ): OpdsFeed =
    seriesRepository.findByIdOrNull(id)?.let { series ->
      if (!principal.user.canAccessSeries(series)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

      OpdsFeedAcquisition(
        id = series.id.toString(),
        title = series.metadata.title,
        updated = series.lastModifiedDate?.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
        author = komgaAuthor,
        links = listOf(
          OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "${routeBase}series/$id"),
          linkStart
        ),
        entries = series.books
          .filter { it.media.status == Media.Status.READY }
          .sortedBy { it.metadata.numberSort }
          .map { it.toOpdsEntry(shouldPrependBookNumbers(userAgent)) }
      )
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @GetMapping("libraries/{id}")
  fun getOneLibrary(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable id: Long
  ): OpdsFeed =
    libraryRepository.findByIdOrNull(id)?.let { library ->
      if (!principal.user.canAccessLibrary(library)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

      OpdsFeedNavigation(
        id = library.id.toString(),
        title = library.name,
        updated = library.lastModifiedDate.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
        author = komgaAuthor,
        links = listOf(
          OpdsLinkFeedNavigation(OpdsLinkRel.SELF, "${routeBase}libraries/$id"),
          linkStart
        ),
        entries = seriesRepository.findByLibraryId(library.id, Sort.by(Sort.Order.asc("metadata.titleSort").ignoreCase())).map { it.toOpdsEntry() }
      )
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)


  private fun Series.toOpdsEntry() =
    OpdsEntryNavigation(
      title = metadata.title,
      updated = lastModifiedDate?.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
      id = id.toString(),
      content = "",
      link = OpdsLinkFeedNavigation(OpdsLinkRel.SUBSECTION, "${routeBase}series/$id")
    )

  private fun Book.toOpdsEntry(prependNumber: Boolean): OpdsEntryAcquisition {
    val mediaTypes = media.pages.map { it.mediaType }.distinct()

    val opdsLinkPageStreaming = if (mediaTypes.size == 1 && mediaTypes.first() in opdsPseSupportedFormats) {
      OpdsLinkPageStreaming(mediaTypes.first(), "${routeBase}books/$id/pages/{pageNumber}?zero_based=true", media.pages.size)
    } else {
      OpdsLinkPageStreaming("image/jpeg", "${routeBase}books/$id/pages/{pageNumber}?convert=jpeg&zero_based=true", media.pages.size)
    }

    return OpdsEntryAcquisition(
      title = "${if (prependNumber) "${decimalFormat.format(metadata.numberSort)} - " else ""}${metadata.title}",
      updated = lastModifiedDate?.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
      id = id.toString(),
      content = run {
        var content = "${fileExtension().toUpperCase()} - ${fileSizeHumanReadable()}"
        if (metadata.summary.isNotBlank())
          content += "\n\n${metadata.summary}"
        content
      },
      authors = metadata.authors.map { OpdsAuthor(it.name) },
      links = listOf(
        OpdsLinkImageThumbnail("image/jpeg", "${routeBase}books/$id/thumbnail"),
        OpdsLinkImage(media.pages[0].mediaType, "${routeBase}books/$id/pages/1"),
        OpdsLinkFileAcquisition(media.mediaType, "${routeBase}books/$id/file/${fileName()}"),
        opdsLinkPageStreaming
      )
    )
  }

  private fun Library.toOpdsEntry(): OpdsEntryNavigation {
    return OpdsEntryNavigation(
      title = name,
      updated = lastModifiedDate.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
      id = id.toString(),
      content = "",
      link = OpdsLinkFeedNavigation(OpdsLinkRel.SUBSECTION, "${routeBase}libraries/$id")
    )
  }

  private fun shouldPrependBookNumbers(userAgent: String) =
    userAgent.contains("chunky", ignoreCase = true)
}
