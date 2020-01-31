package task

import arrow.core.extensions.list.foldable.forAll
import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import task.service.*
import java.lang.IllegalArgumentException

class BasicFunctionalSpec() : StringSpec() {
    private val service: BankServiceWithLifeCycle =
        JdbcBankService("org.h2.Driver", "jdbc:h2:mem:test_mem", "test", "", 4)


    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        service.setup()
    }
    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        service.resetState()
    }


    init {
        "Successful transfer test" {
            val senderId = service
                .createAccount(CreateAccountRequest("A", "B", 100.0))
                .getOrThrow()
            val recipientId = service
                .createAccount(CreateAccountRequest("C", "D"))
                .getOrThrow()
            service.transferFunds(TransferRequest(senderId, recipientId, 40.0)).getOrThrow()

            service.accountInfo(AccountInfoRequest(senderId)).getOrThrow().balance shouldBe  60.0
            service.accountInfo(AccountInfoRequest(recipientId)).getOrThrow().balance shouldBe  40.0
        }

        "Constraints test" {
            val acc1Id = service
                .createAccount(CreateAccountRequest("A", "B", 100.0))
                .getOrThrow()
            val acc2Id = service
                .createAccount(CreateAccountRequest("C", "D", 100.0))
                .getOrThrow()
            shouldThrow<IllegalArgumentException> {
                service.createAccount(CreateAccountRequest("Client", "Unborn", -200.0))
            }

            shouldThrow<ExceedingBalanceException> {
                service.transferFunds(TransferRequest(from = acc1Id, to = acc2Id, sum = 200.0)).getOrThrow()
            }

            shouldThrow<ExceedingBalanceException> {
                service.withdraw(WithdrawalRequest(acc1Id, 120.0)).getOrThrow()
            }
            service.closeAccount(CloseAccountRequest(acc2Id)).getOrThrow() shouldBe Unit

            shouldThrow<NotExistingAccountsException> {
                service.transferFunds(TransferRequest(from = acc1Id, to = acc2Id, sum = 50.0)).getOrThrow()
            }

            // check balance, cash the funds out and close the account -> those operations must finish successfully
            service.accountInfo(AccountInfoRequest(acc1Id)).getOrThrow().balance shouldBe 100.0
            service.withdraw(WithdrawalRequest(acc1Id, 100.0)).getOrThrow() shouldBe 0.0
            service.closeAccount(CloseAccountRequest(acc1Id)).getOrThrow() shouldBe Unit
        }

        "Transactions info test" {
            val acc1Id = service
                .createAccount(CreateAccountRequest("A", "B", 150.0))
                .getOrThrow()
            val acc2Id = service
                .createAccount(CreateAccountRequest("C", "D", 100.0))
                .getOrThrow()

            // 1st transaction of acc1, 1st transaction of acc2
            service.transferFunds(TransferRequest(from = acc1Id, to = acc2Id, sum = 50.0))
            // 2nd transaction of acc1
            service.withdraw(WithdrawalRequest(acc1Id, 80.0))
            val timeLineBorder = service.getCurrentLocalDateTime()
            // 3rd transaction of acc1
            service.deposit(DepositRequest(acc1Id, 250.0))
            // 4th transaction of acc1, 2nd transaction of acc2
            service.transferFunds(TransferRequest(from = acc2Id, to = acc1Id, sum = 60.0))

            val acc1Transactions = service.transactionsInfo(TransactionsInfoRequest(acc1Id)).getOrThrow()
            val acc2Transactions = service.transactionsInfo(TransactionsInfoRequest(acc2Id)).getOrThrow()

            acc1Transactions.size shouldBe 4
            acc1Transactions.forAll { t -> t.senderId == acc1Id || t.recipientId == acc1Id } shouldBe true

            acc2Transactions.forAll { t -> t.senderId == acc2Id || t.recipientId == acc2Id } shouldBe true
            acc2Transactions.size shouldBe 2

            //check limit functional
            service.transactionsInfo(TransactionsInfoRequest(accountId = acc1Id, limit = 3)).getOrThrow().size shouldBe 3
            //check period bounds
            service.transactionsInfo(TransactionsInfoRequest(accountId = acc1Id,  startPeriod = timeLineBorder)).getOrThrow().size shouldBe 2
            service.transactionsInfo(TransactionsInfoRequest(accountId = acc2Id,  startPeriod = timeLineBorder)).getOrThrow().size shouldBe 1

            service.transactionsInfo(TransactionsInfoRequest(accountId = acc1Id,  endPeriod = timeLineBorder)).getOrThrow().size shouldBe 2
            service.transactionsInfo(TransactionsInfoRequest(accountId = acc2Id,  endPeriod = timeLineBorder)).getOrThrow().size shouldBe 1

        }
    }


}
