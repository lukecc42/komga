package org.gotson.komga.interfaces.rest

import org.gotson.komga.infrastructure.security.KomgaUserDetailsLifecycle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch

@ExtendWith(SpringExtension::class)
@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@ActiveProfiles("demo")
class UserControllerTest(
  @Autowired private val userDetailsLifecycle: KomgaUserDetailsLifecycle,
  @Autowired private val mockMvc: MockMvc

) {
  @Test
  @WithMockCustomUser
  fun `given demo profile is active when a user tries to update its password via api then returns forbidden`() {
    val jsonString = """{"password":"new"}"""

    mockMvc.patch("/api/v1/users/me/password") {
      contentType = MediaType.APPLICATION_JSON
      content = jsonString
    }.andExpect {
      status { isForbidden }
    }
  }
}
