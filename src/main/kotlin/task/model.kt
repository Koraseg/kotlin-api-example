package task

import java.time.LocalDateTime

object ErrorMessages {
    const val negativeBalance = "An account must have non-negative balance."
    const val nonPositiveValueInRequest = "The sum in any request must always be greater than zero."
    const val selfTransaction = "Transfers from clients to themselves are forbidden."
}


data class Account(val id: Long,
                   val firstName: String,
                   val secondName: String,
                   val registeredAt: LocalDateTime,
                   val balance: Double
): Comparable<Account> {
    init {
        require(balance >= 0) {
            ErrorMessages.negativeBalance
        }
    }

    constructor(req: CreateAccountRequest, id: Long, registeredAt: LocalDateTime) :
        this(id, req.firstName, req.secondName, registeredAt, req.initialBalance)

    override fun compareTo(other: Account): Int = id.compareTo(other.id)

}

enum class TransactionType(val code: Int) {
    WITHDRAW(-1), DEPOSIT(1), TRANSFER(0)
}

data class Transaction(val id: Long,
                       val senderId: Long?,
                       val recipientId: Long?,
                       val time: LocalDateTime,
                       val sum: Double,
                       val type: TransactionType
)

sealed class ApiRequest

data class CreateAccountRequest(val firstName: String, val secondName: String, val initialBalance: Double = 0.0): ApiRequest()
{
    init {
        require(initialBalance >= 0) {
            ErrorMessages.negativeBalance
        }
    }
}
data class CloseAccountRequest(val accountId: Long): ApiRequest()


data class AccountInfoRequest(val accountId: Long): ApiRequest()
data class DepositRequest(val accountId: Long, val sum: Double): ApiRequest() {
    init {
        require(sum > 0) {
            ErrorMessages.nonPositiveValueInRequest
        }
    }
}

data class WithdrawalRequest(val accountId: Long, val sum: Double): ApiRequest() {
    init {
        require(sum > 0) {
            ErrorMessages.nonPositiveValueInRequest
        }
    }
}

data class TransferRequest(val from: Long, val to: Long, val sum: Double): ApiRequest() {
    init {
        require(sum > 0) {
            ErrorMessages.nonPositiveValueInRequest
        }
        require(from != to) {
            ErrorMessages.selfTransaction
        }
    }
}

data class TransactionsInfoRequest(val accountId: Long,
                                   val startPeriod: LocalDateTime? = null,
                                   val endPeriod: LocalDateTime? = null,
                                   val limit: Int? = null): ApiRequest()

