package task.db

object DDL {
    const val createAccountsTable = """
          DROP TABLE IF EXISTS t_accounts;
          CREATE TABLE t_accounts(
          id bigint not null primary key auto_increment,
          first_name varchar(128) not null,
          second_name varchar(128) not null,
          registered_at timestamp not null,
          balance double not null,
          CHECK (balance >= 0)
        );
    """

    const val createTransactionsTable = """
          DROP TABLE IF EXISTS t_transactions;
          CREATE TABLE t_transactions(
          id bigint not null primary key auto_increment,
          sender_id bigint,
          recipient_id bigint,
          transaction_time timestamp not null,
          sum double not null,
          type int not null
        );
        CREATE INDEX idx_sender on t_transactions (sender_id);
        CREATE INDEX idx_recipient on t_transactions (recipient_id);
    """


}
