package task.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.mchange.v2.c3p0.ComboPooledDataSource
import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.database.TransactionIsolation
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.entity.findById
import me.liuwj.ktorm.entity.findList
import task.*
import task.db.Accounts
import task.db.DDL
import task.db.Transactions
import java.lang.AssertionError
import java.time.LocalDateTime


class JdbcBankService(private val jdbcDriver: String,
                      private val jdbcUrl: String,
                      private val jdbcUser: String,
                      private val jdbcPassword: String,
                      private val poolSize: Int,
                      private val dateTimeProvider: LocalDateTimeProvider = LocalDateTimeProvider.CurrentUTC)
    : BankServiceWithLifeCycle, LocalDateTimeProvider by dateTimeProvider {

    private lateinit var db: Database

    override fun createAccount(createAccountRequest: CreateAccountRequest): Either<BankServiceException, AccountId> =
        try {
            (Accounts.insertAndGenerateKey {
                it.firstName to createAccountRequest.firstName
                it.secondName to createAccountRequest.secondName
                it.registeredAt to getCurrentLocalDateTime()
                it.balance to createAccountRequest.initialBalance
            } as Long).right()
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }



    override fun closeAccount(closeAccountRequest: CloseAccountRequest): Either<BankServiceException, Unit> =
        when(Accounts.delete { it.id eq closeAccountRequest.accountId }) {
            1 -> Unit.right()
            0 -> NotExistingAccountsException(closeAccountRequest.accountId).left()
            else -> throw AssertionError("Unreachable state")
        }


    override fun accountInfo(accountInfoRequest: AccountInfoRequest): Either<BankServiceException, Account> =
        Accounts.findById(accountInfoRequest.accountId)?.right()
            ?: NotExistingAccountsException(accountInfoRequest.accountId).left()

    override fun transferFunds(transferRequest: TransferRequest): Either<BankServiceException, TransactionId> =
        db.useTransaction(isolation = TransactionIsolation.REPEATABLE_READ) { transaction ->
            val accounts = Accounts.findList { (it.id eq transferRequest.from) or (it.id eq transferRequest.to) }
            val sender = accounts.find { it.id == transferRequest.from }
            val recipient = accounts.find { it.id == transferRequest.to }
            if (sender != null && recipient != null) {
                if (sender.balance >= transferRequest.sum) {
                    Accounts.update {
                        it.balance to (it.balance - transferRequest.sum)
                        where {
                            it.id eq sender.id
                        }
                    }
                    Accounts.update {
                        it.balance to (it.balance + transferRequest.sum)
                        where {
                            it.id eq recipient.id
                        }
                    }

                    val tk = Transactions.insertAndGenerateKey {
                        it.from to sender.id
                        it.to to recipient.id
                        it.time to getCurrentLocalDateTime()
                        it.sum to transferRequest.sum
                        it.type to TransactionType.TRANSFER.code
                    }
                    tk.right().map { it as Long }
                } else {
                    transaction.rollback()
                    ExceedingBalanceException(transferRequest.sum, sender.balance).left()
                }
            } else {
                transaction.rollback()
                val notFoundIds = mutableSetOf(transferRequest.from, transferRequest.to).apply { removeAll(accounts.map { it.id }) }
                NotExistingAccountsException(notFoundIds.toList()).left()
            }
        }


    override fun transactionsInfo(transactionsRequest: TransactionsInfoRequest): Either<BankServiceException, List<Transaction>> {
        val dateTimeRange = (transactionsRequest.startPeriod ?: LocalDateTime.MIN)..((transactionsRequest.endPeriod ?: LocalDateTime.MAX))
        return Transactions.findList {
            ((it.from eq transactionsRequest.accountId) or (it.to eq transactionsRequest.accountId))
        }.filter { it.time in dateTimeRange }.run { if (transactionsRequest.limit != null) take(transactionsRequest.limit) else  this}.right()
    }

    override fun deposit(depositRequest: DepositRequest): Either<BankServiceException, AccountBalance> =
        db.useTransaction(isolation = TransactionIsolation.REPEATABLE_READ) { _ ->
            Accounts.findById(depositRequest.accountId)?.right()?.flatMap { account ->
                val updatedBalance = account.balance + depositRequest.sum
                Accounts.update {
                    it.balance to updatedBalance
                    where {
                        it.id eq account.id
                    }
                }
                Transactions.insertAndGenerateKey {
                    it.from to null
                    it.to to depositRequest.accountId
                    it.time to getCurrentLocalDateTime()
                    it.sum to depositRequest.sum
                    it.type to TransactionType.DEPOSIT.code
                }
                updatedBalance.right()
            } ?: NotExistingAccountsException(depositRequest.accountId).left()
        }


    override fun withdraw(withdrawalRequest: WithdrawalRequest): Either<BankServiceException, AccountBalance> =
        db.useTransaction(isolation = TransactionIsolation.REPEATABLE_READ) { _ ->
            Accounts.findById(withdrawalRequest.accountId)?.right()?.flatMap { account ->
                val updatedBalance = account.balance - withdrawalRequest.sum
                if (updatedBalance >= 0) {
                    Accounts.update {
                        it.balance to updatedBalance
                        where {
                            it.id eq account.id
                        }
                    }
                    Transactions.insertAndGenerateKey {
                        it.to to null
                        it.from to withdrawalRequest.accountId
                        it.time to getCurrentLocalDateTime()
                        it.sum to withdrawalRequest.sum
                        it.type to TransactionType.WITHDRAW.code
                    }
                    updatedBalance.right()
                } else {
                    ExceedingBalanceException(requestedSum = withdrawalRequest.sum, availableBalance = account.balance).left()
                }

            } ?: NotExistingAccountsException(withdrawalRequest.accountId).left()
    }

    override fun setup() {
        db = with(ComboPooledDataSource()) {
            driverClass = jdbcDriver
            jdbcUrl = this@JdbcBankService.jdbcUrl
            user = this@JdbcBankService.jdbcUser
            password = this@JdbcBankService.jdbcPassword
            maxPoolSize = poolSize
            this
        }.let {
            Database.connect(it)
        }
        resetState()
    }

    override fun resetState() {
        db.useTransaction { transaction ->
            try {
                transaction.connection.createStatement().use { stm ->
                    stm.executeUpdate(DDL.createAccountsTable)
                    stm.executeUpdate(DDL.createTransactionsTable)
                }
            } catch (t: Throwable) {
                transaction.rollback()
                throw t
            }
            transaction.commit()
        }
    }

}
