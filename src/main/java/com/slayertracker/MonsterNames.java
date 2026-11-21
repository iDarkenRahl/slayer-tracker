package com.slayertracker;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class MonsterNames {
    private final Map<String,Set<String>> names = new HashMap<>();
    MonsterNames() throws IOException {
        try(InputStream stream=MonsterNames.class.getResourceAsStream("aliases.tsv")) {
            if(stream==null)throw new IOException("Missing task aliases");
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null){String[] parts=line.split("\t");String task=normal(parts[0]).replaceFirst("\\s*\\[[a-z]\\]$","");Set<String> set=names.computeIfAbsent(task,k->new HashSet<>());set.add(task);for(int i=1;i<parts.length;i++)set.add(normal(parts[i]));}
            }
        }
    }
    static String normal(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('\u00a0',' ').trim();}
    static String singular(String s){if(s.equals("wolves"))return "wolf";if(s.equals("werewolves"))return "werewolf";if(s.equals("dwarves"))return "dwarf";if(s.equals("elves"))return "elf";return s.endsWith("ies")?s.substring(0,s.length()-3)+"y":s.endsWith("s")?s.substring(0,s.length()-1):s;}
    boolean matches(String task,String npc){String a=normal(task),b=normal(npc);if(a.isEmpty()||b.isEmpty())return false;if(a.equals(b)||singular(a).equals(b))return true;for(String alias:names.getOrDefault(a,Collections.emptySet()))if(alias.equals(b)||singular(alias).equals(b))return true;return false;}
}
