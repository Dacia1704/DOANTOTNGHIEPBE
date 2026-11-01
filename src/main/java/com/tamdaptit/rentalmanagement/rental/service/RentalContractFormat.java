package com.tamdaptit.rentalmanagement.rental.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Presentation of the existing snapshot's ISO dates and decimal VND amounts. */
final class RentalContractFormat {
    static final String BLANK="..............................";
    private static final String[] DIGITS={"không","một","hai","ba","bốn","năm","sáu","bảy","tám","chín"};
    private RentalContractFormat() {}

    static String date(String value) {
        LocalDate date=parseDate(value);
        return date==null?"...../...../........":date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    static String issuedDate(String value) {
        LocalDate date=parseDate(value);
        return date==null?"ngày ..... tháng ..... năm ........":
                date.format(DateTimeFormatter.ofPattern("'ngày' dd 'tháng' MM 'năm' yyyy"));
    }

    private static LocalDate parseDate(String value) {
        if(RentalContractPdfService.missing(value)) return null;
        try { return LocalDate.parse(value.length()>10?value.substring(0,10):value); }
        catch(DateTimeParseException ex) { return null; }
    }

    private static BigDecimal amount(String value) {
        if(RentalContractPdfService.missing(value)) return null;
        try { return new BigDecimal(value.replace("VND","").trim()); }
        catch(NumberFormatException ex) { return null; }
    }

    static String money(String value) {
        BigDecimal amount=amount(value);
        if(amount==null) return BLANK;
        NumberFormat format=NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(Math.max(0,amount.stripTrailingZeros().scale()));
        return format.format(amount);
    }

    static String moneyInWords(String value) {
        BigDecimal amount=amount(value);
        if(amount==null) return BLANK;
        // VND contracts use whole đồng. Never silently round a fractional amount in words.
        if(amount.signum()<0||amount.stripTrailingZeros().scale()>0) return BLANK;
        BigInteger number=amount.toBigIntegerExact();
        if(number.signum()==0) return "Không đồng";
        List<Integer> groups=new ArrayList<>();
        while(number.signum()>0) {
            BigInteger[] part=number.divideAndRemainder(BigInteger.valueOf(1000));
            groups.add(part[1].intValue()); number=part[0];
        }
        StringJoiner words=new StringJoiner(" ");
        for(int i=groups.size()-1;i>=0;i--) {
            int group=groups.get(i);
            if(group==0) continue;
            words.add(readGroup(group,i<groups.size()-1));
            String unit=switch(i%3) { case 1 -> "nghìn"; case 2 -> "triệu"; default -> ""; };
            if(!unit.isEmpty()) words.add(unit);
            for(int billion=0;billion<i/3;billion++) words.add("tỷ");
        }
        String result=words+" đồng";
        return Character.toUpperCase(result.charAt(0))+result.substring(1);
    }

    private static String readGroup(int number,boolean full) {
        int hundreds=number/100,tens=number/10%10,ones=number%10;
        StringJoiner words=new StringJoiner(" ");
        if(hundreds>0||full) { words.add(DIGITS[hundreds]); words.add("trăm"); }
        if(tens>1) { words.add(DIGITS[tens]); words.add("mươi"); }
        else if(tens==1) words.add("mười");
        else if(ones>0&&(hundreds>0||full)) words.add("lẻ");
        if(ones>0) words.add(ones==1&&tens>1?"mốt":ones==5&&tens>0?"lăm":DIGITS[ones]);
        return words.toString();
    }
}
