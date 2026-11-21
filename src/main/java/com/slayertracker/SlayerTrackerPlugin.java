package com.slayertracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name="Slayer Tracker", description="Track Slayer points, assignments and monster kills in a sidebar", tags={"slayer","statistics","tasks"})
public class SlayerTrackerPlugin extends Plugin {
    private static final String GROUP="slayertracker", KEY="ledgerV1";
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private Gson gson;
    @Inject private SkillIconManager skillIcons;
    private TrackerPanel panel;
    private NavigationButton nav;
    private MonsterNames names;
    private Ledger ledger;
    private String profile;
    private long account;
    private volatile boolean running;
    private int warmup;
    private boolean skipMessage;
    private final KillEvidence evidence=new KillEvidence();
    private final Set<NPC> dead=Collections.newSetFromMap(new IdentityHashMap<>());
    private long generation;
    private String fault;
    private boolean waitingForTask;
    private String savedSnapshot;
    @Override protected void startUp() throws Exception {
        names=new MonsterNames(); running=true;
        panel=new TrackerPanel(this,new ImageIcon(skillIcons.getSkillImage(Skill.SLAYER))); nav=NavigationButton.builder().tooltip("Slayer Tracker").icon(icon()).priority(8).panel(panel).build();
        clientToolbar.addNavigation(nav);
    }
    @Override protected void shutDown(){running=false; clientToolbar.removeNavigation(nav); clientThread.invoke(this::clear);}
    private static BufferedImage icon(){BufferedImage source=ImageUtil.loadImageResource(SlayerTrackerPlugin.class,"icon.png");BufferedImage image=new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();try{graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(source,0,0,24,24,0,12,48,60,null);}finally{graphics.dispose();}return image;}
    private boolean ready(){return running && ledger!=null && fault==null && warmup==0 && client.getGameState()==GameState.LOGGED_IN && Objects.equals(profile,configManager.getRSProfileKey()) && account==client.getAccountHash();}
    private void clear(){ledger=null;profile=null;fault=null;savedSnapshot=null;waitingForTask=false;warmup=0;generation++;evidence.clear();dead.clear();skipMessage=false;}
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged e){clientThread.invoke(()->{if(profile!=null&&!Objects.equals(profile,configManager.getRSProfileKey())){clear();publish("Waiting for account profile");}});}
    @Subscribe public void onGameStateChanged(GameStateChanged e){if(e.getGameState()==GameState.LOGIN_SCREEN || e.getGameState()==GameState.HOPPING || e.getGameState()==GameState.LOGGING_IN){clear();publish("Waiting for login");} else if(e.getGameState()==GameState.LOADING){evidence.clear();dead.clear();}}
    @Subscribe public void onGameTick(GameTick e){
        if(!running||client.getGameState()!=GameState.LOGGED_IN)return;
        String key=configManager.getRSProfileKey();long hash=client.getAccountHash();if(key==null||hash==-1||hash==0){clear();publish("Waiting for account profile");return;}
        if(!Objects.equals(profile,key)||account!=hash){clear();profile=key;account=hash;warmup=3;try{savedSnapshot=configManager.getRSProfileConfiguration(GROUP,KEY);Ledger.Data data=savedSnapshot==null?new Ledger.Data():decode(savedSnapshot);ledger=new Ledger(data);}catch(RuntimeException ex){fault="Saved data invalid — tracking stopped";} }
        if(fault!=null){publish(fault);return;}
        if(warmup>0){warmup--;if(warmup>0){publish("Loading account");return;}}
        try {
            Ledger.Sample s=sample();
            if(s==null){evidence.clear();dead.clear();skipMessage=false;waitingForTask=true;publish("Waiting for Slayer task data");return;}
            if(waitingForTask){boolean changed=ledger.baseline(s);evidence.clear();dead.clear();skipMessage=false;waitingForTask=false;if(changed)save();publish(null);return;}
            Ledger.Sample old=ledger.previous;
            boolean dirty=ledger.update(s,evidence.sample(old,s),skipMessage);skipMessage=false;
            if(old!=null && old.active() && (!s.active() || !old.key.equals(s.key))) dead.clear();
            if(dirty)save();
            if(dirty||old==null||!old.key.equals(s.key)||old.remaining!=s.remaining||old.total!=s.total||old.points!=s.points)publish(null);
        } catch(RuntimeException ex){fault="Tracking stopped — unable to read or save data";publish(fault);}
    }
    private Ledger.Sample sample(){
        int remaining=client.getVarpValue(VarPlayerID.SLAYER_COUNT), id=client.getVarpValue(VarPlayerID.SLAYER_TARGET), area=client.getVarpValue(VarPlayerID.SLAYER_AREA), boss=client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID);
        String name="";if(remaining>0){
            List<Integer> rows=id==98?client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,0,boss):client.getDBRowsByValue(DBTableID.SlayerTask.ID,DBTableID.SlayerTask.COL_ID,0,id);
            if(rows==null || rows.isEmpty())return null;
            int row=id==98?(Integer)client.getDBTableField(rows.get(0),DBTableID.SlayerTaskSublist.COL_TASK,0)[0]:rows.get(0);
            name=MonsterNames.normal((String)client.getDBTableField(row,DBTableID.SlayerTask.COL_NAME_UPPERCASE,0)[0]);
        }
        int total=client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        if(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)==2)total+=(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE)==1?-1:1)*client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
        return new Ledger.Sample(id+":"+area+":"+(id==98?boss:0),name,Math.max(0,remaining),Math.max(0,total),client.getVarbitValue(VarbitID.SLAYER_POINTS),client.getSkillExperience(Skill.SLAYER),client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED),client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED),client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED));
    }
    @Subscribe public void onHitsplatApplied(HitsplatApplied e){if(ready()&&e.getActor() instanceof NPC&&e.getHitsplat().isMine()&&e.getHitsplat().getAmount()>0)evidence.damage(e.getActor());}
    @Subscribe public void onActorDeath(ActorDeath e){if(!ready()||!(e.getActor() instanceof NPC)||ledger.previous==null||!ledger.previous.active())return;NPC npc=(NPC)e.getActor();String name=MonsterNames.normal(npc.getName());if(dead.add(npc)&&names.matches(ledger.previous.name,name))evidence.death(npc,name,ledger.previous.key);}
    @Subscribe public void onNpcDespawned(NpcDespawned e){evidence.despawn(e.getNpc());dead.remove(e.getNpc());}
    @Subscribe public void onChatMessage(ChatMessage e){if(ready()&&e.getType()==ChatMessageType.GAMEMESSAGE){String m=MonsterNames.normal(e.getMessage().replaceAll("<[^>]*>",""));if(m.equals("your slayer task has been cancelled.")||m.equals("you have cancelled your slayer task."))skipMessage=true;}}
    private Ledger.Data decode(String json){return decode(json,gson);}
    static Ledger.Data decode(String json,Gson gson){
        JsonObject root=new JsonParser().parse(json).getAsJsonObject();
        requireFields(root,"version","earned","spent","completed","unassignedCompleted","tasks","history");
        JsonObject tasks=root.getAsJsonObject("tasks");
        for(Map.Entry<String,com.google.gson.JsonElement> entry:tasks.entrySet()) requireFields(entry.getValue().getAsJsonObject(),"assigned","completed","skipped","xp","points","kills","monsters");
        for(com.google.gson.JsonElement value:root.getAsJsonArray("history")) requireFields(value.getAsJsonObject(),"task","result","kills","xp","points");
        for(String key:new String[]{"current","pending"})if(root.has(key)&&!root.get(key).isJsonNull()){JsonObject progress=root.getAsJsonObject(key);requireFields(progress,"key","name","stats");requireFields(progress.getAsJsonObject("stats"),"assigned","completed","skipped","xp","points","kills","monsters");}
        Ledger.Data data=gson.fromJson(root,Ledger.Data.class);data.validate();return data;
    }
    private static void requireFields(JsonObject object,String... fields){for(String field:fields)if(!object.has(field)||object.get(field).isJsonNull())throw new IllegalArgumentException("Incomplete ledger");}
    private void writeData(Ledger.Data data){if(!ready()||!Objects.equals(savedSnapshot,configManager.getRSProfileConfiguration(GROUP,KEY)))throw new IllegalStateException("Account or saved data changed");String json=gson.toJson(data);if(!ready())throw new IllegalStateException("Account changed");configManager.setRSProfileConfiguration(GROUP,KEY,json);savedSnapshot=json;}
    private void save(){writeData(ledger.data);}
    private void publish(String message){
        if(panel==null||!running)return;long token=generation;
        Ledger.Data data=message==null&&ledger!=null?gson.fromJson(gson.toJson(ledger.data),Ledger.Data.class):null;
        Ledger.Sample sample=message==null&&ledger!=null?ledger.previous:null;
        Ledger.Stats active=message==null&&ledger!=null?gson.fromJson(gson.toJson(ledger.active),Ledger.Stats.class):null;
        SwingUtilities.invokeLater(()->{if(running&&panel!=null)panel.showData(data,sample,active,message,token);});
    }
    String serializeData(Ledger.Data data){return gson.toJson(data);}
}
