package com.tamdaptit.rentalmanagement.discovery;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ListingDiscoveryService {
    private final ListingService listings;
    private final FeedRanking ranking;
    private final RankingSessions sessions;
    public PageResponse<ListingResponse> search(String actor,int page,int size,ListingSearchRequest query,String sort,String session) {
        if(page<0||size<1||size>100)throw new AppException(ErrorCode.INVALID_REQUEST);
        validateSort(sort);
        var prefs=ranking.preferences(actor);
        String scope="listing-v1|"+Objects.toString(actor,"")+"|"+query+"|"+sort+"|"+size+"|"+prefs.key();
        var candidates=new ArrayList<>(listings.searchCandidates(query));
        // The recommendation pool contains bookable rooms; explicit availability filters remain supported for manual sorts.
        candidates.removeIf(l->!FeedRanking.matchesListing(l,query)||FeedRanking.matchingRooms(l,query,"recommended".equals(sort)).isEmpty());
        Map<String,ListingResponse> byId=candidates.stream().collect(Collectors.toMap(ListingResponse::id,Function.identity()));
        Map<String,Integer> priorities=candidates.stream().collect(Collectors.toMap(ListingResponse::id,l->l.priorityLevel()==null?0:l.priorityLevel()));
        RankingSessions.Snapshot snapshot;
        if(session!=null&&!session.isBlank()) {
            snapshot=sessions.get(session,scope);
            if("recommended".equals(sort)&&snapshot.priorities().entrySet().stream().anyMatch(e->priorities.containsKey(e.getKey())&&!priorities.get(e.getKey()).equals(e.getValue())))
                throw new AppException(ErrorCode.FEED_SESSION_EXPIRED);
        } else {
            if(page>0)throw new AppException(ErrorCode.FEED_SESSION_EXPIRED);
            var ids="recommended".equals(sort)?ranking.listings(candidates,prefs,query,actor):manual(candidates,sort,query);
            snapshot=sessions.create(scope,ids,priorities);
        }
        // Keep offsets in the frozen order; newly hidden/expired items are skipped without shifting subsequent pages.
        long offset=(long)page*size;
        var items=snapshot.ids().stream().skip(offset).limit(size).filter(byId::containsKey).map(byId::get).toList();
        return PageResponse.<ListingResponse>builder().currentPage(page).pageSize(size)
            .totalElements(snapshot.ids().stream().filter(byId::containsKey).count())
            .totalPages((snapshot.ids().size()+size-1)/size).data(items).rankingSession(snapshot.id()).build();
    }
    public List<ListingResponse> map(String actor,ListingSearchRequest query,String sort) {
        validateSort(sort);var candidates=new ArrayList<>(listings.searchCandidates(query));
        candidates.removeIf(l->!FeedRanking.matchesListing(l,query)||FeedRanking.matchingRooms(l,query,"recommended".equals(sort)).isEmpty());
        Map<String,ListingResponse> byId=candidates.stream().collect(Collectors.toMap(ListingResponse::id,Function.identity()));
        List<String> ids="recommended".equals(sort)?ranking.listings(candidates,ranking.preferences(actor),query,actor):manual(candidates,sort,query);
        return ids.stream().map(byId::get).filter(l->l.property().latitude()!=null&&l.property().longitude()!=null).toList();
    }
    private void validateSort(String sort){if(!Set.of("recommended","newest","price-asc","price-desc").contains(sort))throw new AppException(ErrorCode.INVALID_REQUEST);}
    private List<String> manual(List<ListingResponse> items,String sort,ListingSearchRequest query){
        Comparator<ListingResponse> comparator=Comparator.comparing(ListingResponse::publishedAt,Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<BigDecimal> prices="price-desc".equals(sort)?Comparator.reverseOrder():Comparator.naturalOrder();
        if(sort.startsWith("price-"))comparator=Comparator.comparing(l->price(l,query),Comparator.nullsLast(prices));
        return items.stream().sorted(comparator.thenComparing(ListingResponse::id)).map(ListingResponse::id).toList();
    }
    private BigDecimal price(ListingResponse listing,ListingSearchRequest query){return FeedRanking.matchingRooms(listing,query,false).stream()
        .map(r->r.price()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);}
}
