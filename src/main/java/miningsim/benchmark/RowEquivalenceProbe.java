package miningsim.benchmark;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Random;

/** Compares every cell after every tick in two isolated compiled classpaths. */
public final class RowEquivalenceProbe {
    private record Instance(Object world, Object simulation, Object[] materials, Method set, Method get, Method step, Method clear) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("expected baseline and candidate class directories");
        try (URLClassLoader baseline = loader(args[0]); URLClassLoader candidate = loader(args[1])) {
            for (String mode : new String[]{"FULL_SCAN", "ACTIVE_REGIONS"}) {
                Instance a = create(baseline, mode), b = create(candidate, mode);
                Random random = new Random(31);
                for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++) {
                    int id = random.nextInt(7);
                    a.set.invoke(a.world, x, y, a.materials[id]); b.set.invoke(b.world, x, y, b.materials[id]);
                }
                for (int tick = 0; tick < 100; tick++) {
                    if (tick == 40) { a.clear.invoke(a.world); b.clear.invoke(b.world); }
                    if (tick >= 40 && tick % 3 == 0) {
                        int x = tick % 2 == 0 ? 63 : 64;
                        int id = tick % 4 == 0 ? 2 : 3;
                        a.set.invoke(a.world, x, 63, a.materials[id]); b.set.invoke(b.world, x, 63, b.materials[id]);
                    }
                    a.step.invoke(a.simulation); b.step.invoke(b.simulation);
                    for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++) {
                        String left = a.get.invoke(a.world, x, y).toString();
                        String right = b.get.invoke(b.world, x, y).toString();
                        if (!left.equals(right)) throw new AssertionError(mode + " tick=" + tick + " cell=" + x + "," + y);
                    }
                }
                System.out.println(mode + ": 100 complete states matched (130x66, seed 47)");
            }
        }
    }

    private static URLClassLoader loader(String path) throws Exception {
        return new URLClassLoader(new java.net.URL[]{Path.of(path).toUri().toURL()}, null);
    }
    private static Instance create(ClassLoader loader, String modeName) throws Exception {
        Class<?> world = loader.loadClass("miningsim.world.World"), material = loader.loadClass("miningsim.world.Material");
        Class<?> simulation = loader.loadClass("miningsim.simulation.Simulation"), mode = loader.loadClass("miningsim.simulation.Simulation$Mode");
        Object selected = null;
        for (Object value : mode.getEnumConstants()) if (value.toString().equals(modeName)) selected = value;
        Object w = world.getConstructor(int.class, int.class).newInstance(130, 66);
        Constructor<?> constructor = simulation.getConstructor(world, long.class, mode, boolean.class);
        Object s = constructor.newInstance(w, 47L, selected, false);
        return new Instance(w, s, material.getEnumConstants(), world.getMethod("set", int.class, int.class, material),
                world.getMethod("get", int.class, int.class), simulation.getMethod("step"), world.getMethod("clear"));
    }
}
