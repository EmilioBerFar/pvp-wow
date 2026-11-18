package com.pvpwow.lfg.adapters.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * The read-only transaction, in one place.
 *
 * <p>WHY IT EXISTS: CSRF is disabled in this application, and the reason is
 * that the session cookie is {@code SameSite=Lax} and no state-changing route
 * is a GET. The GET half of that promise is checked structurally
 * ({@code GetEndpointsDoNotChangeStateTest}). This is the RUNTIME half: the
 * read paths run inside a transaction PostgreSQL opens as {@code READ ONLY},
 * and the database itself refuses an explicit write with
 * {@code cannot execute INSERT in a read-only transaction}.
 *
 * <p>The two halves cover different mistakes. The structural rule cannot
 * follow a call through a service into a repository and know what happens
 * there; the transaction cannot see a write that never reaches the database
 * as a statement. Together: the structure says "this should not write" and
 * the database says "it will not".
 *
 * <p>WHY A CLASS AND NOT AN ANNOTATION: the adapters are {@code final} by
 * convention, and {@code @Transactional} needs a proxy to work. The
 * programmatic template keeps the boundary visible and works on a final class
 * - the same reason the writing adapters already use one.
 *
 * <p>WHY IT MATTERS FOR THE BOARD SPECIFICALLY: the board query runs FIVE
 * statements. Without a transaction they run in auto-commit, each in its own
 * snapshot, so two of them can see two different versions of the database. A
 * read-only transaction gives them one consistent view AND refuses the
 * accidental write.
 */
@Component
final class ReadOnlyTransactions {

    private final TransactionTemplate reads;

    ReadOnlyTransactions(PlatformTransactionManager transactionManager) {
        this.reads = new TransactionTemplate(transactionManager);
        // The flag that makes PostgreSQL open the transaction as READ ONLY.
        // The PostgreSQL JDBC driver's default readOnlyMode is "transaction",
        // so this reaches the database as BEGIN READ ONLY, and a write
        // statement inside it is rejected by the database itself.
        this.reads.setReadOnly(true);
    }

    /**
     * Runs the given work inside a read-only transaction.
     */
    <T> T read(Supplier<T> work) {
        return reads.execute(status -> work.get());
    }

    /**
     * Runs the given work inside a read-only transaction.
     */
    void read(Runnable work) {
        reads.executeWithoutResult(status -> work.run());
    }
}
