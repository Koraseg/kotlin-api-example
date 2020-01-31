package task

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.http.*
import io.ktor.jackson.jackson
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import task.service.JdbcBankService
import java.lang.RuntimeException
import java.time.LocalDateTime

fun accountInfoFromGetParams(params: Parameters): AccountInfoRequest? = tryOrNull {
    params["accountId"]?.let { AccountInfoRequest(java.lang.Long.parseLong(it))}
}

fun transactionsInfoFromGetParams(params: Parameters): TransactionsInfoRequest? = tryOrNull {
    val accountId = params["accountId"]?.let { java.lang.Long.parseLong(it) }
        ?: throw RuntimeException("Parsing error (will be swallowed because of throw inside tryOrNull scope)")
    val startPeriod = params["startPeriod"]
        ?.let{ LocalDateTime.parse(it, dateToLocalDateTimeFormatter) }

    val endPeriod = params["endPeriod"]
        ?.let{ LocalDateTime.parse(it, dateToLocalDateTimeFormatter) }

    val limit = params["limit"]
        ?.let{ java.lang.Integer.parseInt(it) }
    TransactionsInfoRequest(accountId, startPeriod, endPeriod, limit)
}

fun main(args: Array<String>) {
    val service =
      JdbcBankService("org.h2.Driver", "jdbc:h2:mem:test_mem",
            "test", "", 4).apply {
            setup()
          println("The service is ready!")
      }
    val port: Int = System.getProperty("server.port")?.let { Integer.parseInt(it) } ?: 8080

    val server = embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
        routing {
            get("/accountInfo") {
                sendResponseToRequestWithParameters(::accountInfoFromGetParams, service::accountInfo) { it }
            }

            get("/transactionsInfo") {
                sendResponseToRequestWithParameters(::transactionsInfoFromGetParams, service::transactionsInfo) { it }
            }

            post("/createAccount") {
                sendResponseToRequestWithJson(service::createAccount) {
                    mapOf("accountId" to it)
                }
            }

            delete("/closeAccount") {
                sendResponseToRequestWithJson(service::closeAccount) {
                    emptyMap<String, String>()
                }
            }

            post("/transfer") {
                sendResponseToRequestWithJson(service::transferFunds) {
                    mapOf("transactionId" to it)
                }
            }

            post("/withdraw") {
                sendResponseToRequestWithJson(service::withdraw) {
                    mapOf("balance" to it)
                }
            }

            post("/deposit") {
                sendResponseToRequestWithJson(service::deposit) {
                    mapOf("balance" to it)
                }
            }
        }
    }
    server.start(wait = true)
}
