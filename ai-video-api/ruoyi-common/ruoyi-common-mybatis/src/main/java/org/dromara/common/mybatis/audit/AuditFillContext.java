package org.dromara.common.mybatis.audit;

import org.dromara.common.core.exception.ServiceException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-bound app actor used by MyBatis audit filling.
 */
public final class AuditFillContext {

    private static final ThreadLocal<Deque<Entry>> CONTEXT = new ThreadLocal<>();

    private AuditFillContext() {
    }

    /**
     * Opens a nested app audit scope.
     *
     * @param actorId authenticated app user id
     * @return scope that restores the previous actor on close
     */
    public static Scope open(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ServiceException("app 审计主体无效");
        }
        Deque<Entry> entries = CONTEXT.get();
        if (entries == null) {
            entries = new ArrayDeque<>();
            CONTEXT.set(entries);
        }
        Entry entry = new Entry(actorId);
        entries.push(entry);
        return new Scope(entry);
    }

    /**
     * Returns the current app actor or fails closed when no scope is bound.
     */
    public static Long currentActorId() {
        Deque<Entry> entries = CONTEXT.get();
        if (entries == null || entries.isEmpty()) {
            throw new ServiceException("app 审计上下文未绑定");
        }
        return entries.peek().actorId();
    }

    /**
     * Returns whether an app audit scope is currently bound.
     */
    public static boolean isBound() {
        Deque<Entry> entries = CONTEXT.get();
        return entries != null && !entries.isEmpty();
    }

    private record Entry(Long actorId) {
    }

    /**
     * Nesting-safe context scope.
     */
    public static final class Scope implements AutoCloseable {
        private final Entry entry;
        private boolean closed;

        private Scope(Entry entry) {
            this.entry = entry;
        }

        @Override
        public void close() {
            if (closed) {
                throw new ServiceException("app 审计上下文重复关闭");
            }
            Deque<Entry> entries = CONTEXT.get();
            if (entries == null || entries.peek() != entry) {
                throw new ServiceException("app 审计上下文必须按嵌套顺序关闭");
            }
            entries.pop();
            closed = true;
            if (entries.isEmpty()) {
                CONTEXT.remove();
            }
        }
    }
}
