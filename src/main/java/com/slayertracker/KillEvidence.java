package com.slayertracker;

import java.util.*;

/** Short-lived evidence only; a corroborating interval cannot identify multiple deaths. */
final class KillEvidence {
    private static final long WINDOW = 3;
    private long tick;
    private final Map<Object,Long> damaged = new IdentityHashMap<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private boolean ambiguous;
    private static final class Candidate {
        final String name, task;
        final long tick;
        Candidate(String name,String task,long tick){this.name=name;this.task=task;this.tick=tick;}
    }
    void damage(Object npc){damaged.put(npc,tick);}
    void despawn(Object npc){damaged.remove(npc);}
    void death(Object npc,String name,String task){Long hit=damaged.remove(npc);if(hit!=null && tick-hit<=WINDOW){candidates.add(new Candidate(name,task,tick));if(candidates.size()>1)ambiguous=true;}}
    void clear(){damaged.clear();candidates.clear();ambiguous=false;}
    List<String> sample(Ledger.Sample old,Ledger.Sample now){
        tick++;
        damaged.values().removeIf(t->tick-t>WINDOW);
        candidates.removeIf(c->tick-c.tick>WINDOW || old==null || !old.active() || !c.task.equals(old.key));
        if(candidates.isEmpty())ambiguous=false;
        if(old==null || !old.active())return Collections.emptyList();
        boolean same=now.active() && old.key.equals(now.key);
        boolean evidence=now.xp>old.xp || (same && now.remaining<old.remaining);
        List<String> result=Collections.emptyList();
        if(evidence && !candidates.isEmpty()){
            if(!ambiguous && candidates.size()==1)result=Collections.singletonList(candidates.get(0).name);
            candidates.clear();ambiguous=false;
        }
        if(!same)clear();
        return result;
    }
}
