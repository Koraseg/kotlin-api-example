package task.service

import arrow.core.Either
import arrow.core.NonEmptyList
import task.*

typealias AccountId = Long
typealias TransactionId = Long
typealias AccountBalance = Double

sealed class BankServiceException : Throwable()

class NotExistingAccountsException(private val accountIds: NonEmptyList<Long>): BankServiceException() {

    constructor(one: Long) : this(NonEmptyList.just(one))
    constructor(many: List<Long>) : this(NonEmptyList.fromListUnsafe(many))

    override val message: String
        get() = "Account ids ${accountIds.toList().joinToString(prefix = "[", separator = ",", postfix = "]")} do not exist."
}

class ExceedingBalanceException(private val requestedSum: Double, private val availableBalance: Double): BankServiceException() {
    override val message: String
        get() = "The requested sum [$requestedSum] is greater than available balance [$availableBalance]."
}

/* placeholder for I/O exceptions (network, database, etc)
class ServiceUnavailableException(override val cause: Exception? = null): BankServiceException(){
    override val message: String
        get() = "The service is currently unavailable"
}
 */


interface BankService: LocalDateTimeProvider {
    fun createAccount(createAccountRequest: CreateAccountRequest): Either<BankServiceException, AccountId>
    fun closeAccount(closeAccountRequest: CloseAccountRequest): Either<BankServiceException, Unit>
    fun accountInfo(accountInfoRequest: AccountInfoRequest): Either<BankServiceException, Account>
    fun transferFunds(transferRequest: TransferRequest): Either<BankServiceException, TransactionId>
    fun transactionsInfo(transactionsRequest: TransactionsInfoRequest): Either<BankServiceException, List<Transaction>>
    fun deposit(depositRequest: DepositRequest): Either<BankServiceException, AccountBalance>
    fun withdraw(withdrawalRequest: WithdrawalRequest): Either<BankServiceException, AccountBalance>
}

interface BankServiceWithLifeCycle: BankService {
    fun setup()
    fun resetState()
}


