package com.pios.networkmanagement.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pios.networkmanagement.application.AcceptInvitationApplicationService
import com.pios.networkmanagement.application.CreateConnectionApplicationService
import com.pios.networkmanagement.application.CreateInvitationApplicationService
import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.application.CreatePersonProfileApplicationService
import com.pios.networkmanagement.application.RetrievePersonConnectionsHandler
import com.pios.networkmanagement.application.RetrievePersonHandler
import com.pios.networkmanagement.application.RetrievePersonProfilesHandler
import com.pios.networkmanagement.domain.ConnectionType
import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import com.pios.networkmanagement.persistence.InMemoryConnectionRepository
import com.pios.networkmanagement.persistence.InMemoryInvitationRepository
import com.pios.networkmanagement.persistence.InMemoryPersonProfileRepository
import com.pios.networkmanagement.persistence.InMemoryPersonRepository
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Full MVC + filter checks, including malicious request bodies. No production database. */
class NetworkEndpointSecurityTest {
    private val secret = Base64.getEncoder().encodeToString("network-d12-test-secret-0123456789".toByteArray())
    private val json = jacksonObjectMapper()
    private val persons = InMemoryPersonRepository()
    private val profiles = InMemoryPersonProfileRepository()
    private val connections = InMemoryConnectionRepository()
    private val invitations = InMemoryInvitationRepository()
    private val createPerson = CreatePersonApplicationService(persons)
    private val current = CurrentPerson(persons)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(
        PersonController(createPerson, RetrievePersonHandler(persons), RetrievePersonProfilesHandler(profiles),
            RetrievePersonConnectionsHandler(connections), current),
        ProfileController(CreatePersonProfileApplicationService(persons, profiles), current),
        ConnectionController(CreateConnectionApplicationService(persons, connections), current),
        InvitationController(CreateInvitationApplicationService(persons, invitations),
            AcceptInvitationApplicationService(invitations, persons, connections), current, invitations)
    ).setMessageConverters(MappingJackson2HttpMessageConverter(json))
        .addFilters<StandaloneMockMvcBuilder>(NetworkSessionFilter(SessionTokenVerifier(secret))).build()

