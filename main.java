import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import core.Texture;
import core.image.WIL;
import core.image.WZL;

import java.awt.image.*;
import java.awt.*;

class Main {
    public static void main(String[] args) {
        File file = new File("./res/wils/Items.wzl");
        // WIL wzl = new WIL(file.getAbsolutePath());
        WZL wzl = new WZL(file.getAbsolutePath());
        if (wzl.isLoaded()) {
            System.out.println("wzl loaded");
            System.out.println("wzl: image count: " + wzl.getImageCount());
            /// write file to local
            Color blackColor = new Color(0x000000);
            for (int index = 0; index < wzl.getImageCount(); index++) {
                Texture texture = wzl.tex(index);
                if (!texture.empty()) {
                    String dist = "./outputs/mon/output_" + index + ".png";
                    BufferedImage bufferedImage = Texture.toBufferedImage(texture, true);
                    Image image = makeColorTransparent(bufferedImage, blackColor);
                    BufferedImage transparentBufferedImage = imageToBufferedImage(image);
                    if (transparentBufferedImage != null) {
                        File savefile = new File(dist);
                        try {
                            ImageIO.write(transparentBufferedImage, "png", savefile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {
            System.out.println("wzl loaded failed");
        }
    }

    private static BufferedImage imageToBufferedImage(Image image) {

        BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bufferedImage.createGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.dispose();

        return bufferedImage;

    }

    public static Image makeColorTransparent(BufferedImage im, final Color color) {
        ImageFilter filter = new RGBImageFilter() {

            // the color we are looking for... Alpha bits are set to opaque
            public int markerRGB = color.getRGB() | 0xFF000000;

            public final int filterRGB(int x, int y, int rgb) {
                if ((rgb | 0xFF000000) == markerRGB) {
                    // Mark the alpha bits as zero - transparent
                    return 0x00FFFFFF & rgb;
                } else {
                    // nothing to do
                    return rgb;
                }
            }
        };

        ImageProducer ip = new FilteredImageSource(im.getSource(), filter);
        return Toolkit.getDefaultToolkit().createImage(ip);
    }

}