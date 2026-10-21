package com.shrtrl.services;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.shrtrl.DTOs.UpdateLongUrl;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.queries.UserQueries;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class OperationFailureTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void databaseFailuresAreReturnedDirectlyByServices() throws Exception {
        assertFailures(new DataAccessResourceFailureException("database unavailable"), "DatabaseFailure", null);
    }

    @Test
    void runtimeFailuresAreReturnedDirectlyByServices() throws Exception {
        assertFailures(new IllegalStateException("unexpected failure"), "RuntimeFailure", null);
    }

    @Test
    void caughtFailuresRollBackAllSixTransactions() throws Exception {
        CountingTransactions transactions = new CountingTransactions();
        assertFailures(new DataAccessResourceFailureException("database unavailable"), "DatabaseFailure", transactions);
        assertEquals(6, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    private void assertFailures(RuntimeException failure, String type,
                                CountingTransactions transactions) throws Exception {
        UserQueries users = mock(UserQueries.class, invocation -> { throw failure; });
        GroupQueries groups = mock(GroupQueries.class, invocation -> { throw failure; });
        UrlQueries urls = mock(UrlQueries.class, invocation -> { throw failure; });
        GroupService groupService = new GroupService(groups, users, urls);
        UrlService urlService = new UrlService(urls, users, groups);
        if (transactions != null) {
            groupService = transactional(groupService, transactions);
            urlService = transactional(urlService, transactions);
        }
        GroupService group = groupService;
        UrlService url = urlService;
        List<Supplier<Object>> operations = List.of(
                () -> group.createGroup(USER_ID, "workgroup"),
                () -> group.updateGroup(1, USER_ID, "workgroup"),
                () -> group.deleteGroup(1, USER_ID),
                () -> url.shortenurl(new UrlCreateRequest("https://example.com"), USER_ID),
                () -> url.updateLongurl("abc123", new UpdateLongUrl("https://example.com/new"), USER_ID),
                () -> url.deleteurl("abc123", USER_ID));
        for (Supplier<Object> operation : operations) {
            Object result = operation.get();
            assertEquals(type, result.getClass().getSimpleName());
            assertSame(failure, result.getClass().getMethod("cause").invoke(result));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target, CountingTransactions manager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private static class CountingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
