package org.gotson.komga.application.tasks

import mu.KotlinLogging
import org.gotson.komga.application.service.BookLifecycle
import org.gotson.komga.application.service.MetadataLifecycle
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.service.LibraryScanner
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS_SELECTOR
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

@Service
class TaskHandler(
  private val taskReceiver: TaskReceiver,
  private val libraryRepository: LibraryRepository,
  private val bookRepository: BookRepository,
  private val libraryScanner: LibraryScanner,
  private val bookLifecycle: BookLifecycle,
  private val metadataLifecycle: MetadataLifecycle
) {

  @JmsListener(destination = QUEUE_TASKS, selector = QUEUE_TASKS_SELECTOR)
  @Transactional
  fun handleTask(task: Task) {
    logger.info { "Executing task: $task" }
    try {
      measureTime {
        when (task) {
          is Task.ScanLibrary ->
            libraryRepository.findByIdOrNull(task.libraryId)?.let {
              libraryScanner.scanRootFolder(it)
              taskReceiver.analyzeUnknownBooks(it)
            } ?: logger.warn { "Cannot execute task $task: Library does not exist" }

          is Task.AnalyzeBook ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              bookLifecycle.analyzeAndPersist(it)
              taskReceiver.refreshBookMetadata(it)
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }

          is Task.GenerateBookThumbnail ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              bookLifecycle.regenerateThumbnailAndPersist(it)
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }

          is Task.RefreshBookMetadata ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              metadataLifecycle.refreshMetadata(it)
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }
        }
      }.also {
        logger.info { "Task $task executed in $it" }
      }
    } catch (e: Exception) {
      logger.error(e) { "Task $task execution failed" }
    }
  }
}


