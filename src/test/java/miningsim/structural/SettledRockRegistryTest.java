package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.fragment.ExtractionPlanner;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettledRockRegistryTest {
    private static FragmentDescriptor shape(miningsim.resource.SubstanceId substance,int count) {
        var cells=new java.util.ArrayList<FragmentDescriptor.Cell>();
        for(int x=0;x<count;x++) cells.add(new FragmentDescriptor.Cell(x,0));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(substance),cells);
    }
    private static void complete(RockConnectivity connectivity) { while(connectivity.pending()) connectivity.advance(17); }
    @Test void ownershipSeparatesBondsAndComponentsWithoutLosingSubstance() {
        var world=new World(12,10,World.Mode.BEDROCK_ENABLED);
        for(int x=2;x<=6;x++) world.set(x,5,Material.STONE);
        var connectivity=new RockConnectivity(world); complete(connectivity);
        var registry=new SettledRockRegistry(world,connectivity,10,10);
        long oldIncarnation=connectivity.bondIncarnation(2,5,3,5);
        long marble=registry.registerExisting(41,shape(SubstanceRegistry.MARBLE,2),3,5);
        long granite=registry.registerExisting(42,shape(SubstanceRegistry.GRANITE,1),5,5);
        assertEquals(1,marble); assertEquals(2,granite);
        assertEquals(0,registry.ownerAt(2,5)); assertEquals(marble,registry.ownerAt(3,5));
        assertEquals(RockConnectivity.BondState.ABSENT,connectivity.bondState(2,5,3,5));
        assertEquals(RockConnectivity.BondState.INTACT,connectivity.bondState(3,5,4,5));
        assertEquals(RockConnectivity.BondState.ABSENT,connectivity.bondState(4,5,5,5));
        assertEquals(RockConnectivity.BondState.ABSENT,connectivity.bondState(5,5,6,5));
        assertThrows(IllegalStateException.class,()->connectivity.bondIncarnation(2,5,3,5));
        assertThrows(IllegalArgumentException.class,()->connectivity.breakBond(4,5,5,5));
        assertTrue(connectivity.pending()); complete(connectivity);
        assertNotEquals(connectivity.componentAt(2,5),connectivity.componentAt(3,5));
        assertEquals(connectivity.componentAt(3,5),connectivity.componentAt(4,5));
        assertNotEquals(connectivity.componentAt(4,5),connectivity.componentAt(5,5));
        var rock=registry.snapshot().getFirst(); assertEquals(41,rock.sourceFragmentId());
        assertEquals(SubstanceRegistry.MARBLE,rock.descriptor().substance().id()); assertEquals(5_400_000,rock.massMilligrams());
        assertEquals(List.of(new SettledRockRegistry.WorldCell(3,5),new SettledRockRegistry.WorldCell(4,5)),registry.cells(marble));
        assertTrue(connectivity.revision()>oldIncarnation);
    }
    @Test void removalUpdatesOwnershipMassAndPreventsImplicitReweld() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED);
        world.set(2,3,Material.STONE); world.set(3,3,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
        long id=registry.registerExisting(7,shape(SubstanceRegistry.MARBLE,2),2,3);
        world.set(2,3,Material.AIR);
        var after=registry.snapshot().getFirst(); assertEquals(1,after.remainingCells()); assertEquals(2_700_000,after.massMilligrams());
        assertEquals(SettledRockRegistry.Integrity.DAMAGED,after.integrity()); assertEquals(0,registry.ownerAt(2,3));
        world.set(2,3,Material.STONE);
        assertEquals(RockConnectivity.BondState.ABSENT,connectivity.bondState(2,3,3,3));
        assertEquals(List.of(new SettledRockRegistry.WorldCell(3,3)),registry.cells(id));
        world.set(3,3,Material.AIR); assertEquals(SettledRockRegistry.Integrity.REMOVED,registry.snapshot().getFirst().integrity());
        assertEquals(0,registry.snapshot().getFirst().massMilligrams());
    }
    @Test void rejectedClaimsAndTraversalPreserveState() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); world.set(2,3,Material.STONE); world.set(3,3,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,1,2);
        connectivity.breakBond(2,3,3,3); long revision=connectivity.revision();
        assertThrows(IllegalArgumentException.class,()->registry.registerExisting(1,shape(SubstanceRegistry.STONE,2),2,3));
        assertEquals(revision,connectivity.revision()); assertTrue(registry.snapshot().isEmpty());
        assertThrows(IllegalArgumentException.class,()->registry.registerExisting(1,shape(SubstanceRegistry.STONE,1),8,8));
        connectivity.forEachBond(2,3,4,4,(a,b,c,d,state)->assertThrows(IllegalStateException.class,()->registry.registerExisting(1,shape(SubstanceRegistry.STONE,1),2,3)));
        assertTrue(registry.snapshot().isEmpty());
    }
    @Test void worldClearAndSinkReplacementRespectOwnership() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); world.set(2,3,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
        registry.registerExisting(1,shape(SubstanceRegistry.STONE,1),2,3);
        assertThrows(IllegalStateException.class,()->world.setChangeSink(null));
        assertThrows(IllegalStateException.class,()->world.setChangeSink(connectivity)); assertEquals(1,registry.ownerAt(2,3));
        world.clear(); assertEquals(0,registry.ownerAt(2,3)); assertEquals(SettledRockRegistry.Integrity.REMOVED,registry.snapshot().getFirst().integrity());
        assertEquals(Material.BEDROCK,world.get(2,9)); world.setChangeSink(null);
    }
    @Test void genericExtractorCannotEraseSettledSubstance() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); world.set(2,3,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
        registry.registerExisting(7,shape(SubstanceRegistry.MARBLE,1),2,3); complete(connectivity);
        assertEquals(ExtractionPlanner.Status.SETTLED_OWNERSHIP,ExtractionPlanner.prepare(world,connectivity,2,3,4).status());
        assertEquals(SubstanceRegistry.MARBLE,registry.snapshot().getFirst().descriptor().substance().id());
        assertEquals(Material.STONE,world.get(2,3));
    }
    @Test void breakingInternalBondMarksIntegrityWithoutChangingMass() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); world.set(2,3,Material.STONE); world.set(3,3,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
        long id=registry.registerExisting(1,shape(SubstanceRegistry.MARBLE,2),2,3); long revision=registry.revision();
        assertTrue(connectivity.breakBond(2,3,3,3));
        var record=registry.snapshot().getFirst(); assertEquals(SettledRockRegistry.Integrity.DAMAGED,record.integrity());
        assertEquals(5_400_000,record.massMilligrams()); assertEquals(revision+1,registry.revision());
        assertEquals(id,registry.ownerAt(2,3)); assertEquals(id,registry.ownerAt(3,3));
        complete(connectivity); assertNotEquals(connectivity.componentAt(2,3),connectivity.componentAt(3,3));
    }
    @Test void bedrockTouchIsNotASettledRockBond() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); world.set(2,8,Material.STONE); world.set(5,8,Material.STONE);
        var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
        registry.registerExisting(1,shape(SubstanceRegistry.STONE,1),2,8); complete(connectivity);
        int settledOrdinal=connectivity.componentOrdinal(connectivity.componentAt(2,8));
        int ordinaryOrdinal=connectivity.componentOrdinal(connectivity.componentAt(5,8));
        assertFalse(connectivity.foundationConnected(settledOrdinal));
        assertTrue(connectivity.foundationConnected(ordinaryOrdinal));
        assertEquals(Material.BEDROCK,world.get(2,9));
    }
    @Test void seamBatchAndDeterministicReplayPreserveOwnership() {
        var results=new java.util.ArrayList<String>();
        for(int run=0;run<2;run++) {
            var world=new World(130,20,World.Mode.BEDROCK_ENABLED);
            world.set(63,5,Material.STONE); world.set(64,5,Material.STONE); world.set(65,5,Material.STONE);
            var connectivity=new RockConnectivity(world); var registry=new SettledRockRegistry(world,connectivity,4,4);
            long id=registry.registerExisting(9,shape(SubstanceRegistry.GRANITE,2),63,5);
            assertEquals(RockConnectivity.BondState.INTACT,connectivity.bondState(63,5,64,5));
            assertEquals(RockConnectivity.BondState.ABSENT,connectivity.bondState(64,5,65,5));
            long before=registry.revision();
            assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(List.of(new World.CellChange(63,5,Material.STONE,Material.AIR))));
            assertEquals(before+1,registry.revision()); assertEquals(2_700_000,registry.snapshot().getFirst().massMilligrams());
            assertEquals(List.of(new SettledRockRegistry.WorldCell(64,5)),registry.cells(id));
            complete(connectivity); var rock=registry.snapshot().getFirst();
            results.add(rock.id()+":"+rock.sourceFragmentId()+":"+rock.massMilligrams()+":"+rock.generation()+":"+rock.integrity()+":"+registry.cells(id)+":"+connectivity.componentAt(64,5));
        }
        assertEquals(results.getFirst(),results.getLast());
    }
}
