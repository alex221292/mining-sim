package miningsim.structural;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import miningsim.resource.MassAccounting;
import miningsim.world.Material;
import miningsim.world.World;

/** Owner-thread resumable component analysis. Every call performs at most its work budget. */
public final class IncrementalComponentSupport {
    public enum Status { IN_PROGRESS, COMPLETE, STALE, LIMIT, INVALID }
    public record Component(ComponentSupportAnalysis.Key key,List<SettledRockRegistry.WorldCell> cells,boolean supported,String reason) { }
    private record Contact(ComponentSupportAnalysis.Key below,double left,double right) { }
    private static final class Node {
        final ComponentSupportAnalysis.Key key; final List<SettledRockRegistry.WorldCell> cells;
        final long mass; final ArrayList<Contact> contacts=new ArrayList<>(); final TreeSet<ComponentSupportAnalysis.Key> uppers=new TreeSet<>();
        double sum,load,moment,unknownLeft=Double.POSITIVE_INFINITY,unknownRight=Double.NEGATIVE_INFINITY;
        ComponentSupportAnalysis.Key chosenA,chosenB; int incoming; boolean grounded,forceDone,forceFailed,stable,broken;
        Node(ComponentSupportAnalysis.Key key,List<SettledRockRegistry.WorldCell> cells,long mass) { this.key=key; this.cells=cells; this.mass=mass; }
    }
    private final World world; private final RockConnectivity topology; private final SettledRockRegistry settled;
    private final long root;
    private final int maxRocks,maxCells; private final long terrain,revision,ownership;
    private final TreeSet<Long> pending=new TreeSet<>(); private final HashSet<Long> visited=new HashSet<>();
    private final TreeMap<ComponentSupportAnalysis.Key,Node> nodes=new TreeMap<>(); private final HashMap<Long,ComponentSupportAnalysis.Key> owners=new HashMap<>();
    private final TreeSet<ComponentSupportAnalysis.Key> ready=new TreeSet<>(); private HashSet<ComponentSupportAnalysis.Key> targets=new HashSet<>();
    private final TreeMap<ComponentSupportAnalysis.Key,ComponentSupportAnalysis.Key> parents=new TreeMap<>();
    private final TreeSet<ComponentSupportAnalysis.Key> assemblyRoots=new TreeSet<>();
    private final ArrayList<Component> results=new ArrayList<>(); private final List<Component> resultView=Collections.unmodifiableList(results);
    private final ArrayList<Component> unsupported=new ArrayList<>(); private final List<Component> unsupportedView=Collections.unmodifiableList(unsupported);
    private final ArrayList<Long> covered=new ArrayList<>(); private final List<Long> coveredView=Collections.unmodifiableList(covered);
    private long lastCovered;
    private ComponentEnumeration enumeration; private SettledRockRegistry.Rock rock; private List<List<SettledRockRegistry.WorldCell>> groups;
    private Iterator<Node> iterator; private Node current; private Contact first,last; private int phase,groupIndex,cellIndex,subIndex,contactIndex,cells;
    private boolean changed; private double left,right; private long total,lastWork;
    private ComponentSupportAnalysis.Key findA,findB;
    private int stableCount,stableContacts;
    private Status status=Status.IN_PROGRESS;
    public IncrementalComponentSupport(World world,RockConnectivity topology,SettledRockRegistry settled,long root,int maxRocks,int maxCells) {
        if(!settled.owns(world,topology) || root<=0 || maxRocks<1 || maxCells<1) throw new IllegalArgumentException("Invalid support analysis inputs");
        this.world=world; this.topology=topology; this.settled=settled; this.root=root; this.maxRocks=maxRocks; this.maxCells=maxCells;
        covered.add(root);
        terrain=world.rigidTerrainRevision(); revision=topology.revision(); ownership=settled.revision(); pending.add(root);
    }
    public long lastWork() { return lastWork; }
    public long work() { return total; }
    private boolean stale() { return world.rigidTerrainRevision()!=terrain || topology.revision()!=revision || settled.revision()!=ownership; }
    public List<Component> components() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return resultView; }
    public List<Component> unsupported() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return unsupportedView; }
    public List<Long> coveredIds() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return coveredView; }
    public int assemblyCount() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return assemblyRoots.size(); }
    public int supportedCount() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return stableCount; }
    public int contactCount() { if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale"); return stableContacts; }
    private void use() { lastWork++; total++; }
    private static int compare(ComponentSupportAnalysis.Key a,ComponentSupportAnalysis.Key b) { if(a==null) return b==null?0:-1; return b==null?1:a.compareTo(b); }
    private void nextPass(int value) { iterator=nodes.values().iterator(); current=null; phase=value; }
    public Status step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Budget must be positive"); lastWork=0;
        if(stale()) { status=Status.STALE; return status; }
        while(lastWork<budget && status==Status.IN_PROGRESS) {
            if(phase==0) {
                if(pending.isEmpty()) { nextPass(4); continue; }
                long id=pending.pollFirst(); use(); if(!visited.add(id)) continue;
                if(visited.size()>maxRocks) { status=Status.LIMIT; break; }
                rock=settled.rock(id); if(rock.remainingCells()==0) continue;
                if(rock.descriptor().cellCount()>maxCells-cells) { status=Status.LIMIT; break; }
                cells+=rock.descriptor().cellCount(); enumeration=new ComponentEnumeration(world,topology,settled,id,maxCells); phase=1;
            } else if(phase==1) {
                var state=enumeration.step(1); lastWork+=enumeration.lastWork(); total+=enumeration.lastWork();
                if(state==ComponentEnumeration.Status.STALE) { status=Status.STALE; break; }
                if(state==ComponentEnumeration.Status.LIMIT) { status=Status.LIMIT; break; }
                if(state==ComponentEnumeration.Status.INVALID) { status=Status.INVALID; break; }
                if(state==ComponentEnumeration.Status.COMPLETE) { groups=enumeration.components(); groupIndex=0; current=null; phase=2; }
            } else if(phase==2) {
                if(groupIndex>=groups.size()) { groups=null; enumeration=null; phase=0; continue; }
                var list=groups.get(groupIndex++); var anchor=list.getFirst();
                var key=new ComponentSupportAnalysis.Key(rock.id(),anchor.y(),anchor.x());
                long mass=Math.multiplyExact(MassAccounting.cellMilligrams(rock.descriptor().substance()),list.size());
                current=new Node(key,list,mass); nodes.put(key,current); cellIndex=0; subIndex=0; phase=3; use();
            } else if(phase==3) {
                if(cellIndex>=current.cells.size()) { current.load=current.mass; current.moment=current.mass*current.sum/current.cells.size(); phase=2; continue; }
                var c=current.cells.get(cellIndex);
                if(subIndex==0) { owners.put((long)c.y()*world.width()+c.x(),current.key); current.sum+=c.x()+0.5;
                    if(world.get(c.x(),c.y())!=Material.STONE) status=Status.INVALID; }
                else { int y=c.y()+(subIndex==1?-1:1); if(y>=0 && y<world.height()) { long id=settled.ownerAt(c.x(),y); if(id>0 && !visited.contains(id)) pending.add(id); } }
                subIndex++; if(subIndex==3) { subIndex=0; cellIndex++; } use();
            } else if(phase==4) {
                if(current==null) { if(!iterator.hasNext()) { nextPass(5); changed=false; continue; } current=iterator.next(); cellIndex=0; subIndex=0; use(); continue; }
                if(cellIndex>=current.cells.size()) { current=null; continue; }
                var c=current.cells.get(cellIndex); int x=c.x(),y=c.y();
                if(subIndex<2) { int nx=x+(subIndex==0?1:0),ny=y+(subIndex==1?1:0);
                    if(world.contains(nx,ny) && current.key.equals(owners.get((long)ny*world.width()+nx)) && topology.bondState(x,y,nx,ny)==RockConnectivity.BondState.BROKEN) current.broken=true;
                } else if(subIndex==2 && y+1<world.height()) {
                    var material=world.get(x,y+1); var below=owners.get((long)(y+1)*world.width()+x);
                    if(material==Material.BEDROCK || material==Material.STONE && below==null && settled.ownerAt(x,y+1)==0 && y+2<world.height() && world.get(x,y+2)==Material.BEDROCK) current.contacts.add(new Contact(null,x,x+1.0));
                    else if(below!=null && !below.equals(current.key)) { current.contacts.add(new Contact(below,x,x+1.0)); nodes.get(below).uppers.add(current.key); }
                } else if(subIndex==3 && y>0 && world.get(x,y-1)==Material.STONE && settled.ownerAt(x,y-1)==0) {
                    current.unknownLeft=Math.min(current.unknownLeft,x); current.unknownRight=Math.max(current.unknownRight,x+1.0);
                }
                if(++subIndex==4) { subIndex=0; cellIndex++; } use();
            } else if(phase==5) {
                if(current==null) { if(!iterator.hasNext()) { if(changed) { changed=false; nextPass(5); } else nextPass(6); continue; }
                    current=iterator.next(); contactIndex=0; use(); continue; }
                if(current.grounded || contactIndex>=current.contacts.size()) { current=null; continue; }
                var c=current.contacts.get(contactIndex++); if(c.below()==null || nodes.get(c.below()).grounded) { current.grounded=true; changed=true; } use();
            } else if(phase==6) {
                if(!iterator.hasNext()) { current=null; phase=7; continue; }
                var n=iterator.next(); n.incoming=n.uppers.size(); parents.put(n.key,n.key); if(n.incoming==0) ready.add(n.key); use();
            } else if(phase==7) {
                if(current==null) { if(ready.isEmpty()) { changed=false; nextPass(9); continue; }
                    current=nodes.get(ready.pollFirst()); contactIndex=0; first=last=null; use(); continue; }
                if(contactIndex<current.contacts.size()) {
                    var c=current.contacts.get(contactIndex++); use(); if(c.below()!=null && !nodes.get(c.below()).grounded) continue;
                    if(first==null || c.left()<first.left() || c.left()==first.left() && compare(c.below(),first.below())<0) first=c;
                    if(last==null || c.right()>last.right() || c.right()==last.right() && compare(c.below(),last.below())<0) last=c;
                    continue;
                }
                double resultant=current.moment/current.load;
                current.forceFailed=first==null || resultant<=first.left()+1e-9 || resultant>=last.right()-1e-9 || current.unknownLeft<first.left()-1e-9 || current.unknownRight>last.right()+1e-9;
                if(!current.forceFailed) {
                    double share=(resultant-first.left())/(last.right()-first.left()); current.chosenA=first.below(); current.chosenB=last.below();
                    if(first.below()!=null) addLoad(nodes.get(first.below()),current.load*(1-share),first.left());
                    if(last.below()!=null) addLoad(nodes.get(last.below()),current.load*share,last.right());
                }
                current.forceDone=true; contactIndex=0; targets=new HashSet<>(); phase=8; use();
            } else if(phase==8) {
                if(contactIndex>=current.contacts.size()) { current=null; phase=7; continue; }
                var c=current.contacts.get(contactIndex++);
                if(c.below()!=null) { var below=nodes.get(c.below());
                    if(Double.isFinite(current.unknownLeft)) { below.unknownLeft=Math.min(below.unknownLeft,c.left()); below.unknownRight=Math.max(below.unknownRight,c.right()); }
                    if(targets.add(c.below()) && --below.incoming==0) ready.add(below.key);
                } use();
            } else if(phase==9) {
                if(current==null) { if(!iterator.hasNext()) { if(changed) { changed=false; nextPass(9); } else nextPass(11); continue; }
                    current=iterator.next(); contactIndex=0; left=Double.POSITIVE_INFINITY; right=Double.NEGATIVE_INFINITY; use(); continue; }
                if(current.stable || !current.forceDone || current.forceFailed || current.broken) { current=null; continue; }
                if(contactIndex<current.contacts.size()) { var c=current.contacts.get(contactIndex++); if(c.below()==null || nodes.get(c.below()).stable) { left=Math.min(left,c.left()); right=Math.max(right,c.right()); } use(); continue; }
                double center=current.moment/current.load;
                if((current.chosenA==null || nodes.get(current.chosenA).stable) && (current.chosenB==null || nodes.get(current.chosenB).stable)
                        && center>left+1e-9 && center<right-1e-9 && current.unknownLeft>=left-1e-9 && current.unknownRight<=right+1e-9) { current.stable=true; changed=true; }
                current=null; use();
            } else if(phase==11) {
                if(current==null) { if(!iterator.hasNext()) { nextPass(10); continue; } current=iterator.next(); contactIndex=0; use(); continue; }
                if(contactIndex>=current.contacts.size()) { current=null; continue; }
                var c=current.contacts.get(contactIndex++); use();
                if(current.stable && c.below()!=null && nodes.get(c.below()).stable) { findA=current.key; findB=c.below(); phase=12; }
            } else if(phase==12) {
                var parent=parents.get(findA); use(); if(!parent.equals(findA)) findA=parent; else phase=13;
            } else if(phase==13) {
                var parent=parents.get(findB); use(); if(!parent.equals(findB)) findB=parent; else phase=14;
            } else if(phase==14) {
                if(!findA.equals(findB)) { if(findA.compareTo(findB)<0) parents.put(findB,findA); else parents.put(findA,findB); }
                phase=11; use();
            } else if(phase==10) {
                if(current==null) { if(!iterator.hasNext()) { status=Status.COMPLETE; continue; } current=iterator.next(); use();
                    if(current.stable) { findA=current.key; phase=15; } else emit(current); continue; }
                throw new IllegalStateException("Invalid component output state");
            } else if(phase==15) {
                var parent=parents.get(findA); use();
                if(!parent.equals(findA)) findA=parent;
                else { assemblyRoots.add(findA); stableCount++; stableContacts+=current.contacts.size(); emit(current); phase=10; }
            } else {
                throw new IllegalStateException("Invalid support phase");
            }
        }
        if(status==Status.IN_PROGRESS && stale()) status=Status.STALE;
        return status;
    }
    private void emit(Node n) {
        String reason=n.stable?"":n.broken?"broken internal bond":!n.grounded?"no vertical foundation path":!n.forceDone?"cyclic load graph":n.forceFailed?"vertical force or moment imbalance":"support not certified";
        var component=new Component(n.key,n.cells,n.stable,reason); results.add(component); if(!n.stable) unsupported.add(component);
        if(n.key.settledId()!=lastCovered) { lastCovered=n.key.settledId(); if(lastCovered!=root) covered.add(lastCovered); }
        current=null;
    }
    private static void addLoad(Node node,double force,double x) { node.load+=force; node.moment+=force*x; }
}
