package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import miningsim.resource.*;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class ResourceContractsTest {
    private final SubstanceRegistry registry = SubstanceRegistry.reference();
    @Test void identityRegistryAndLegacy() {
        assertEquals("marble",SubstanceRegistry.MARBLE.value());
        assertEquals(2700,registry.get(SubstanceRegistry.MARBLE).densityKgPerM3());
        assertThrows(IllegalArgumentException.class,()->new SubstanceRegistry(List.of(registry.get(SubstanceRegistry.STONE),registry.get(SubstanceRegistry.STONE))));
        assertNotEquals(new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.PEBBLE),new MaterialSample(SubstanceRegistry.GRANITE,MaterialForm.PEBBLE));
        assertNotEquals(new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.PEBBLE),new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.GRIT));
        assertEquals(new MaterialSample(SubstanceRegistry.STONE,MaterialForm.SOLID_MASS),SubstanceRegistry.legacyCell(Material.STONE).orElseThrow());
        assertTrue(SubstanceRegistry.legacyCell(Material.GRAVEL).isEmpty());
        assertEquals(2700000,MassAccounting.cellMilligrams(registry.get(SubstanceRegistry.MARBLE)));
        assertEquals(0,MassAccounting.solidCellsMilligrams(registry.get(SubstanceRegistry.MARBLE),0));
        assertThrows(ArithmeticException.class,()->MassAccounting.solidCellsMilligrams(registry.get(SubstanceRegistry.MARBLE),Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->MassAccounting.solidCellsMilligrams(registry.get(SubstanceRegistry.MARBLE),-1));
    }
    private FragmentDescriptor fragment() {
        return new FragmentDescriptor(registry.get(SubstanceRegistry.MARBLE),List.of(new Cell(1,1),new Cell(0,0),new Cell(1,0),new Cell(0,1)));
    }
    @Test void fragmentAndSeededDebrisConserveMass() {
        var f=fragment(); assertEquals(List.of(new Cell(0,0),new Cell(1,0),new Cell(0,1),new Cell(1,1)),f.cells());
        assertThrows(IllegalArgumentException.class,()->new FragmentDescriptor(f.substance(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new FragmentDescriptor(f.substance(),List.of(new Cell(0,0),new Cell(0,0))));
        var parts=f.partition(List.of(List.of(new Cell(0,0),new Cell(1,0)),List.of(new Cell(0,1),new Cell(1,1))));
        assertEquals(f.massMilligrams(),parts.stream().mapToLong(FragmentDescriptor::massMilligrams).sum());
        assertThrows(IllegalArgumentException.class,()->f.partition(List.of(List.of(new Cell(0,0)),List.of(new Cell(0,0)))));
        var plan=FragmentationPlan.select(f,2,42); var again=FragmentationPlan.select(f,2,42);
        assertEquals(plan.removed(),again.removed()); assertEquals(4,FragmentationPlan.surface(f).size());
        assertEquals(f.massMilligrams(),plan.debrisMilligrams()+plan.remainder().stream().mapToLong(FragmentDescriptor::massMilligrams).sum());
        assertEquals(SubstanceRegistry.MARBLE,plan.debris().substance());
        assertTrue(FragmentationPlan.select(f,4,1).remainder().isEmpty());
        var square = new java.util.ArrayList<Cell>();
        for(int y=0;y<3;y++) for(int x=0;x<3;x++) square.add(new Cell(x,y));
        var solid = new FragmentDescriptor(f.substance(),square);
        assertEquals(8,FragmentationPlan.surface(solid).size());
        assertFalse(FragmentationPlan.surface(solid).contains(new Cell(1,1)));
        assertThrows(IllegalArgumentException.class,()->new FragmentationPlan(solid,List.of(),List.of(new Cell(1,1)),
                new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.GRIT),MassAccounting.cellMilligrams(f.substance())));
        assertThrows(IllegalArgumentException.class,()->new FragmentDescriptor(f.substance(),List.of(new Cell(0,0),new Cell(2,0))));
        var offset = new FragmentDescriptor(f.substance(),List.of(new Cell(10,20),new Cell(11,20)));
        assertEquals(2,offset.width()); assertEquals(1,offset.height());
    }
    @Test void inventoryCapacityIdentityAndBoulder() {
        var pebble=new ItemType("pebble",ItemType.QuantityKind.COUNT,100000,true);
        var grit=new ItemType("grit",ItemType.QuantityKind.MASS,1,true);
        var rules=new InventoryRules(24,25000000,Map.of("pebble",64L,"grit",1000L));
        var marble=new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.PEBBLE);
        var granite=new MaterialSample(SubstanceRegistry.GRANITE,MaterialForm.PEBBLE);
        var a=InventoryInsertion.insert(rules,InventorySnapshot.empty(24),new ItemStack(pebble,marble,"",130));
        assertEquals(0,a.remainder().quantity()); assertEquals(64,a.inventory().slots().get(0).orElseThrow().quantity());
        var b=InventoryInsertion.insert(rules,a.inventory(),new ItemStack(pebble,granite,"",2));
        assertEquals(granite,b.inventory().slots().get(3).orElseThrow().sample());
        var c=InventoryInsertion.insert(rules,b.inventory(),new ItemStack(grit,new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.GRIT),"",1500));
        assertEquals(1000,c.inventory().slots().get(4).orElseThrow().quantity());
        var differentForm=InventoryInsertion.insert(rules,c.inventory(),new ItemStack(pebble,new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.ROCK_PIECE),"",1));
        assertEquals(MaterialForm.ROCK_PIECE,differentForm.inventory().slots().get(6).orElseThrow().sample().form());
        var slotLimited=InventoryInsertion.insert(new InventoryRules(1,25000000,Map.of("pebble",64L)),InventorySnapshot.empty(1),new ItemStack(pebble,marble,"",70));
        assertEquals(6,slotLimited.remainder().quantity());
        var limited=InventoryInsertion.insert(new InventoryRules(1,250000,Map.of("pebble",64L)),InventorySnapshot.empty(1),new ItemStack(pebble,marble,"",10));
        assertEquals(2,limited.inventory().slots().getFirst().orElseThrow().quantity()); assertEquals(8,limited.remainder().quantity());
        var boulder=new ItemStack(pebble,new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.BOULDER),"",1);
        assertEquals(1,InventoryInsertion.insert(rules,InventorySnapshot.empty(24),boulder).remainder().quantity());
        assertEquals(0,InventoryInsertion.insert(rules,InventorySnapshot.empty(24),new ItemStack(pebble,marble,"",0)).remainder().quantity());
        assertThrows(ArithmeticException.class,()->new ItemStack(pebble,marble,"",Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->new ItemStack(pebble,marble,"",-1));
        var metadata=InventoryInsertion.insert(rules,InventorySnapshot.empty(24),new ItemStack(pebble,marble,"first",1));
        metadata=InventoryInsertion.insert(rules,metadata.inventory(),new ItemStack(pebble,marble,"second",1));
        assertTrue(metadata.inventory().slots().get(1).isPresent());
    }
    @Test void compositeRecipeConservesEachSubstance() {
        var pebble=new ItemType("pebble",ItemType.QuantityKind.COUNT,1000,true);
        var binder=new ItemType("clay",ItemType.QuantityKind.MASS,1,true);
        var block=new ItemType("block",ItemType.QuantityKind.COUNT,2500,true);
        var inputs=List.of(new ItemStack(pebble,new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.PEBBLE),"",2),
                new ItemStack(binder,new MaterialSample(SubstanceRegistry.CLAY,MaterialForm.GRIT),"",500));
        var out=new ItemStack(block,new MaterialSample(SubstanceRegistry.MARBLE,MaterialForm.CRAFTED_BLOCK),"composite",1);
        assertDoesNotThrow(()->new CraftingRecipe(inputs,out,Map.of(SubstanceRegistry.MARBLE,2000L,SubstanceRegistry.CLAY,500L)));
        assertThrows(IllegalArgumentException.class,()->new CraftingRecipe(inputs,out,Map.of(SubstanceRegistry.MARBLE,2500L)));
    }
}
