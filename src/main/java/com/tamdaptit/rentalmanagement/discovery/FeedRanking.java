package com.tamdaptit.rentalmanagement.discovery;

import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FeedRanking {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record Preferences(String provinceId,String province,List<String> wards,List<String> wardNames,
        BigDecimal minPrice,BigDecimal maxPrice,BigDecimal minArea,BigDecimal maxArea,String roomType,boolean enabled) {
        public static Preferences empty(){return new Preferences("","",List.of(),List.of(),null,null,null,null,"",false);}
        public String key(){return toString();}
    }
    public record Item(String id,String owner,int priority,double score,double fresh,long exposure) {}
    public record Exposure(long total,long mine) {}
    public Preferences preferences(String actor) {
        if(actor==null)return Preferences.empty();
        var rows=jdbc.queryForList("""
            SELECT tp.id,tp.desired_province_id,tp.desired_ward_id,tp.price_min,tp.price_max,tp.area_min,tp.area_max,
              p.name province_name,w.name ward_name,rt.name room_type
            FROM tenant_profiles tp JOIN users u ON u.id=tp.user_id AND u.status='ACTIVE'
            LEFT JOIN provinces p ON p.id=tp.desired_province_id
            LEFT JOIN wards w ON w.id=tp.desired_ward_id
            LEFT JOIN room_types rt ON rt.id=tp.desired_room_type_id
            WHERE tp.user_id=? AND tp.allow_personalization=1
            """,actor);
        if(rows.isEmpty())return Preferences.empty();
        var row=rows.getFirst();var ids=new TreeSet<String>();var names=new TreeSet<String>();
        if(row.get("desired_ward_id")!=null){ids.add(str(row,"desired_ward_id"));names.add(str(row,"ward_name"));}
        for(var w:jdbc.queryForList("SELECT w.id,w.name FROM tenant_profile_wards tw JOIN wards w ON w.id=tw.ward_id WHERE tw.tenant_profile_id=?",row.get("id"))){ids.add(str(w,"id"));names.add(str(w,"name"));}
        return new Preferences(str(row,"desired_province_id"),str(row,"province_name"),new ArrayList<>(ids),new ArrayList<>(names),
            decimal(row,"price_min"),decimal(row,"price_max"),decimal(row,"area_min"),decimal(row,"area_max"),str(row,"room_type"),true);
    }
    public static String normalize(String value) {
        if(value==null)return "";
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
            .replace('đ','d').replaceAll("[^a-z0-9#_ ]"," ").replaceAll("\\s+"," ").trim();
    }
    private static String place(String value){return normalize(value).replaceFirst("^(phuong|xa|quan|huyen|tinh|thanh pho|thi tran|thi xa) +","");}
    public static double location(Preferences p,String provinceId,String wardId,String text) {
        if(!p.enabled())return .5;
        if(!p.wards().isEmpty()&&p.wards().contains(wardId))return 1;
        String hay=" "+normalize(text)+" ";
        if(p.wardNames().stream().map(FeedRanking::place).filter(n->!n.isBlank()).anyMatch(n->hay.contains(" "+n+" ")))return 1;
        if(!p.provinceId().isBlank()&&p.provinceId().equals(provinceId))return p.wards().isEmpty()?1:.4;
        if(!p.province().isBlank()&&hay.contains(" "+place(p.province())+" "))return p.wards().isEmpty()?1:.4;
        return p.wards().isEmpty()&&p.provinceId().isBlank()?.5:0;
    }
    public Map<String,Exposure> exposures(String surface,Collection<String> ids,String actor) {
        if(ids.isEmpty())return Map.of();
        var sql=new NamedParameterJdbcTemplate(jdbc);
        var rows=sql.queryForList("""
            SELECT item_id,SUM(impression_count) total,
            SUM(CASE WHEN user_id=:actor AND seen_at>=:recent THEN impression_count ELSE 0 END) mine
            FROM feed_impressions WHERE surface=:surface AND item_id IN (:ids) AND seen_at>=:since GROUP BY item_id
            """,Map.of("actor",actor==null?"":actor,"surface",surface,"ids",ids,
                "recent",LocalDateTime.now(clock).minusDays(7),"since",LocalDateTime.now(clock).minusDays(30)));
        var result=new HashMap<String,Exposure>();for(var row:rows)result.put(str(row,"item_id"),new Exposure(num(row,"total"),num(row,"mine")));return result;
    }
    public List<String> listings(List<ListingResponse> listings,Preferences prefs,ListingSearchRequest query,String actor) {
        var exposure=exposures("LISTING",listings.stream().map(ListingResponse::id).toList(),prefs.enabled()?actor:null);
        var stats=new HashMap<String,double[]>();
        if(!listings.isEmpty())for(var row:new NamedParameterJdbcTemplate(jdbc).queryForList("""
            SELECT listing_id,SUM(view_count) views,SUM(contact_count) contacts FROM listing_daily_stats
            WHERE listing_id IN (:ids) AND stat_date>=:since GROUP BY listing_id
            """,Map.of("ids",listings.stream().map(ListingResponse::id).toList(),"since",LocalDate.now(clock).minusDays(7))))
            stats.put(str(row,"listing_id"),new double[]{num(row,"views"),num(row,"contacts")});
        var items=new ArrayList<Item>();
        for(var listing:listings){
            var property=listing.property();
            var rooms=matchingRooms(listing,query,true);
            if(rooms.isEmpty())continue;
            double relevant=rooms.stream().mapToDouble(room->roomRelevance(listing,room,prefs,query)).max().orElse(.5);
            double quality=(listing.title()!=null&&!listing.title().isBlank()?.15:0)
                +(listing.description()!=null&&listing.description().length()>=100?.2:.05)
                +(!property.images().isEmpty()||rooms.stream().anyMatch(r->!r.images().isEmpty())?.25:0)
                +(property.latitude()!=null&&property.longitude()!=null?.1:0)
                +(rooms.stream().anyMatch(r->r.price()!=null&&r.price().signum()>0&&r.areaM2()!=null&&r.areaM2().signum()>0)?.2:0)
                +(!property.amenities().isEmpty()||rooms.stream().anyMatch(r->!r.amenities().isEmpty())?.1:0);
            double[] stat=stats.getOrDefault(listing.id(),new double[]{0,0});
            double engagement=Math.min(1,(stat[1]+1)/(Math.max(stat[0],stat[1])+10));
            double fresh=freshness(listing.publishedAt(),30*24);
            var seen=exposure.getOrDefault(listing.id(),new Exposure(0,0));
            double score=(.35*relevant+.25*quality+.25*engagement+.15*fresh)/(1+.15*seen.mine())-.025*Math.log1p(seen.total());
            items.add(new Item(listing.id(),listing.landlordId(),listing.priorityLevel()==null?0:listing.priorityLevel(),score,fresh,seen.total()));
        }
        return interleave(items,true);
    }
    private double roomRelevance(ListingResponse listing,RoomResponse room,Preferences p,ListingSearchRequest q) {
        double loc=(q.wardId()!=null&&!q.wardId().isBlank())||(q.provinceId()!=null&&!q.provinceId().isBlank())||q.radiusKm()!=null||q.south()!=null?1:
            location(p,listing.property().provinceId(),listing.property().wardId(),listing.property().wardName()+" "+listing.property().provinceName());
        BigDecimal min=q.minPrice()!=null?q.minPrice():p.minPrice(),max=q.maxPrice()!=null?q.maxPrice():p.maxPrice();
        String type=q.roomTypeName()!=null?q.roomTypeName():p.roomType();
        return .45*loc+.35*range(room.price(),min,max)+.1*range(room.areaM2(),p.minArea(),p.maxArea())
            +.1*(type==null||type.isBlank()?.5:normalize(type).equals(normalize(room.roomTypeName()))?1:0);
    }
    private static double range(BigDecimal value,BigDecimal min,BigDecimal max){
        if(min==null&&max==null)return .5;if(value==null)return 0;
        if(min!=null&&value.compareTo(min)<0)return Math.max(0,1-min.subtract(value).doubleValue()/Math.max(min.doubleValue(),1));
        if(max!=null&&value.compareTo(max)>0)return Math.max(0,1-value.subtract(max).doubleValue()/Math.max(max.doubleValue(),1));return 1;
    }
    public double freshness(LocalDateTime created,int hours){return created==null?.5:Math.exp(-Math.max(0,Duration.between(created,LocalDateTime.now(clock)).toMinutes())/(hours*60.0));}
    public Map<String,Integer> topics(String actor,Preferences prefs) {
        if(actor==null||!prefs.enabled())return Map.of();
        var result=new HashMap<String,Integer>();
        for(String text:jdbc.queryForList("""
            SELECT p.content FROM community_posts p WHERE p.status='VISIBLE' AND p.deleted_at IS NULL
            AND (EXISTS(SELECT 1 FROM community_saved_posts s WHERE s.post_id=p.id AND s.user_id=? AND s.created_at>=?)
              OR EXISTS(SELECT 1 FROM community_reactions r WHERE r.target_type='POST' AND r.target_id=p.id AND r.user_id=? AND r.created_at>=?)
              OR EXISTS(SELECT 1 FROM community_comments c WHERE c.post_id=p.id AND c.author_id=? AND c.status='VISIBLE' AND c.deleted_at IS NULL AND c.created_at>=?))
            ORDER BY p.created_at DESC LIMIT 200
            """,String.class,actor,LocalDateTime.now(clock).minusDays(90),actor,LocalDateTime.now(clock).minusDays(90),actor,LocalDateTime.now(clock).minusDays(90)))
            for(String tag:tags(text))result.merge(tag,1,Integer::sum);
        return result;
    }
    public static Set<String> tags(String text){var matcher=Pattern.compile("#[\\p{L}\\p{N}_]+").matcher(text==null?"":text);var result=new HashSet<String>();while(matcher.find())result.add(normalize(matcher.group()));return result;}
    public double interest(Preferences p,Map<String,Integer> topics,String area,String content,List<Map<String,Object>> locations){
        double geo=location(p,"","",area+" "+content);
        for(var row:locations)geo=Math.max(geo,location(p,str(row,"province_id"),str(row,"ward_id"),""));
        boolean hasGeo=p.enabled()&&(!p.wards().isEmpty()||!p.provinceId().isBlank());
        double topic=topics.isEmpty()?.5:tags(content).stream().mapToInt(t->topics.getOrDefault(t,0)).sum()/(double)Math.max(1,topics.values().stream().mapToInt(Integer::intValue).sum());
        return hasGeo?.75*geo+.25*topic:topic;
    }
    public static double communityScore(double interest,double engagement,boolean following,double fresh,double trust,long seen){
        return (.30*interest+.25*engagement+.20*(following?1:0)+.15*fresh+.10*trust)/(1+.25*seen);
    }
    public static boolean matchesListing(ListingResponse listing,ListingSearchRequest q) {
        if(q.rentalMode()!=null && !q.rentalMode().equals(listing.property().rentalMode().name()))return false;
        if(q.propertyTypeId()!=null && !q.propertyTypeId().equals(listing.property().propertyTypeId()))return false;
        String keyword=normalize(q.keyword());
        if(keyword.isBlank())return true;
        String hay=normalize(listing.title()+" "+listing.description()+" "+listing.property().name()+" "
                +listing.property().addressLine()+" "+listing.property().wardName()+" "+listing.property().provinceName());
        return Arrays.stream(keyword.split(" ")).allMatch(hay::contains);
    }
    private static boolean within(BigDecimal value,BigDecimal min,BigDecimal max) {
        return (min==null&&max==null)||(value!=null&&(min==null||value.compareTo(min)>=0)&&(max==null||value.compareTo(max)<=0));
    }
    public static List<RoomResponse> matchingRooms(ListingResponse listing,ListingSearchRequest q,boolean availableOnly) {
        return listing.property().rooms().stream()
            .filter(r->!availableOnly||(r.rentalStatus()==RoomRentalStatus.AVAILABLE&&"AVAILABLE".equals(r.bookingAvailability())))
            .filter(r->q.rentalStatus()==null||r.rentalStatus()==q.rentalStatus())
            .filter(r->q.rentalStatus()!=RoomRentalStatus.AVAILABLE||"AVAILABLE".equals(r.bookingAvailability()))
            .filter(r->q.roomTypeName()==null||q.roomTypeName().isBlank()||normalize(q.roomTypeName()).equals(normalize(r.roomTypeName())))
            .filter(r->q.minPrice()==null||(r.price()!=null&&r.price().compareTo(q.minPrice())>=0))
            .filter(r->q.maxPrice()==null||(r.price()!=null&&r.price().compareTo(q.maxPrice())<=0))
            .filter(r->within(r.areaM2(),q.minArea(),q.maxArea()))
            .filter(r->q.minOccupants()==null||(r.maxOccupants()!=null&&r.maxOccupants()>=q.minOccupants()))
            .filter(r->q.minFloor()==null||(r.floorNumber()!=null&&r.floorNumber()>=q.minFloor()))
            .filter(r->q.maxFloor()==null||(r.floorNumber()!=null&&r.floorNumber()<=q.maxFloor()))
            .filter(r->within(r.defaultDeposit(),null,q.maxDeposit()))
            .filter(r->within(r.electricityPrice(),null,q.maxElectricityPrice()))
            .filter(r->within(r.waterPrice(),null,q.maxWaterPrice()))
            .filter(r->within(r.serviceFee(),null,q.maxServiceFee()))
            .filter(r->q.amenityNames()==null||q.amenityNames().stream().filter(name->name!=null&&!name.isBlank()).allMatch(name->
                r.amenities().stream().anyMatch(a->normalize(name).equals(normalize(a.name())))||listing.property().amenities().stream().anyMatch(a->normalize(name).equals(normalize(a.name())))))
            .toList();
    }
    /** Weighted slots 5 high / 3 low / 2 ordinary, with per-author caps and a discovery slot. */
    public static List<String> interleave(List<Item> input,boolean paid){
        var remaining=new ArrayList<>(input);remaining.sort(Comparator.comparingDouble(Item::score).reversed().thenComparing(Item::id));
        var result=new ArrayList<String>();var counts=new HashMap<String,Integer>();String previous="";int[] slots={2,1,2,0,2,1,2,0,2,1};
        while(!remaining.isEmpty()){
            int position=result.size();if(position%10==0)counts.clear();int desired=paid?slots[position%10]:0;
            List<Item> eligible=remaining.stream().filter(i->counts.getOrDefault(i.owner(),0)<2).toList();
            if(eligible.isEmpty())eligible=remaining;
            var pool=eligible.stream().filter(i->!paid||i.priority()==desired).toList();if(pool.isEmpty())pool=eligible;
            String last=previous;var diverse=pool.stream().filter(i->!i.owner().equals(last)).toList();if(!diverse.isEmpty())pool=diverse;
            Item selected=position%10==9?pool.stream().max(Comparator.comparingDouble((Item i)->.6*i.fresh()+.4/(1+i.exposure())).thenComparing(Item::id)).orElseThrow():pool.getFirst();
            result.add(selected.id());counts.merge(selected.owner(),1,Integer::sum);previous=selected.owner();remaining.remove(selected);
        }
        return result;
    }
    public static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    public static long num(Map<String,Object> row,String key){return row.get(key) instanceof Number n?n.longValue():0;}
    public static BigDecimal decimal(Map<String,Object> row,String key){return row.get(key) instanceof BigDecimal d?d:null;}
}