    private fun token(sub: String, guest: Boolean = false, generation: Int = 0): String {
        val payload = json.writeValueAsBytes(mapOf("sub" to sub, "drv" to null,
            "gst" to guest, "sgen" to generation, "exp" to Instant.now().plusSeconds(3600).epochSecond))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
        }
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encoded.toByteArray()))
        return "Bearer $encoded.$signature"
    }

    private fun call(method: String, path: String, body: String? = null, authorization: String? = null): MockHttpServletResponse {
        val builder = request(HttpMethod.valueOf(method), path)
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body)
        if (authorization != null) builder.header("Authorization", authorization)
        return mvc.perform(builder).andReturn().response
    }

    private fun person(identity: String, name: String): Person =
        createPerson.handle(CreatePersonCommand(name, null, identity))

    @Test
    fun `every endpoint rejects anonymous and invalid Bearer before parsing body`() {
        val a = person("identity-a", "Artur")
        val paths = listOf(
            "POST" to "/v1/persons", "GET" to "/v1/persons/${a.id.value}",
            "GET" to "/v1/persons/${a.id.value}/profiles",
            "GET" to "/v1/persons/${a.id.value}/connections",
            "POST" to "/v1/profiles", "POST" to "/v1/connections",
            "POST" to "/v1/invitations", "POST" to "/v1/invitations/UNKNOWN/accept"
        )
        for ((method, path) in paths) {
            assertEquals(401, call(method, path, "broken-json").status, "$method $path anonymous")
            assertEquals(401, call(method, path, "broken-json", "Bearer forged").status, "$method $path invalid")
        }
    }

    @Test
    fun `valid caller completes all eight endpoint flows`() {
        val a = person("identity-a", "Artur")
        val b = person("identity-b", "Regina")
        val bearerA = token("identity-a")
        val bearerB = token("identity-b")
        assertEquals(200, call("POST", "/v1/persons", """{"name":"Other"}""", bearerA).status)
        assertEquals(200, call("GET", "/v1/persons/${a.id.value}", authorization = bearerA).status)
        assertEquals(200, call("GET", "/v1/persons/${a.id.value}/profiles", authorization = bearerA).status)
        assertEquals(200, call("GET", "/v1/persons/${a.id.value}/connections", authorization = bearerA).status)
        assertEquals(201, call("POST", "/v1/profiles", """{"type":"DRIVER"}""", bearerA).status)
        assertEquals(201, call("POST", "/v1/connections",
            """{"toPersonId":"${b.id.value}","type":"CONNECTED"}""", bearerA).status)
        val invitation = call("POST", "/v1/invitations", "{}", bearerA)
        assertEquals(201, invitation.status)
        val code = json.readTree(invitation.contentAsString).get("code").asText()
        assertEquals(200, call("POST", "/v1/invitations/$code/accept", "{}", bearerB).status)
        assertEquals(2, connections.findByFromPerson(a.id).size)
    }

    @Test
    fun `path substitution is forbidden on person and both child reads`() {
        val b = person("identity-b", "Regina")
        person("identity-a", "Artur")
        for (path in listOf("/v1/persons/${b.id.value}",
            "/v1/persons/${b.id.value}/profiles", "/v1/persons/${b.id.value}/connections")) {
            assertEquals(403, call("GET", path, authorization = token("identity-a")).status, path)
        }
    }

    @Test
    fun `four mutation bodies cannot substitute caller Person`() {
        val a = person("identity-a", "Artur")
        val b = person("identity-b", "Regina")
        val bearerA = token("identity-a")
        val code = json.readTree(call("POST", "/v1/invitations", "{}", token("identity-b")).contentAsString)
            .get("code").asText()
        val attempts = listOf(
            "/v1/profiles" to """{"personId":"${b.id.value}","type":"DRIVER"}""",
            "/v1/connections" to """{"fromPersonId":"${b.id.value}","toPersonId":"${a.id.value}","type":"CONNECTED"}""",
            "/v1/invitations" to """{"creatorPersonId":"${b.id.value}"}""",
            "/v1/invitations/$code/accept" to """{"personId":"${b.id.value}"}"""
        )
        for ((path, body) in attempts) assertEquals(403, call("POST", path, body, bearerA).status, path)
        assertTrue(profiles.findByPersonId(b.id).isEmpty())
        assertTrue(connections.findByFromPerson(b.id).isEmpty())
        assertNotEquals("USED", invitations.findByCode(code)?.status?.name)
    }

    @Test
    fun `person creation ignores injected identity and remains one-to-one`() {
        val body = """{"name":"Artur","phone":"+7900","identityId":"identity-b"}"""
        assertEquals(403, call("POST", "/v1/persons", body, token("identity-a")).status)
        val created = call("POST", "/v1/persons", """{"name":"Artur","phone":"+7900"}""", token("identity-a"))
        assertEquals(201, created.status)
        val a = assertNotNull(persons.findByIdentityId("identity-a"))
        assertNull(persons.findByIdentityId("identity-b"))
        assertFalse(created.contentAsString.contains("phone"))
        assertFalse(created.contentAsString.contains("+7900"))
        assertFalse(created.contentAsString.contains("identityId"))
        assertEquals(200, call("POST", "/v1/persons", """{"name":"Other"}""", token("identity-a")).status)
        assertEquals(a.id, persons.findByIdentityId("identity-a")?.id)
    }

    @Test
    fun `unbound identity and quarantined Persons fail closed`() {
        val a = person("identity-a", "Artur")
        val legacy = Person(PersonId("legacy-id"), "Legacy", "+7999", Instant.now())
        persons.save(legacy)
        assertEquals(403, call("GET", "/v1/persons/${a.id.value}", authorization = token("unbound")).status)
        assertEquals(403, call("GET", "/v1/persons/${legacy.id.value}", authorization = token("identity-a")).status)
        assertEquals(404, call("POST", "/v1/connections",
            """{"toPersonId":"${legacy.id.value}","type":"CONNECTED"}""", token("identity-a")).status)
        assertEquals(403, call("POST", "/v1/profiles", """{"type":"DRIVER"}""", token("unbound")).status)
    }

    @Test
    fun `guest upgrade and recovery preserve the same Person binding`() {
        val created = call("POST", "/v1/persons", """{"name":"Guest"}""", token("guest-id", guest = true))
        assertEquals(201, created.status)
        val id = json.readTree(created.contentAsString).get("id").asText()
        assertEquals(200, call("GET", "/v1/persons/$id", authorization = token("guest-id")).status)
        assertEquals(200, call("POST", "/v1/persons", """{"name":"Changed"}""",
            token("guest-id", generation = 1)).status)
        assertEquals(id, persons.findByIdentityId("guest-id")?.id?.value)
        assertEquals(403, call("GET", "/v1/persons/$id", authorization = token("other-id")).status)
    }

    @Test
    fun `all network responses omit phone and authentication data`() {
        val a = person("identity-a", "Artur")
        val b = person("identity-b", "Regina")
        val bearerA = token("identity-a")
        val bearerB = token("identity-b")
        val profile = call("POST", "/v1/profiles", """{"type":"DRIVER"}""", bearerA)
        val connection = call("POST", "/v1/connections",
            """{"toPersonId":"${b.id.value}","type":"CONNECTED"}""", bearerA)
        val invitation = call("POST", "/v1/invitations", "{}", bearerA)
        val code = json.readTree(invitation.contentAsString).get("code").asText()
        val accepted = call("POST", "/v1/invitations/$code/accept", "{}", bearerB)
        val responses = listOf(
            call("GET", "/v1/persons/${a.id.value}", authorization = bearerA),
            call("GET", "/v1/persons/${a.id.value}/profiles", authorization = bearerA),
            call("GET", "/v1/persons/${a.id.value}/connections", authorization = bearerA),
            profile, connection, invitation, accepted
        )
        for (response in responses) {
            assertTrue(response.status in 200..299)
            for (field in listOf("phone", "identityId", "identityBoundAt", "credential", "otp", "sessionGeneration"))
                assertFalse(response.contentAsString.contains(field, ignoreCase = true), field)
        }
    }
}
