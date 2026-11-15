package com.tamdaptit.rentalmanagement.discovery;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Frozen ID order shared through Redis, with a bounded fallback during cache outages. */
@Service @RequiredArgsConstructor
public class RankingSessions {
    private final CacheManager cacheManager;
    private final Clock clock;
    private final Map<String,Snapshot> local = new LinkedHashMap<>();
    public record Snapshot(String id,String scope,List<String> ids,Map<String,Integer> priorities,Instant expiresAt) {}
    public synchronized Snapshot create(String scope,List<String> ids,Map<String,Integer> priorities) {
        local.values().removeIf(s -> !s.expiresAt().isAfter(clock.instant()));
        while(local.size()>=256)local.remove(local.keySet().iterator().next());
        var value=new Snapshot(UUID.randomUUID().toString(),scope,new ArrayList<>(ids),new HashMap<>(priorities),clock.instant().plusSeconds(1800));
        local.put(value.id(),value);
        cacheManager.getCache("rankingSessions").put(value.id(),value);
        return value;
    }
    public synchronized Snapshot get(String id,String scope) {
        Snapshot value=local.get(id);
        if(value==null)value=cacheManager.getCache("rankingSessions").get(id,Snapshot.class);
        if(value==null||!value.scope().equals(scope)||!value.expiresAt().isAfter(clock.instant()))
            throw new AppException(ErrorCode.FEED_SESSION_EXPIRED);
        return value;
    }
}
