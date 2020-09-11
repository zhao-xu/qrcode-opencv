package demo;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class QrClipRecognizerTest {
    public static void main(String[] args) throws Exception {
        var workRoot = Path.of("d:/temp/test-qrcode");
        var workPath = workRoot.resolve(Path.of(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))));

        System.out.println("zxing");
        var image = ImageIO.read(workRoot.resolve("xx-2.jpg").toFile());
        var text = decodeWithZXing(image);
        if (text != null) {
            System.out.println(">> " + text + " <<");
        }

        System.out.println("----");
        System.out.println("use recognizer");

        var qrClipRecognizer = new QrClipRecognizer(QrClipRecognizerTest::decodeWithZXing, workPath);
        text = qrClipRecognizer.decode(image);
        if (text != null) {
            System.out.println(">> " + text + " <<");
        }
    }

    private static String decodeWithZXing(BufferedImage image) {
        var luminanceSource = new BufferedImageLuminanceSource(image);
        var binarizer = new HybridBinarizer(luminanceSource);
        var binaryBitmap = new BinaryBitmap(binarizer);
        try {
            return new QRCodeReader().decode(binaryBitmap, null).getText();
        } catch (Exception e) {
            return null;
        }
    }
}
