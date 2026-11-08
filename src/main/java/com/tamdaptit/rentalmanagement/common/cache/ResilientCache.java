package com.tamdaptit.rentalmanagement.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Cache failures must not turn committed business operations into errors. */
@RequiredArgsConstructor
@Slf4j
public class ResilientCache implements Cache {
    private final Cache delegate;
    public String getName() { return delegate.getName(); }
    public Object getNativeCache() { return delegate.getNativeCache(); }
    public ValueWrapper get(Object key) { return attempt(() -> delegate.get(key),null); }
    public <T> T get(Object key,Class<T> type) { return attempt(() -> delegate.get(key,type),null); }
    @SuppressWarnings("unchecked")
    public <T> T get(Object key,Callable<T> loader) {
        var cached = get(key);
        if (cached!=null) return (T)cached.get();
        try {
            T value = loader.call();
            put(key,value);
            return value;
        } catch (Exception ex) { throw new ValueRetrievalException(key,loader,ex); }
    }
    public void put(Object key,Object value) { attempt(() -> {delegate.put(key,value);return null;},null); }
    public ValueWrapper putIfAbsent(Object key,Object value) { return attempt(() -> delegate.putIfAbsent(key,value),null); }
    public void evict(Object key) { attempt(() -> {delegate.evict(key);return null;},null); }
    public boolean evictIfPresent(Object key) { return attempt(() -> delegate.evictIfPresent(key),false); }
    public void clear() { attempt(() -> {delegate.clear();return null;},null); }
    public boolean invalidate() { return attempt(delegate::invalidate,false); }
    private <T> T attempt(Supplier<T> action,T fallback) {
        try { return action.get(); }
        catch (RuntimeException ex) {
            log.warn("Cache {} unavailable; using database ({})",getName(),ex.getClass().getSimpleName());
            return fallback;
        }
    }
}
