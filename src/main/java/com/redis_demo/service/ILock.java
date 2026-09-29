package com.redis_demo.service;

public interface ILock {

    boolean tryLock(Long timeoutSec);

    void unlock();
}
