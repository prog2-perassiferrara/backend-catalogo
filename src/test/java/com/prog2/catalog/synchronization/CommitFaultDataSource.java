package com.prog2.catalog.synchronization;

import java.lang.reflect.*;
import java.sql.*;
import java.util.Locale;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Frontera JDBC de prueba: provoca un timeout real del driver durante un commit
 * elegido, antes o después de confirmar. No sustituye Spring, JPA ni MySQL.
 */
final class CommitFaultDataSource extends DelegatingDataSource {
    enum Moment { BEFORE_COMMIT, AFTER_COMMIT, DURING_STATEMENT }
    private record Fault(String sqlFragment, Moment moment) { }
    private final AtomicReference<Fault> armed = new AtomicReference<>();
    private final AtomicInteger triggered = new AtomicInteger();
    private final AtomicInteger writeCommits = new AtomicInteger();

    CommitFaultDataSource(DataSource delegate) { super(delegate); }

    void arm(String sqlFragment, Moment moment) {
        if (!armed.compareAndSet(null, new Fault(sqlFragment, moment))) {
            throw new IllegalStateException("A fictional commit fault is already armed");
        }
    }

    void resetFault() { armed.set(null); triggered.set(0); writeCommits.set(0); }
    int triggeredFaults() { return triggered.get(); }
    int matchingWriteCommits() { return writeCommits.get(); }

    @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
    @Override public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, new CommitConnection(delegate));
    }

    private final class CommitConnection implements InvocationHandler {
        private final Connection delegate;
        private boolean matches;
        private CommitConnection(Connection delegate) { this.delegate = delegate; }

        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getName().equals("prepareStatement") && arguments != null && arguments[0] instanceof String sql) {
                prepare(sql);
            }
            if (method.getName().equals("commit")) { beforeCommit(); }
            if (method.getName().equals("rollback")) { matches = false; }
            try { return method.invoke(delegate, arguments); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }

        private void prepare(String sql) throws SQLException {
            Fault fault = armed.get();
            matches |= fault != null && sql.toLowerCase(Locale.ROOT).contains(fault.sqlFragment());
            if (matches && fault != null && fault.moment() == Moment.DURING_STATEMENT
                    && triggered.compareAndSet(0, 1)) {
                delegate.setNetworkTimeout(Runnable::run, 250);
            }
        }

        private void beforeCommit() throws SQLException {
            Fault fault = armed.get();
            if (matches) { writeCommits.incrementAndGet(); }
            if (matches && fault != null && triggered.compareAndSet(0, 1)) {
                if (fault.moment() == Moment.AFTER_COMMIT) { delegate.commit(); }
                // Solo esta conexión descartable espera menos que SLEEP. Hikari la invalida al fallar.
                delegate.setNetworkTimeout(Runnable::run, 250);
                try (Statement statement = delegate.createStatement()) {
                    statement.execute("SELECT SLEEP(1)");
                }
                throw new IllegalStateException("The fictional driver timeout did not occur");
            }
            matches = false;
        }
    }
}
