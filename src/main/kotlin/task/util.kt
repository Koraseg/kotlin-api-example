package task

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import task.service.BankServiceException
import task.service.ExceedingBalanceException
import task.service.NotExistingAccountsException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

fun <U> tryOrNull(block: () -> U): U? = try {
    block()
} catch (e: Exception) {
    null
}

fun <T> Either<BankServiceException, T>.getOrThrow(): T = getOrHandle { throw it }
/*
/**
 * workaround to process jackson parsing errors in coroutine context
 */
@UseExperimental(ExperimentalStdlibApi::class)
suspend inline fun <reified T : Any> ApplicationCall.receiveOrNullSafe(): T? = try {
    receiveOrNull()
} catch (e: Exception) {
    e.printStackTrace()
    null
}*/

val dateToLocalDateTimeFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd")
    .optionalStart()
    .appendPattern(" HH:mm:ss")
    .optionalEnd()
    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
    .parseDefaulting(ChronoField.SECOND_OF_DAY, 0)
    .toFormatter()



suspend inline fun <reified Req : ApiRequest, Resp> PipelineContext<Unit, ApplicationCall>.sendResponseToRequestWithJson(
    crossinline requestProcessor: (Req) -> Either<BankServiceException, Resp>,
    respToMsg: (Resp) -> Any

) {

    // still have to catch jackson parsing exceptions
    val req = try {
        call.receiveOrNull<Req>()
    } catch (t: Exception) {
        t.printStackTrace()
        null
    }

    if (req != null) {
        val resp = withContext(Dispatchers.IO) {
            requestProcessor(req)
        }
        when(resp) {
            is Either.Right -> call.respond(HttpStatusCode.OK, respToMsg(resp.b))
            is Either.Left ->
                when(resp.a) {
                    is ExceedingBalanceException ->
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to resp.a.message))
                    is NotExistingAccountsException ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to resp.a.message))
                }
        }
    } else {
        call.respond(HttpStatusCode.UnsupportedMediaType, mapOf("error" to "Request body parsing error"))
    }
}

suspend inline fun <reified Req : ApiRequest, Resp> PipelineContext<Unit, ApplicationCall>.sendResponseToRequestWithParameters(
    crossinline requestParser: (Parameters) -> Req?,
    crossinline requestProcessor: (Req) -> Either<BankServiceException, Resp>,
    respToMsg: (Resp) -> Any
) {

    val req = requestParser(call.request.queryParameters)
    if (req != null) {
        val resp = withContext(Dispatchers.IO) {
            requestProcessor(req)
        }
        when(resp) {
            is Either.Right -> call.respond(HttpStatusCode.OK, respToMsg(resp.b))
            is Either.Left ->
                when(resp.a) {
                    is ExceedingBalanceException ->
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to resp.a.message))
                    is NotExistingAccountsException ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to resp.a.message))
                }
        }
    } else {
        call.respond(HttpStatusCode.UnsupportedMediaType, mapOf("error" to "Request body parsing error"))
    }
}
