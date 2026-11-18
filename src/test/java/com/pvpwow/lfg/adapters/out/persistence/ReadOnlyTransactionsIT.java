package com.pvpwow.lfg.adapters.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL refuses the write, not us.
 *
 * <p>CSRF is disabled in this application, and the reason is that no
 * state-changing route is a GET. The structural half of that promise is
 * {@code GetEndpointsDoNotChangeStateTest}, which reads the bytecode and
 * answers "could this method possibly write?". This is the other half: the
 * read paths run inside a transaction the database opened as READ ONLY, and
 * the database itself refuses an explicit write.
 *
 * <p>The two halves cover different mistakes. The structural rule cannot
 * follow a call through a service into a repository and know what happens
 * there; the transaction cannot see a write that never reaches the database
 * as a statement. Together: the structure says "this should not write" and
 * the database says "it will not".
 *
 * <p>What this test pins down is the promise itself, and it pins it down
 * EMPIRICALLY: it asks the database to write inside a read-only transaction
 * and checks that the database says no. A flag that silently stopped reaching
 * the driver would leave the suite green and the promise broken, so the test
 * would have to fail for the wrong reason - which is why it asserts on the
 * database's own answer rather than on our flag.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ReadOnlyTransactionsIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReadOnlyTransactions reads;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from players");
    }

    @Test
    void aReadInsideTheReadOnlyTransactionSucceeds() {
        Integer answer = reads.read(() -> jdbc.queryForObject("select 1", Integer.class));

        assertThat(answer).isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesAWriteInsideTheReadOnlyTransaction() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> reads.read(() -> {
            jdbc.update("insert into players (id, battle_net_id, battle_tag) "
                    + "values (?, ?, ?)", playerId, "123456789", "emilio#12345");
            return null;
        }))
                .as("PostgreSQL opened the transaction as READ ONLY and said no")
                .hasMessageContaining("read-only transaction");
    }

    @Test
    void theWriteThatWasRefusedDidNotHappen() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> reads.read(() -> {
            jdbc.update("insert into players (id, battle_net_id, battle_tag) "
                    + "values (?, ?, ?)", playerId, "123456789", "emilio#12345");
            return null;
        })).isInstanceOf(Exception.class);

        assertThat(jdbc.queryForObject(
                "select count(*) from players where id = ?", Integer.class, playerId))
                .isZero();
    }

    @Test
    void aWriteOutsideTheReadOnlyTransactionStillWorks() {
        // The other half of the promise: read-only must not leak into the
        // writing paths. If this failed, the product could not save anything.
        UUID playerId = UUID.randomUUID();

        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");

        assertThat(jdbc.queryForObject(
                "select count(*) from players where id = ?", Integer.class, playerId))
                .isEqualTo(1);
    }
}
