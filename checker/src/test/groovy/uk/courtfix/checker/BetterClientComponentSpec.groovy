package uk.courtfix.checker

import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.Executors

class BetterClientComponentSpec extends Specification {
    HttpServer server
    String responseBody
    int responseStatus
    String requestPath
    String requestedWith

    def setup() {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/slots") { exchange ->
            requestPath = exchange.requestURI.toString()
            requestedWith = exchange.requestHeaders.getFirst("X-Requested-With")
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(responseStatus, bytes.length)
            exchange.responseBody.withCloseable { it.write(bytes) }
        }
        server.start()
    }

    def cleanup() {
        server?.stop(0)
        server?.executor?.shutdownNow()
    }

    def "requests a date and returns slots from the Better-shaped JSON response"() {
        given:
        responseStatus = 200
        responseBody = '''{"data":[{"id":"slot-1","location":{"id":"5147","name":"Court 1"},"date":{"raw":"2026-10-12"},"starts_at":{"format_24_hour":"09:00"},"ends_at":{"format_24_hour":"10:00"},"action_to_show":{"status":"BOOK"},"spaces":1,"price":{"formatted":"£10.00"}}]}'''
        def client = clientForServer()

        when:
        def slots = client.getSlots(LocalDate.parse("2026-10-12"))

        then:
        slots.size() == 1
        slots.first().courtId() == "5147"
        slots.first().isBookable()
        requestPath == "/slots?date=2026-10-12"
        requestedWith == "XMLHttpRequest"
    }

    def "explains when Better rejects a date outside its booking window"() {
        given:
        responseStatus = 422
        responseBody = "{}"

        when:
        clientForServer().getSlots(LocalDate.parse("2026-10-12"))

        then:
        def error = thrown(IOException)
        error.message.contains("outside the currently viewable booking window")
    }

    def "fails on other unsuccessful HTTP responses"() {
        given:
        responseStatus = 503
        responseBody = "unavailable"

        when:
        clientForServer().getSlots(LocalDate.parse("2026-10-12"))

        then:
        def error = thrown(IOException)
        error.message.contains("HTTP 503")
    }

    private BetterClient clientForServer() {
        def http = java.net.http.HttpClient.newHttpClient()
        new BetterClient(http, "http://127.0.0.1:${server.address.port}/slots?date=")
    }
}
