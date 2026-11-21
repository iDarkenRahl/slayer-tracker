package com.slayertracker;

import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {
    private Ledger.Sample sample(String task,int remaining,int points,int xp,int normal,int wild,int mortimer){
        return new Ledger.Sample(task,task,remaining,200,points,xp,normal,wild,mortimer);
    }
    @Test void baselineDoesNotInventHistory(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.update(sample("bears",30,900,100_000,30,10,2),Collections.emptyList(),false);
        assertEquals(0,data.earned);assertEquals(0,data.completed);assertTrue(data.tasks.isEmpty());
    }
    @Test void estimatedKillsTrackCandidates(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.baseline(sample("abyssal demons",10,100,1000,10,0,0));
        ledger.update(sample("abyssal demons",10,100,1150,10,0,0),List.of("abyssal demon"),false);
        ledger.update(sample("abyssal demons",8,100,1300,10,0,0),List.of("abyssal demon"),false);
        assertEquals(2,data.stats("abyssal demons").kills);
        ledger.update(sample("abyssal demons",6,100,1450,10,0,0),List.of("abyssal demon"),false);
        assertEquals(3,data.stats("abyssal demons").kills);
    }
    @Test void lateStreakCompletesTask(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.baseline(sample("dust devils",1,100,1000,5,5,0));
        ledger.update(sample("",0,100,1100,5,5,0),List.of("dust devil"),false);
        ledger.update(sample("",0,115,1100,5,6,0),Collections.emptyList(),false);
        assertEquals(1,data.completed);assertEquals(1,data.stats("dust devils").completed);assertEquals(15,data.earned);
    }
    @Test void explicitSkipAndPointSpend(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.baseline(sample("bears",30,100,1000,5,5,0));
        ledger.update(sample("",0,70,1000,5,5,0),Collections.emptyList(),true);
        assertEquals(30,data.spent);assertEquals(1,data.stats("bears").skipped);assertEquals(0,data.completed);
    }
    @Test void skipMessageCanPrecedeCounterChange(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.baseline(sample("bears",30,100,1000,5,0,0));
        ledger.update(sample("bears",30,70,1000,5,0,0),Collections.emptyList(),true);
        ledger.update(sample("",0,70,1000,5,0,0),Collections.emptyList(),false);
        assertEquals(1,data.stats("bears").skipped);assertEquals("Skipped",data.history.get(0).result);
    }
    @Test void oldSkipMessageExpires(){
        Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        Ledger.Sample bears=sample("bears",30,100,1000,5,0,0);ledger.baseline(bears);
        ledger.update(bears,Collections.emptyList(),true);
        ledger.update(bears,Collections.emptyList(),false);ledger.update(bears,Collections.emptyList(),false);
        ledger.update(sample("",0,100,1000,5,0,0),Collections.emptyList(),false);
        ledger.update(sample("",0,100,1000,5,0,0),Collections.emptyList(),false);
        ledger.update(sample("",0,100,1000,5,0,0),Collections.emptyList(),false);
        assertEquals(0,data.stats("bears").skipped);assertTrue(data.history.isEmpty());
    }
    @Test void currentTaskSurvivesSerializationAndResumes(){
        Gson gson=new Gson();Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.update(sample("bears",3,100,1000,5,0,0),Collections.emptyList(),false);
        ledger.update(sample("bears",2,100,1100,5,0,0),List.of("bear"),false);
        Ledger.Data restored=SlayerTrackerPlugin.decode(gson.toJson(data),gson);Ledger next=new Ledger(restored);
        next.update(sample("bears",2,100,1100,5,0,0),Collections.emptyList(),false);
        next.update(sample("bears",1,100,1200,5,0,0),List.of("bear"),false);
        next.update(sample("",0,115,1300,6,0,0),List.of("bear"),false);
        assertEquals(3,restored.stats("bears").kills);assertEquals(3,restored.history.get(0).kills);
        assertEquals(1,restored.completed);
    }
    @Test void interruptedTaskChangeKeepsTotalsWithoutHistory(){
        Gson gson=new Gson();Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.update(sample("bears",10,100,1000,5,0,0),Collections.emptyList(),false);
        ledger.update(sample("bears",9,100,1100,5,0,0),List.of("bear"),false);
        Ledger.Data restored=SlayerTrackerPlugin.decode(gson.toJson(data),gson);Ledger next=new Ledger(restored);
        next.update(sample("wolves",20,100,1100,5,0,0),Collections.emptyList(),false);
        assertEquals(1,restored.stats("bears").kills);assertTrue(restored.history.isEmpty());
        Ledger again=new Ledger(SlayerTrackerPlugin.decode(gson.toJson(restored),gson));
        again.update(sample("wolves",20,100,1100,5,0,0),Collections.emptyList(),false);
        assertTrue(again.data.history.isEmpty());
    }
    @Test void pendingOutcomeDoesNotCreateHistoryOnRestart(){
        Gson gson=new Gson();Ledger.Data data=new Ledger.Data();Ledger ledger=new Ledger(data);
        ledger.update(sample("bears",2,100,1000,5,0,0),Collections.emptyList(),false);
        ledger.update(sample("",0,100,1100,5,0,0),List.of("bear"),false);
        Ledger.Data restored=SlayerTrackerPlugin.decode(gson.toJson(data),gson);Ledger next=new Ledger(restored);
        next.update(sample("",0,100,1100,5,0,0),Collections.emptyList(),false);
        assertTrue(restored.history.isEmpty());assertEquals(1,restored.stats("bears").kills);
    }
    @Test void wikiAliases() throws Exception {
        MonsterNames names=new MonsterNames();assertTrue(names.matches("abyssal demons","Abyssal Sire"));assertTrue(names.matches("abyssal demons","Greater abyssal demon"));assertFalse(names.matches("abyssal demons","Goblin"));
    }
    @Test void footnotesAndIrregularPluralsMatch() throws Exception {
        MonsterNames names=new MonsterNames();
        assertTrue(names.matches("black dragons","Baby black dragon"));
        assertTrue(names.matches("wolves","Wolf"));assertTrue(names.matches("werewolves","Werewolf"));
        assertTrue(names.matches("dwarves","Dwarf"));assertTrue(names.matches("elves","Elf"));
        assertFalse(names.matches("wolves","Werewolf"));
    }
    @Test void invalidBackupRejected(){Ledger.Data data=new Ledger.Data();data.earned=-1;assertThrows(IllegalArgumentException.class,data::validate);}
    @Test void incompleteBackupIsRejected(){
        Gson gson=new Gson();
        assertThrows(RuntimeException.class,()->SlayerTrackerPlugin.decode("{}",gson));
        String partial=gson.toJson(new Ledger.Data()).replace("\"history\":[]", "\"history\":[{}]");
        assertThrows(RuntimeException.class,()->SlayerTrackerPlugin.decode(partial,gson));
        assertDoesNotThrow(()->SlayerTrackerPlugin.decode(gson.toJson(new Ledger.Data()),gson));
    }
    @Test void malformedProgressIsRejected(){
        Gson gson=new Gson();String json=gson.toJson(new Ledger.Data());
        String invalid=json.substring(0,json.length()-1)+",\"current\":{\"key\":\"bears\"}}";
        assertThrows(RuntimeException.class,()->SlayerTrackerPlugin.decode(invalid,gson));
    }
}
