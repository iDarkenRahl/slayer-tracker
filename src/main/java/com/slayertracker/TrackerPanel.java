package com.slayertracker;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;

final class TrackerPanel extends PluginPanel {
    private static final Color BG=new Color(35,32,28), CARD=new Color(48,43,36), GOLD=new Color(255,214,122), TEXT=Color.WHITE, MUTED=new Color(232,225,213), LINE=new Color(81,72,58);
    private final SlayerTrackerPlugin plugin;
    private final Icon slayerIcon;
    private final JPanel cards=new JPanel();
    private final JTextField search=new SearchField();
    private final JComboBox<String> tab=new JComboBox<>(new String[]{"Overview","Monsters","History"});
    private final JComboBox<String> sort=new JComboBox<>(new String[]{"Kills","Tasks","Points","Name"});
    private final JLabel sortLabel=label("Sort By",MUTED,false);
    private final Set<String> expanded=new HashSet<>();
    private Ledger.Data data;private Ledger.Sample sample;private Ledger.Stats active;private String status;private long token=-1;private String lastSignature="";
    TrackerPanel(SlayerTrackerPlugin plugin,Icon slayerIcon){
        super(false);this.plugin=plugin;this.slayerIcon=slayerIcon;setLayout(new BorderLayout());setBackground(BG);
        JPanel top=new JPanel();top.setBackground(BG);top.setBorder(new EmptyBorder(8,7,4,7));top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));JLabel title=label("Slayer Tracker",GOLD,true);title.setHorizontalAlignment(SwingConstants.CENTER);title.setAlignmentX(Component.CENTER_ALIGNMENT);title.setMaximumSize(new Dimension(Integer.MAX_VALUE,title.getPreferredSize().height));top.add(title);top.add(Box.createVerticalStrut(5));top.add(tab);top.add(search);search.setToolTipText("Search assignments");sortLabel.setLabelFor(sort);sortLabel.setHorizontalAlignment(SwingConstants.CENTER);sortLabel.setBorder(new EmptyBorder(5,0,0,0));sortLabel.setAlignmentX(Component.CENTER_ALIGNMENT);sortLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,sortLabel.getPreferredSize().height));top.add(sortLabel);top.add(sort);
        add(top,BorderLayout.NORTH);
        cards.setLayout(new BoxLayout(cards,BoxLayout.Y_AXIS));cards.setBackground(BG);cards.setBorder(new EmptyBorder(10,7,12,7));JScrollPane scroll=new JScrollPane(cards);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUI(new TrackerScrollBarUI());scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8,0));scroll.getVerticalScrollBar().setUnitIncrement(16);add(scroll,BorderLayout.CENTER);
        applyControlFonts(top);
        tab.addActionListener(e->render());sort.addActionListener(e->render());search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){render();}public void removeUpdate(DocumentEvent e){render();}public void changedUpdate(DocumentEvent e){render();}});
        status="Waiting for login";render();
    }
    void showData(Ledger.Data d,Ledger.Sample s,Ledger.Stats active,String msg,long token){if(this.token!=token){expanded.clear();expanded.add("@current");lastSignature="";}this.token=token;data=d;sample=s;this.active=active;status=msg;String sig=msg+"|"+(d==null?"":plugin.serializeData(d))+"|"+(s==null?"":s.key+":"+s.remaining+":"+s.total+":"+s.points);if(!sig.equals(lastSignature)){lastSignature=sig;render();}}
    private static JLabel label(String s,Color color,boolean bold){JLabel l=new JLabel(s);l.setForeground(color);l.setFont(bold?FontManager.getRunescapeBoldFont():FontManager.getRunescapeFont());return l;}
    private static final class SearchField extends JTextField {
        SearchField(){getAccessibleContext().setAccessibleName("Search monsters");}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);
            if(!getText().isEmpty())return;
            Graphics copy=graphics.create();
            try{copy.setColor(new Color(150,150,150));copy.setFont(getFont());Insets insets=getInsets();FontMetrics metrics=copy.getFontMetrics();copy.clipRect(insets.left,insets.top,Math.max(0,getWidth()-insets.left-insets.right),Math.max(0,getHeight()-insets.top-insets.bottom));copy.drawString("Search monsters…",insets.left,(getHeight()-metrics.getHeight())/2+metrics.getAscent());}finally{copy.dispose();}
        }
    }
    private static void applyControlFonts(Component component){if(component instanceof AbstractButton||component instanceof JTextField||component instanceof JComboBox)component.setFont(FontManager.getRunescapeFont());if(component instanceof Container)for(Component child:((Container)component).getComponents())applyControlFonts(child);}
    private static final class TrackerScrollBarUI extends BasicScrollBarUI {
        private static final Color TRACK=new Color(29,27,24), THUMB=new Color(120,105,78);
        @Override protected JButton createDecreaseButton(int orientation){return hiddenButton();}
        @Override protected JButton createIncreaseButton(int orientation){return hiddenButton();}
        private JButton hiddenButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle bounds){g.setColor(TRACK);g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);}
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle bounds){if(bounds.isEmpty()||!scrollbar.isEnabled())return;g.setColor(THUMB);g.fillRoundRect(bounds.x+1,bounds.y,Math.max(3,bounds.width-2),bounds.height,5,5);}
    }
    private static JPanel card(){JPanel c=new JPanel();c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));c.setBackground(CARD);c.setBorder(new EmptyBorder(10,7,10,7));c.setAlignmentX(Component.LEFT_ALIGNMENT);return c;}
    static void alignCardContents(JPanel card){for(Component child:card.getComponents())if(child instanceof JComponent)((JComponent)child).setAlignmentX(Component.LEFT_ALIGNMENT);}
    private void addCard(JPanel c){alignCardContents(c);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height));cards.add(c);cards.add(Box.createVerticalStrut(8));}
    private void header(JPanel c,String title,String key){header(c,title,key,TEXT);}
    private void header(JPanel c,String title,String key,Color color){JPanel h=new JPanel(new BorderLayout(5,0));h.setOpaque(false);JLabel l=label(title,color,true);if(key!=null)l.setIcon(slayerIcon);h.add(l,BorderLayout.CENTER);if(key!=null){JButton b=new JButton("−");b.setFont(FontManager.getRunescapeFont());b.setMargin(new Insets(0,0,0,0));b.setPreferredSize(new Dimension(20,20));b.setToolTipText(expanded.contains(key)?"Collapse":"Expand");b.addActionListener(e->{if(!expanded.add(key))expanded.remove(key);render();});JPanel control=new JPanel(new GridBagLayout());control.setOpaque(false);control.add(b);h.add(control,BorderLayout.EAST);}c.add(h);c.add(Box.createVerticalStrut(6));}
    private static String fmt(long n){return String.format(Locale.US,"%,d",n);}
    private static String title(String s){StringBuilder b=new StringBuilder();for(String w:s.split(" ")){if(w.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));}return b.toString();}
    private static void row(JPanel c,String name,long n){row(c,name,fmt(n));}
    private static JPanel row(JPanel c,String name,String value){JPanel r=new JPanel(new BorderLayout(4,0));r.setOpaque(false);r.add(label(name,MUTED,false),BorderLayout.WEST);r.add(label(value,TEXT,false),BorderLayout.EAST);c.add(r);c.add(Box.createVerticalStrut(5));return r;}
    private static void killRow(JPanel c,String label,long count){JPanel r=row(c,label,fmt(count));String tip="Estimated from observed combat; counts may be incomplete or inaccurate.";r.setToolTipText(tip);for(Component child:r.getComponents())if(child instanceof JComponent)((JComponent)child).setToolTipText(tip);}
    private static void divider(JPanel c){JSeparator s=new JSeparator();s.setForeground(LINE);s.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));c.add(s);c.add(Box.createVerticalStrut(5));}
    private void render(){cards.removeAll();boolean enabled=data!=null;String selected=(String)tab.getSelectedItem();search.setVisible("Monsters".equals(selected));sortLabel.setVisible("Monsters".equals(selected));sort.setVisible("Monsters".equals(selected));if(!enabled){JPanel c=card();header(c,status==null?"Waiting for login":status,null);addCard(c);}else if("Overview".equals(selected))overview();else if("Monsters".equals(selected))monsters();else history();cards.revalidate();cards.repaint();}
    private void overview(){JPanel s=card();header(s,"Slayer Statistics",null,GOLD);row(s,"Current Points:",sample==null?"—":fmt(sample.points));row(s,"Points Earned:",data.earned);row(s,"Points Spent:",data.spent);divider(s);row(s,"Tasks Completed:",data.completed);row(s,"Tasks Assigned:",data.tasks.values().stream().mapToLong(a->a.assigned).sum());row(s,"Tasks Skipped:",data.tasks.values().stream().mapToLong(a->a.skipped).sum());killRow(s,"Tasks Kill Total:",data.tasks.values().stream().mapToLong(a->a.kills).sum());addCard(s);
        JPanel c=card();header(c,"Current Task","@current");if(sample!=null&&sample.active()){JLabel creature=label(title(sample.name),GOLD,true);creature.setHorizontalAlignment(SwingConstants.LEFT);creature.setAlignmentX(Component.LEFT_ALIGNMENT);creature.setMaximumSize(new Dimension(Integer.MAX_VALUE,creature.getPreferredSize().height));c.add(creature);c.add(Box.createVerticalStrut(5));if(sample.total>0&&sample.remaining<=sample.total){int p=(int)(1000L*(sample.total-sample.remaining)/sample.total);row(c,"Task Progress:",String.format(Locale.US,"%.1f%%",p/10d));JProgressBar b=new JProgressBar(0,1000);b.setUI(new BasicProgressBarUI());b.setValue(p);b.setForeground(new Color(230,180,79));b.setBackground(new Color(23,20,15));b.setBorderPainted(false);b.setPreferredSize(new Dimension(200,10));b.setMaximumSize(new Dimension(Integer.MAX_VALUE,10));c.add(b);c.add(Box.createVerticalStrut(5));}row(c,"Start Kills:",sample.total>0?fmt(sample.total):"—");row(c,"Kills Left:",sample.remaining);if(expanded.contains("@current")&&active!=null){divider(c);row(c,"Slayer XP:",active.xp);}}else c.add(label("No active assignment",MUTED,false));addCard(c);}
    private void monsters(){List<Map.Entry<String,Ledger.Stats>> list=new ArrayList<>(data.tasks.entrySet());String q=MonsterNames.normal(search.getText());list.removeIf(e->!e.getKey().contains(q));Comparator<Map.Entry<String,Ledger.Stats>> cmp=Map.Entry.comparingByKey();String by=(String)sort.getSelectedItem();if(!"Name".equals(by))cmp=Comparator.<Map.Entry<String,Ledger.Stats>>comparingLong(e->"Tasks".equals(by)?e.getValue().completed:"Points".equals(by)?e.getValue().points:e.getValue().kills).reversed().thenComparing(Map.Entry.comparingByKey());list.sort(cmp);for(Map.Entry<String,Ledger.Stats> e:list){Ledger.Stats s=e.getValue();JPanel c=card();header(c,title(e.getKey()),e.getKey());c.add(label("Tasks Completed: "+fmt(s.completed),GOLD,true));c.add(Box.createVerticalStrut(5));killRow(c,"Kills:",s.kills);if(expanded.contains(e.getKey())){divider(c);row(c,"Tasks Assigned:",s.assigned);row(c,"Tasks Skipped:",s.skipped);row(c,"Points Earned:",s.points);row(c,"Slayer XP:",s.xp);divider(c);c.add(label("Individual Kills",GOLD,true));s.monsters.forEach((n,k)->row(c,title(n)+":",k));}addCard(c);} }
    private void history(){int size=data.history.size();for(int i=size-1;i>=Math.max(0,size-100);i--){Ledger.Record r=data.history.get(i);JPanel c=card();header(c,title(r.task),null,GOLD);if(!"Completed".equals(r.result))c.add(label(r.result,TEXT,true));killRow(c,"Kills:",r.kills);row(c,"Points Earned:",r.points);row(c,"Slayer XP:",r.xp);addCard(c);}if(size>100){JPanel c=card();c.add(label("Showing latest 100 records",MUTED,false));addCard(c);}}
}
