package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class ComponentSupportAnalysisTest {
    @Test void incrementalSupportWithBudgetOneMatchesReference() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        long id=rock(w,r,3,18,5,1); for(int x=4;x<=6;x++) w.set(x,18,Material.AIR);
        var analysis=new IncrementalComponentSupport(w,t,r,id,10,20); long max=0; int calls=0;
        IncrementalComponentSupport.Status status;
        do { status=analysis.step(1); max=Math.max(max,analysis.lastWork()); assertTrue(++calls<1000); }
        while(status==IncrementalComponentSupport.Status.IN_PROGRESS);
        assertEquals(IncrementalComponentSupport.Status.COMPLETE,status); assertEquals(1,max);
        var expected=SettledSupportGraph.analyzeComponents(w,t,r,List.of(id),10,20,10000);
        assertEquals(expected.components().stream().map(ComponentSupportAnalysis.Component::supported).toList(),analysis.components().stream().map(IncrementalComponentSupport.Component::supported).toList());
        assertEquals(expected.components().stream().map(ComponentSupportAnalysis.Component::cells).toList(),analysis.components().stream().map(IncrementalComponentSupport.Component::cells).toList());
        System.out.println("incremental support maxWork="+max+" calls="+calls);
    }
    @Test void incrementalBridgeAndMutationRestart() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        long a=rock(w,r,3,18,1,1); rock(w,r,5,18,1,2); rock(w,r,3,17,3,3); rock(w,r,4,16,1,4);
        var stale=new IncrementalComponentSupport(w,t,r,a,10,30);
        for(int i=0;i<8;i++) assertEquals(IncrementalComponentSupport.Status.IN_PROGRESS,stale.step(1));
        w.set(5,18,Material.AIR); assertEquals(IncrementalComponentSupport.Status.STALE,stale.step(1));
        assertEquals(0,stale.lastWork()); assertThrows(IllegalStateException.class,stale::components);
        var fresh=new IncrementalComponentSupport(w,t,r,a,10,30); int calls=0; IncrementalComponentSupport.Status status;
        do { status=fresh.step(1); assertTrue(fresh.lastWork()<=1); assertTrue(++calls<2000); } while(status==IncrementalComponentSupport.Status.IN_PROGRESS);
        assertEquals(IncrementalComponentSupport.Status.COMPLETE,status);
        var expected=SettledSupportGraph.analyzeComponents(w,t,r,List.of(a),10,30,100000);
        assertEquals(expected.components().stream().map(ComponentSupportAnalysis.Component::supported).toList(),fresh.components().stream().map(IncrementalComponentSupport.Component::supported).toList());
    }
    @Test void largeComponentAnalysisRemainsBoundedAndReadOnly() {
        var w=new World(128,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,200);
        long id=rock(w,r,10,18,100,1); w.set(60,18,Material.AIR);
        var graph=new IncrementalComponentSupport(w,t,r,id,10,200); long revision=w.mutationRevision(); int calls=0; IncrementalComponentSupport.Status status;
        do { status=graph.step(1); assertTrue(graph.lastWork()<=1); assertTrue(++calls<10000); assertEquals(revision,w.mutationRevision()); }
        while(status==IncrementalComponentSupport.Status.IN_PROGRESS);
        assertEquals(IncrementalComponentSupport.Status.COMPLETE,status); assertTrue(calls>100);
        assertEquals(2,graph.components().size()); assertTrue(graph.components().stream().allMatch(IncrementalComponentSupport.Component::supported));
        assertEquals(99,graph.components().stream().mapToInt(c->c.cells().size()).sum());
        assertEquals(99,r.rock(id).remainingCells());
    }
    @Test void incrementalEnumerationUsesOneUnitAndRejectsStaleInput() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        long id=rock(w,r,3,18,5,1); for(int x=4;x<=6;x++) w.set(x,18,Material.AIR);
        var enumeration=new ComponentEnumeration(w,t,r,id,20); int calls=0; long max=0;
        while(enumeration.step(1)==ComponentEnumeration.Status.IN_PROGRESS) { calls++; max=Math.max(max,enumeration.lastWork()); assertTrue(calls<200); }
        max=Math.max(max,enumeration.lastWork()); assertEquals(1,max); assertTrue(calls>1);
        System.out.println("component enumeration maxWork="+max+" calls="+(calls+1));
        assertEquals(r.connectedComponents(id),enumeration.components());
        var stale=new ComponentEnumeration(w,t,r,id,20); assertEquals(ComponentEnumeration.Status.IN_PROGRESS,stale.step(1));
        w.set(3,18,Material.AIR); assertEquals(ComponentEnumeration.Status.STALE,stale.step(1));
        assertEquals(0,stale.lastWork()); assertThrows(IllegalStateException.class,stale::components);
    }
    private static FragmentDescriptor row(int width) {
        var cells=new ArrayList<FragmentDescriptor.Cell>(); for(int x=0;x<width;x++) cells.add(new FragmentDescriptor.Cell(x,0));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    private static long rock(World w,SettledRockRegistry registry,int x,int y,int width,long source) {
        for(int dx=0;dx<width;dx++) w.set(x+dx,y,Material.STONE);
        return registry.registerExisting(source,row(width),x,y);
    }
    private static ComponentSupportAnalysis.Result analyze(World w,RockConnectivity t,SettledRockRegistry r,List<Long> seeds,long budget) {
        return SettledSupportGraph.analyzeComponents(w,t,r,seeds,20,100,budget);
    }
    @Test void splitBottomAndUnsupportedUpperAreIndependentReadOnlyAndDeterministic() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        long a=rock(w,r,3,18,5,1),b=rock(w,r,4,17,3,2),c=rock(w,r,4,16,3,3);
        for(int x=4;x<=6;x++) w.set(x,18,Material.AIR);
        while(t.pending()) t.advance(1000);
        var prepared=r.prepareComponentReactivationRemoval(List.of(new SettledRockRegistry.ComponentReactivationMapping(a,42,r.connectedComponents(a).getFirst())));
        assertTrue(prepared.valid());
        long worldRevision=w.mutationRevision(),topologyRevision=t.revision(),ownershipRevision=r.revision(); var before=r.snapshot();
        var result=analyze(w,t,r,List.of(a,b,c),100000); assertEquals(SettledSupportGraph.Status.COMPLETE,result.status());
        assertEquals(result,analyze(w,t,r,List.of(c,a,b),100000));
        assertEquals(List.of(true,true,false,false),result.components().stream().map(ComponentSupportAnalysis.Component::supported).toList());
        assertEquals(List.of(a,a,b,c),result.components().stream().map(v->v.key().settledId()).toList());
        assertEquals(List.of(3,7,4,4),result.components().stream().map(v->v.key().anchorX()).toList());
        assertEquals(before,r.snapshot()); assertEquals(worldRevision,w.mutationRevision()); assertEquals(topologyRevision,t.revision()); assertEquals(ownershipRevision,r.revision());
        assertTrue(prepared.valid());
        assertEquals(SettledSupportGraph.Status.LIMIT,analyze(w,t,r,List.of(a,b,c),1).status());
        assertEquals(before,r.snapshot());
    }
    @Test void oneOwnerCanContainSupportedAndUnsupportedComponents() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(1,1)));
        for(var cell:shape.cells()) w.set(3+cell.x(),17+cell.y(),Material.STONE);
        long id=r.registerExisting(1,shape,3,17); w.set(4,17,Material.AIR); w.set(4,18,Material.AIR);
        var result=analyze(w,t,r,List.of(id),10000); assertEquals(SettledSupportGraph.Status.COMPLETE,result.status());
        assertEquals(2,result.components().size());
        assertEquals(List.of(false,true),result.components().stream().map(ComponentSupportAnalysis.Component::supported).toList());
        assertEquals(List.of(id,id),result.components().stream().map(v->v.key().settledId()).toList());
    }
    @Test void stackAndBridgeBalanceUseActualLoads() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var r=new SettledRockRegistry(w,t,10,20);
        long a=rock(w,r,3,18,1,1),b=rock(w,r,5,18,1,2),bridge=rock(w,r,3,17,3,3),top=rock(w,r,4,16,1,4);
        var result=analyze(w,t,r,List.of(a),100000); assertEquals(SettledSupportGraph.Status.COMPLETE,result.status());
        assertEquals(List.of(a,b,bridge,top),result.components().stream().map(v->v.key().settledId()).toList());
        assertTrue(result.components().stream().allMatch(ComponentSupportAnalysis.Component::supported),result.toString());
    }
}
