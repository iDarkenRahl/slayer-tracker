package com.slayertracker;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KillEvidenceTest {
    private Ledger.Sample sample(String key,int remaining,int xp){return new Ledger.Sample(key,key,remaining,10,0,xp,0,0,0);}
    @Test void delayedEvidenceCountsOnlyOneCandidate(){
        KillEvidence e=new KillEvidence();Object npc=new Object();Ledger.Sample old=sample("bears",10,100);
        e.damage(npc);e.death(npc,"bear","bears");assertTrue(e.sample(old,old).isEmpty());
        assertEquals(List.of("bear"),e.sample(old,sample("bears",10,110)));
        assertTrue(e.sample(old,sample("bears",10,110)).isEmpty());
    }
    @Test void multipleDeathsAreAmbiguous(){
        KillEvidence e=new KillEvidence();Object a=new Object(),b=new Object();Ledger.Sample old=sample("bears",10,100);
        e.damage(a);e.damage(b);e.death(a,"bear","bears");e.death(b,"bear","bears");
        assertTrue(e.sample(old,sample("bears",8,120)).isEmpty());
    }
    @Test void staleDamageAndCandidatesExpire(){
        KillEvidence e=new KillEvidence();Object npc=new Object();Ledger.Sample old=sample("bears",10,100);
        e.damage(npc);for(int i=0;i<4;i++)e.sample(old,old);e.death(npc,"bear","bears");
        assertTrue(e.sample(old,sample("bears",9,110)).isEmpty());
        e.damage(npc);e.death(npc,"bear","bears");for(int i=0;i<4;i++)e.sample(old,old);
        assertTrue(e.sample(old,sample("bears",9,110)).isEmpty());
    }
    @Test void taskChangeDiscardsPendingEvidence(){
        KillEvidence e=new KillEvidence();Object npc=new Object();Ledger.Sample old=sample("bears",10,100);
        e.damage(npc);e.death(npc,"bear","bears");Ledger.Sample next=sample("wolves",10,100);
        assertTrue(e.sample(old,next).isEmpty());assertTrue(e.sample(next,sample("wolves",9,110)).isEmpty());
    }
}
