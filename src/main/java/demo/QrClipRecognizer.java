package demo;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class QrClipRecognizer {
    private static final Logger log = LoggerFactory.getLogger(QrClipRecognizer.class);

    static {
        OpenCV.loadLocally();
    }

    private final QrClipDecoder qrClipDecoder;
    private final Path workPath;

    /**
     * @param qrClipDecoder 二维码解析
     * @param workPath 临时文件输出目录，logLevel == DEBUG
     */
    public QrClipRecognizer(QrClipDecoder qrClipDecoder, Path workPath) {
        this.qrClipDecoder = qrClipDecoder;
        this.workPath = workPath;
        try {
            Files.createDirectories(workPath);
        } catch (Exception e) {
            log.error("create workPath {}", workPath);
            System.exit(1);
        }
    }

    /**
     * 使用 qrClipDecoder 解析二维码
     * @param bufferedImage 图片
     * @return 二维码文本，未解析到结果时返回 null，与 QrClipDecoder 保持一致
     */
    public String decode(BufferedImage bufferedImage) {
        var srcMat = bufferedImage2Mat(bufferedImage);
        var prepareRound = 0;
        var workMat = prepareMat(srcMat, prepareRound);
        var text = scanMat(srcMat, workMat);
        // 没有解析到二维码定位点，使用 threshold 增强图片再次解析
        if (text == null) {
            log.info("not found, try threshold");
            prepareRound++;
            workMat = prepareMat(srcMat, prepareRound);
            text = scanMat(srcMat, workMat);
        }
        return text;
    }

    /**
     * 对图片预处理。由于对时间要求较高，这里只额外处理了一次。如果是静态处理，可以组合修改高斯模糊的像素距离等其它方式
     */
    private Mat prepareMat(Mat srcMat, int prepareRound) {
        var workMat = new Mat();
        Imgproc.cvtColor(srcMat, workMat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(workMat, workMat, new Size(5, 5), 0);
        if (log.isDebugEnabled()) {
            Imgcodecs.imwrite(workPath.resolve("GaussianBlur.jpg").toString(), workMat);
        }
        if (prepareRound > 0) {
            Imgproc.threshold(workMat, workMat, 100, 255, Imgproc.THRESH_BINARY);
            if (log.isDebugEnabled()) {
                Imgcodecs.imwrite(workPath.resolve("threshold.jpg").toString(), workMat);
            }
        }
        Imgproc.Canny(workMat, workMat, 112, 255);
        if (log.isDebugEnabled()) {
            Imgcodecs.imwrite(workPath.resolve("Canny.jpg").toString(), workMat);
        }
        return workMat;
    }

    private String scanMat(Mat src, Mat work) {
        var hierarchy = new Mat();
        var contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(work, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

        // 有些中文子轮廓数量很多，这里根据识别到的子轮廓数量分组，判断分组内个数满足条件才进行识别
        var countMarkerMap = new HashMap<Integer, ArrayList<MatOfPoint>>();
        final int HIERARCHY_IDX_FIRST_CHILD = 2;
        for (int i = 0; i < contours.size(); i++) {
            var contour = contours.get(i);
            var rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
            if (testSquare(rect)) {
                var hierarchyArray = hierarchy.get(0, i);
                int count = 0;
                // 子轮廓数量
                while ((int) hierarchyArray[HIERARCHY_IDX_FIRST_CHILD] != -1) {
                    count++;
                    hierarchyArray = hierarchy.get(0, (int) hierarchyArray[HIERARCHY_IDX_FIRST_CHILD]);
                }
                if (count >= 5) { // qr 标记点子轮廓数量。如果识别率不好，可以把四层也纳入
                    countMarkerMap.computeIfAbsent(count, k -> new ArrayList<>()).add(contour);
                }
            }
        }
        for (var markerContours : countMarkerMap.values()) {
            var markerCount = markerContours.size();
            // 标记点数量少于三个肯定不对，多于五个可能误判太多，同时避免图片中有两个二维码
            if (markerCount >= 3 && markerCount <= 5) {
                var text = cutImage(src, markerContours);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String cutImage(Mat src, List<MatOfPoint> contours) {
        int size = contours.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                for (int k = j + 1; k < size; k++) {
                    Point[] points = {
                            centerPoint(contours.get(i)),
                            centerPoint(contours.get(j)),
                            centerPoint(contours.get(k))
                    };

                    if (log.isDebugEnabled()) {
                        var lineMat = src.clone();
                        Imgproc.line(lineMat, points[0], points[1], new Scalar(0, 0, 255), 2);
                        Imgproc.line(lineMat, points[1], points[2], new Scalar(0, 0, 255), 2);
                        Imgproc.line(lineMat, points[2], points[0], new Scalar(0, 0, 255), 2);
                        Imgcodecs.imwrite(workPath.resolve(String.format("cutImage-test_%d-%d-%d.jpg", i, j, k)).toString(), lineMat);
                    }

                    if (testTriangle(points)) {
                        var p0 = points[0];
                        var p1 = points[1];
                        var p2 = points[2];
                        var p3 = new Point(p1.x + p2.x - p0.x, p2.y + p1.y - p0.y); // 三角形有旋转，点坐标修正

                        double width = 200; // 200, 50 都为测试调整值，不代表普适情况
                        double offset = 50;
                        var transList = new ArrayList<Point>();
                        transList.add(new Point(offset, offset));
                        transList.add(new Point(offset + width, offset));
                        transList.add(new Point(offset, offset + width));
                        transList.add(new Point(offset + width, offset + width));

                        var transform = Imgproc.getPerspectiveTransform(
                                Converters.vector_Point_to_Mat(Arrays.asList(p0, p1, p2, p3), CvType.CV_32F),
                                Converters.vector_Point_to_Mat(transList, CvType.CV_32F)
                        );
                        var temp = new Mat();
                        Imgproc.warpPerspective(src, temp, transform, new Size(width + offset * 2, width + offset * 2), Imgproc.INTER_LINEAR);
                        if (log.isDebugEnabled()) {
                            Imgcodecs.imwrite(workPath.resolve(String.format("cutImage-match_%d-%d-%d.jpg", i, j, k)).toString(), temp);
                        }
                        var clip = mat2BufferedImage(temp);
                        var text = qrClipDecoder.decode(clip);
                        if (text != null) {
                            return text;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Point centerPoint(MatOfPoint matOfPoint) {
        var rect = Imgproc.minAreaRect(new MatOfPoint2f(matOfPoint.toArray()));
        return rect.center;
    }

    /** rect 宽高比大致为正方形 **/
    private boolean testSquare(RotatedRect rect) {
        final double SQUARE_SCALE_MAX = 1.2;
        final double SQUARE_SCALE_MIN = 1 / 1.2;

        var rectRatio = rect.size.width / rect.size.height;
        return rectRatio > SQUARE_SCALE_MIN
                && rectRatio < SQUARE_SCALE_MAX;
    }

    /**
     * 三个点大致为等腰直角三角形
     * 如果结果为 true，将 points 内元素旋转，使得 (p1, p2) 为三角形斜边
     */
    private boolean testTriangle(Point[] points) {
        var p0 = points[0];
        var p1 = points[1];
        var p2 = points[2];

        // 计算线段长度，结果为平方值
        var l01 = pow2(p0.x - p1.x) + pow2(p0.y - p1.y);
        if (l01 == 0) {
            return false;
        }
        var l12 = pow2(p1.x - p2.x) + pow2(p1.y - p2.y);
        if (l12 == 0) {
            return false;
        }
        var l20 = pow2(p2.x - p0.x) + pow2(p2.y - p0.y);
        if (l20 == 0) {
            return false;
        }

        double[] lines = {l01, l12, l20};
        Arrays.sort(lines);
        // lines[1] 不用判断，一定在范围内
        var lA = lines[0];
        var lC = lines[2];
        var ratioCA = lC / lA;
        // 2.3104 = 取平方(2 * sin(100 / 2))，100 度角对应线段长度，大于这个角度不太可能为直角三角形
        // 1.6384 = 取平方(2 * sin(80 / 2))，80 度角对应线段长度
        if (ratioCA > 2.3104 || ratioCA < 1.6384) {
            return false;
        }

        // 旋转，使得 points[0] 为直角点，points[1] 与 points[2] 构成斜边
        if (l01 > l12 && l01 > l20) {
            points[0] = p2;
            points[1] = p0;
            points[2] = p1;
        } else if (l20 > l01 && l20 > l12) {
            points[0] = p1;
            points[1] = p2;
            points[2] = p0;
        }
        return true;
    }

    private double pow2(double val) {
        return val * val;
    }

    private Mat bufferedImage2Mat(BufferedImage bufferedImage) {
        var buffer = new ByteArrayOutputStream();
        try {
            ImageIO.write(bufferedImage, "bmp", buffer);
            buffer.flush();
        } catch (Exception e) {
            log.error("convert", e);
        }
        return Imgcodecs.imdecode(new MatOfByte(buffer.toByteArray()), Imgcodecs.IMREAD_UNCHANGED);
    }

    private BufferedImage mat2BufferedImage(Mat mat) {
        var buffer = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, buffer);
        try {
            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
        } catch (Exception e) {
            log.error("convert", e);
            return null;
        }
    }
}
