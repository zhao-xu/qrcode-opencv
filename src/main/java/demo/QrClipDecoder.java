package demo;

import java.awt.image.BufferedImage;

@FunctionalInterface
interface QrClipDecoder {
    /**
     * 二维码解析
     * @param clip 包含二维码的图片
     * @return 解析到的二维码文本，解析失败时必须返回 null，与 QrClipRecognizer 配合，减少循环次数
     */
    String decode(BufferedImage clip);
}
