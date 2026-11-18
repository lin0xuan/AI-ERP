package com.aierp.erp.memory;

import com.aierp.model.entity.AiConversationHistory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class MemoryPolicy {
    private MemoryPolicy() {}
    private static final Pattern CRITICAL = Pattern.compile("[0-9０-９]|[零一二三四五六七八九十百千万亿]+[元年月日个件%％]|必须|禁止|不能|不要|不得|仅限|务必|预算|截止|上限|下限|至少|至多|不超过|约束|must|never|only|deadline|budget",Pattern.CASE_INSENSITIVE);
    public static boolean explicit(String text) {
        return text.matches("(?s).*(请记住|记住[：:，, ]|记住我的|记住我|以后都|长期记住|remember that|remember my).*" )
                && !text.matches("(?s).*(不要记住|别记住|不用记住|不要保存).*" );
    }
    public static String key(String value) {
        String result=Normalizer.normalize(value,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(result.isEmpty() || result.length()>255) throw new IllegalArgumentException("记忆键长度无效");
        return result;
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static Set<String> terms(String text) {
        Set<String> terms=new HashSet<>();
        var matcher=Pattern.compile("[a-z0-9_.-]+|[\\p{IsHan}]+",Pattern.CASE_INSENSITIVE).matcher(text.toLowerCase(Locale.ROOT));
        while(matcher.find()) {
            String word=matcher.group();
            if(word.matches("[\\p{IsHan}]+") && word.length()>1) {
                for(int i=0;i<word.length()-1;i++) terms.add(word.substring(i,i+2));
            } else terms.add(word);
        }
        return terms;
    }
    public static double keyword(String query,String content) {
        Set<String> q=terms(query), c=terms(content);
        if(q.isEmpty()) return 0;
        return q.stream().filter(c::contains).count()/(double)q.size();
    }
    public static double cosine(float[] a,float[] b) {
        if(a.length==0 || a.length!=b.length) return 0;
        double dot=0,aa=0,bb=0;
        for(int i=0;i<a.length;i++) { dot+=a[i]*b[i]; aa+=a[i]*a[i]; bb+=b[i]*b[i]; }
        return aa==0 || bb==0 ? 0 : dot/Math.sqrt(aa*bb);
    }
    public static double score(double relevance,int importance,long updated,long accessed,long now) {
        double age=Math.max(0,now-updated)/86400_000d;
        double unused=Math.max(0,now-accessed)/86400_000d;
        // Importance slows decay but never gives a three-year-old fact the same score as a new one.
        double decay=Math.pow(0.5,age/(30+importance*18d));
        return relevance*(0.55+importance/10d)*(0.25+0.75*decay)/(1+unused/730d);
    }
    public static int recentStart(List<AiConversationHistory> history,int turns) {
        int count=0;
        for(int i=history.size()-1;i>=0;i--) if("user".equals(history.get(i).getRole()) && ++count>=turns) return i;
        return 0;
    }
    public static List<String> anchors(List<AiConversationHistory> history) {
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(var message:history) {
            for(String line:message.getContent().split("(?<=[。！？!?\\n])")) {
                if(CRITICAL.matcher(line).find()) result.add("["+message.getRole()+"#"+message.getId()+"] "+line);
            }
        }
        return new ArrayList<>(result);
    }
    public static String redact(String text) {
        if(text==null) return "";
        return text.replaceAll("(?i)([\\\"']?(?:api[_-]?key|password|token|secret|authorization)[\\\"']?\\s*[:=]\\s*)[\\\"']?[^\\s,}\\\"']+[\\\"']?", "$1[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[a-z0-9_.-]+","Bearer [REDACTED]");
    }
}
