package com.tamdaptit.rentalmanagement.notification;

import com.tamdaptit.rentalmanagement.common.cache.ResilientCache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResilientCacheTest {
    @Test void cacheReadAndDeferredEvictionFailureDoNotFailBusinessOperation(){
        var redis=mock(Cache.class);when(redis.getName()).thenReturn("catalog");
        when(redis.get("key")).thenThrow(new IllegalStateException("offline"));
        doThrow(new IllegalStateException("offline")).when(redis).clear();
        var cache=new TransactionAwareCacheDecorator(new ResilientCache(redis));
        assertNull(cache.get("key"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.clear();verify(redis,never()).clear();
            for(var synchronization:TransactionSynchronizationManager.getSynchronizations())
                assertDoesNotThrow(synchronization::afterCommit);
            verify(redis).clear();
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
