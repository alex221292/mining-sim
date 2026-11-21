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
