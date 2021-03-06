package org.gotson.komga.domain.persistence

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Library
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KomgaUserRepository : CrudRepository<KomgaUser, Long> {
  fun existsByEmailIgnoreCase(email: String): Boolean
  fun findByEmailIgnoreCase(email: String): KomgaUser?
  fun findBySharedLibrariesContaining(library: Library): List<KomgaUser>
}
