package task

import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.matchers.doubles.shouldBeLessThan
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import task.service.BankServiceWithLifeCycle
import task.service.JdbcBankService
import kotlin.math.abs

class ParallelTransactionsSpec : StringSpec() {
    private val service: BankServiceWithLifeCycle =
        JdbcBankService("org.h2.Driver", "jdbc:h2:mem:test_mem", "test", "", 4)

    private val acceptableError: Double = 1e-4
    private val initialBalance: Double = 10000.0
    private val usersNumber = 50

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        service.setup()
    }
    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        service.resetState()
    }


    init {

        "Parallel transactions - Secret Santa on steroids (not fair though)" {
            val accountIds = createAccounts()

            // transfer half of funds to other guy
            accountIds.map { accId ->
                GlobalScope.launch {
                    val availableBalance = service.accountInfo(AccountInfoRequest(accId)).getOrThrow().balance
                    val randomFriend = accountIds.filterNot { it == accId }.shuffled()[0]
                    service.transferFunds(TransferRequest(accId, randomFriend, availableBalance / 2))
                }
            }.forEach { it.join() }

            val moneyInSystem = accountIds.map { accId ->
                service.accountInfo(AccountInfoRequest(accId)).getOrThrow().balance
            }.sum()

            abs(moneyInSystem - usersNumber * initialBalance) shouldBeLessThan acceptableError
        }

        "Parallel transactions - one to many version" {
            val accountIds = createAccounts()
            val sponsor = accountIds[0]
            val beneficiaries = accountIds.subList(1, accountIds.size)
            val parallelism = 50
            (1..parallelism).map { _ ->
                GlobalScope.launch {
                    val availableBalance = service.accountInfo(AccountInfoRequest(sponsor)).getOrThrow().balance
                    val randomBeneficiary = beneficiaries.shuffled()[0]
                    service.transferFunds(TransferRequest(sponsor, randomBeneficiary, availableBalance / parallelism))
                }
            }.forEach { it.join() }

            val moneyInSystem = accountIds.map { accId ->
                service.accountInfo(AccountInfoRequest(accId)).getOrThrow().balance
            }.sum()

            abs(moneyInSystem - usersNumber * initialBalance) shouldBeLessThan acceptableError
        }
    }
    private fun createAccounts(): List<Long> =
        (1..usersNumber).map { service.createAccount(dummyAccountRequest()).getOrThrow()}


    private fun dummyAccountRequest(): CreateAccountRequest =
        CreateAccountRequest("Irrelevant", "thing", initialBalance)

}
