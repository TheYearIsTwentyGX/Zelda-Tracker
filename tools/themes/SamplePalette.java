import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Prints the most common colours in a downloaded screenshot or sprite sheet, for picking a game
 * theme's palette (docs/themes.md). Run it as a single-file program on the project's JDK:
 *
 *   java tools/themes/SamplePalette.java sheet.png [x y w h] [count]
 *
 * Transparent pixels are skipped, and colours within a few steps of a commoner one are counted with
 * it, so dithering and anti-aliasing don't split a window fill into a dozen shades.
 */
public class SamplePalette {
    private static final int MERGE_DISTANCE = 12;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 2 && args.length != 5 && args.length != 6) {
            System.err.println("usage: SamplePalette <png> [x y w h] [count]");
            System.exit(2);
        }
        BufferedImage image = ImageIO.read(new File(args[0]));
        if (image == null) throw new IllegalArgumentException("not an image: " + args[0]);
        int x0 = 0, y0 = 0, w = image.getWidth(), h = image.getHeight();
        if (args.length >= 5) {
            x0 = Integer.parseInt(args[1]);
            y0 = Integer.parseInt(args[2]);
            w = Math.min(Integer.parseInt(args[3]), image.getWidth() - x0);
            h = Math.min(Integer.parseInt(args[4]), image.getHeight() - y0);
        }
        int count = args.length == 2 ? Integer.parseInt(args[1]) : args.length == 6 ? Integer.parseInt(args[5]) : 12;

        Map<Integer, Integer> counts = new HashMap<>();
        long total = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 128) continue;
                counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
                total++;
            }
        }
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        // Fold each colour into the first commoner one it's close to.
        List<int[]> groups = new ArrayList<>(); // {rgb, count}
        for (Map.Entry<Integer, Integer> e : sorted) {
            int[] home = null;
            for (int[] g : groups) if (distance(g[0], e.getKey()) <= MERGE_DISTANCE) { home = g; break; }
            if (home == null) groups.add(new int[] {e.getKey(), e.getValue()});
            else home[1] += e.getValue();
        }
        groups.sort((a, b) -> b[1] - a[1]);
        System.out.printf("%s  region %d,%d %dx%d  %d opaque pixels%n", args[0], x0, y0, w, h, total);
        for (int i = 0; i < Math.min(count, groups.size()); i++) {
            int[] g = groups.get(i);
            System.out.printf("  #%06X  %5.1f%%%n", g[0], 100.0 * g[1] / total);
        }
    }

    private static int distance(int a, int b) {
        return Math.max(Math.abs((a >> 16 & 255) - (b >> 16 & 255)),
            Math.max(Math.abs((a >> 8 & 255) - (b >> 8 & 255)), Math.abs((a & 255) - (b & 255))));
    }
}
