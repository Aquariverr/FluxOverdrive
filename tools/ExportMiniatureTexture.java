import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Mechanical nearest-neighbor export of the ImageGen master; does not redraw its artwork. */
public class ExportMiniatureTexture {
    public static void main(String[] args) throws Exception {
        BufferedImage master = ImageIO.read(Path.of(args[0]).toFile());
        BufferedImage tile = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(master, 0, 0, 32, 32, null);
        graphics.dispose();
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        ImageIO.write(tile, "png", output.toFile());
    }
}
