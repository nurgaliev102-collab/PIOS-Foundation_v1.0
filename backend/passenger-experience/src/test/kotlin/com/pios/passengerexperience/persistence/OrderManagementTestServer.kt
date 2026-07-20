package com.pios.passengerexperience.persistence

import com.pios.ordermanagement.api.OrderSubmissionController
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * Test-only, REST-transport-only web configuration hosting Order
 * Management's real, unmodified [OrderSubmissionController] (Tranche 2:
 * Passenger Experience REST Transport), backed by an in-memory
 * [InMemoryOrderRepository] -- no database, no RabbitMQ. Deliberately does
 * not start order-management's full production application: that
 * application's own RabbitMQ consumer wiring
 * (`AssignmentAcceptedListener`, bound to the real, shared
 * `order-management.from-dispatch` queue) would otherwise compete, as a
 * second consumer on the real broker, with order-management's own
 * consumer integration tests whenever Gradle runs both modules' test
 * tasks in parallel (`org.gradle.parallel=true`, gradle.properties) --
 * this narrower server avoids that collision entirely while still
 * exercising the genuine controller class over real HTTP.
 */
@Configuration
@EnableWebMvc
class TestOrderSubmissionWebConfiguration {

    @Bean
    fun tomcatServletWebServerFactory(): TomcatServletWebServerFactory = TomcatServletWebServerFactory(0)

    @Bean
    fun dispatcherServlet(): DispatcherServlet = DispatcherServlet()

    @Bean
    fun dispatcherServletRegistration(dispatcherServlet: DispatcherServlet): ServletRegistrationBean<DispatcherServlet> =
        ServletRegistrationBean(dispatcherServlet, "/*")

    @Bean
    fun orderSubmissionController(): OrderSubmissionController =
        OrderSubmissionController(OrderSubmissionRequestHandler(OrderLifecycleApplicationService(InMemoryOrderRepository())))
}

/**
 * Test-only access to a real, separately-started embedded server hosting
 * [OrderSubmissionController], on a random port, once per test JVM --
 * mirroring `PostgreSQLTestDatabase`'s own `by lazy` singleton convention
 * in order-management's test source. This is what lets
 * [RestClientOrderSubmissionClient]'s tests exercise the genuine
 * controller class, not a mock or hand-rolled substitute.
 */
internal object OrderManagementTestServer {
    val baseUrl: String by lazy {
        val context = AnnotationConfigServletWebServerApplicationContext(TestOrderSubmissionWebConfiguration::class.java)
        "http://127.0.0.1:${context.webServer.port}"
    }
}
