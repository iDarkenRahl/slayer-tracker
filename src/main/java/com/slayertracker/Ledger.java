package com.slayertracker;

import java.util.*;

/** No RuneLite or UI dependencies. All mutations on the client thread. */
final class Ledger {
    static final int VERSION = 1;
    static final class Stats {
        long assigned, completed, skipped, xp, points, kills;
        Map<String, Long> monsters = new TreeMap<>();
        void kill(String name) { kills++; monsters.merge(name, 1L, Long::sum); }
    }
    static final class Record {
        String task, result;
        long kills, xp, points;
        Record() {}
        Record(String task, String result, Stats delta) {
            this.task=task; this.result=result; kills=delta.kills; xp=delta.xp; points=delta.points;
        }
    }
    static final class Progress {
        String key, name;
        Stats stats;
        Progress(Sample sample,Stats stats){this.key=sample.key;this.name=sample.name;this.stats=stats;}
    }
    static final class Data {
        int version = VERSION;
        long earned, spent, completed, unassignedCompleted;
        Map<String, Stats> tasks = new TreeMap<>();
        List<Record> history = new ArrayList<>();
        Progress current, pending;
        Stats stats(String name) { return tasks.computeIfAbsent(name, k -> new Stats()); }
        void validate() {
            if(version!=VERSION || earned<0 || spent<0 || completed<0 || unassignedCompleted<0 || tasks==null || history==null) throw new IllegalArgumentException("Invalid ledger");
            for (Map.Entry<String, Stats> e: tasks.entrySet()) {
                Stats s=e.getValue();
                if(e.getKey()==null || e.getKey().isBlank() || s==null || s.monsters==null || s.assigned<0 || s.completed<0 || s.skipped<0 || s.xp<0 || s.points<0 || s.kills<0) throw new IllegalArgumentException("Invalid task statistics");
                long n=0; for(Map.Entry<String,Long> m:s.monsters.entrySet()) { if(m.getKey()==null || m.getKey().isBlank() || m.getValue()==null || m.getValue()<0) throw new IllegalArgumentException("Invalid kills"); n=Math.addExact(n,m.getValue()); }
                if(n!=s.kills) throw new IllegalArgumentException("Kill total mismatch");
            }
            for(Record r:history) if(r==null || r.task==null || r.task.isBlank() || !Arrays.asList("Completed","Skipped").contains(r.result) || r.kills<0 || r.xp<0 || r.points<0) throw new IllegalArgumentException("Invalid history");
            validateProgress(current);validateProgress(pending);
        }
        private void validateProgress(Progress p){if(p==null)return;if(p.key==null||p.key.isBlank()||p.name==null||p.name.isBlank()||p.stats==null)throw new IllegalArgumentException("Invalid task progress");Data check=new Data();check.tasks.put(p.name,p.stats);check.validate();}
    }
    static final class Sample {
        final String key, name; final int remaining, total, points, xp, normal, wild, mortimer;
        Sample(String key,String name,int remaining,int total,int points,int xp,int normal,int wild,int mortimer) {
            this.key=remaining>0?key:""; this.name=remaining>0?name:""; this.remaining=remaining; this.total=total; this.points=points; this.xp=xp; this.normal=normal; this.wild=wild; this.mortimer=mortimer;
        }
        boolean active(){return remaining>0 && !name.isBlank();}
    }
    final Data data;
    Sample previous;
    Stats active = new Stats();
    private Sample ended;
    private Stats endedStats;
    private int pendingTicks;
    private boolean pendingSkip;
    private String skipTask;
    private int skipTicks;
    Ledger(Data data){this.data=data;}
    boolean baseline(Sample s){
        boolean changed=false;
        if(data.pending!=null){data.pending=null;changed=true;}
        Progress saved=data.current;
        if(saved!=null && (!s.active() || !saved.key.equals(s.key) || !saved.name.equals(s.name))){saved=null;changed=true;}
        previous=s;active=saved==null?new Stats():saved.stats;ended=null;endedStats=null;pendingTicks=0;pendingSkip=false;skipTask=null;skipTicks=0;
        if(s.active()){if(saved==null){data.current=new Progress(s,active);changed=true;}}else data.current=null;
        return changed;
    }
    private void clearEnded(){ended=null;endedStats=null;pendingTicks=0;pendingSkip=false;}
    private void finish(String result){
        if(ended==null)return;
        Stats s=data.stats(ended.name);
        if(result.equals("Completed"))s.completed++; else if(result.equals("Skipped"))s.skipped++;
        data.history.add(new Record(ended.name,result,endedStats));
        clearEnded();
    }
    boolean update(Sample now,List<String> candidates,boolean explicitSkip){
        if(previous==null)return baseline(now);
        Sample old=previous;
        int pointDelta=now.points-old.points;
        long xp=Math.max(0,now.xp-old.xp);
        int completions=Math.max(0,now.normal-old.normal)+Math.max(0,now.wild-old.wild)+Math.max(0,now.mortimer-old.mortimer);
        boolean changed=pointDelta!=0 || completions>0;
        boolean same=old.active() && now.active() && old.key.equals(now.key);
        boolean transition=old.active() && (!same || (completions>0 && ended==null));
        if(explicitSkip && ended==null && old.active()){skipTask=old.key;skipTicks=3;}
        boolean skipForOld=skipTicks>0 && old.active() && old.key.equals(skipTask);
        if(pointDelta>0)data.earned+=pointDelta; else if(pointDelta<0)data.spent-=pointDelta;
        if(completions>0)data.completed+=completions;
        if(old.active()){
            Stats s=data.stats(old.name);
            s.xp+=xp; active.xp+=xp;
            // Candidates have already been evaluated by KillEvidence.
            for(String name:candidates){s.kill(name);active.kill(name);changed=true;}
            changed|=xp>0;
        }
        if(transition){
            clearEnded();ended=old;endedStats=active;pendingTicks=3;pendingSkip=skipForOld;skipTask=null;skipTicks=0;active=new Stats();changed=true;
        }
        if(now.active() && (!old.active() || transition)){
            data.stats(now.name).assigned++;changed=true;
        }
        if(ended!=null){
            pendingSkip|=explicitSkip;
            if(completions==1){
                if(pointDelta>0){data.stats(ended.name).points+=pointDelta;endedStats.points+=pointDelta;}
                finish("Completed");completions=0;changed=true;
            } else if(pendingSkip){finish("Skipped");changed=true;}
            else if(--pendingTicks<=0){clearEnded();changed=true;}
        }
        if(completions>0){data.unassignedCompleted+=completions;changed=true;}
        if(skipTicks>0 && --skipTicks==0)skipTask=null;
        data.current=now.active()?new Progress(now,active):null;
        data.pending=ended==null?null:new Progress(ended,endedStats);
        previous=now;
        return changed;
    }
}
