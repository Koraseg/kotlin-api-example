package task.db

import me.liuwj.ktorm.dsl.QueryRowSet
import me.liuwj.ktorm.schema.*
import task.Account
import task.Transaction
import task.TransactionType
import java.lang.RuntimeException
import java.time.LocalDateTime

object Accounts : BaseTable<Account>("t_accounts") {
    val id by long("id").primaryKey()
    val firstName by varchar("first_name")
    val secondName by varchar("second_name")
    val registeredAt by datetime("registered_at")
    val balance by double("balance")


    override fun doCreateEntity(row: QueryRowSet, withReferences: Boolean): Account = Account(
        id = row[id] ?: -1L,
        firstName = row[firstName] ?: "",
        secondName = row[secondName] ?: "",
        registeredAt = row[registeredAt] ?: LocalDateTime.now(),
        balance = row[balance] ?: 0.0
    )
}

object Transactions : BaseTable<Transaction>("t_transactions") {
    val id by long("id").primaryKey()
    val from by long("sender_id")
    val to  by long("recipient_id")
    val time by datetime("transaction_time")
    val sum by double("sum")
    val type by int("type")

    override fun doCreateEntity(row: QueryRowSet, withReferences: Boolean) = Transaction(
        id = row[id] ?: -1L,
        senderId = row[from],
        recipientId = row[to],
        time = row[time] ?: LocalDateTime.now(),
        sum = row[sum] ?: 0.0,
        type = when(row[type]) {
            0 -> TransactionType.TRANSFER
            -1 -> TransactionType.WITHDRAW
            1 -> TransactionType.DEPOSIT
            else -> throw RuntimeException("Unexpected row format.")
        }
    )
}
