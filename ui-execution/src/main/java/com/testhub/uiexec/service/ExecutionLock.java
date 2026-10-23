package com.testhub.uiexec.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Single JVM-wide lock serialising task state transitions, queue dispatch and
 * callbacks. V1 is a single instance; a distributed lock would replace this if
 * ui-execution were scaled out.
 */
@Component
public class ExecutionLock {

    private final ReentrantLock lock = new ReentrantLock();

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
