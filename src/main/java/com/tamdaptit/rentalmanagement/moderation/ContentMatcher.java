package com.tamdaptit.rentalmanagement.moderation;

import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Rule;
import java.net.URI;
import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic token matching; patterns are data, never executable regex. */
public final class ContentMatcher {
    private ContentMatcher() {}
    public static String normalize(String value, boolean accents) {
        String text=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[\\p{Cf}]", "");
        if(accents) text=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ','d');
        return text.replaceAll("[^\\p{L}\\p{N}]+", " ").strip().replaceAll("\\s+", " ");
    }
    public static boolean matches(Rule rule,String text) {
        if(text==null || text.isBlank()) return false;
        if("DOMAIN".equals(rule.matchType())) {
            var urls=Pattern.compile("https?://[^\\s<>]+",Pattern.CASE_INSENSITIVE).matcher(text);
            while(urls.find()) try {
                String host=URI.create(urls.group()).getHost();
                if(host==null) continue;
                host=IDN.toASCII(host).toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
                String domain=IDN.toASCII(rule.pattern().strip()).toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
                if(host.equals(domain)||host.endsWith("."+domain)) return true;
            } catch(IllegalArgumentException ignored) { /* Invalid URL is plain text. */ }
            return false;
        }
        String pattern=normalize(rule.pattern(),rule.accentInsensitive());
        return !pattern.isBlank() && (" "+normalize(text,rule.accentInsensitive())+" ").contains(" "+pattern+" ");
    }
}
