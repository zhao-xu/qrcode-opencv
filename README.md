# 使用 OpenCV 提高 ZXing 的识别率

由于 ZXing 的识别率比较迷，有时明明很清晰的图片怎么都识别不出来，就想着用 OpenCV 把二维码部分截取出来，矫正之后再识别。

算法部分参考了 [JAVA提高ZXING对图片中的二维码的识别率（第二弹）](https://blog.csdn.net/strangesir/article/details/93143177)

但对于其文章中使用角度判断的方式不是很认同，而且除了最大角，还需要判断最小角的角度，因为会由于某个点过偏导致不符合条件的图形。
  
这里对三角形的判断使用三条边边长比的方式，相比角度判断可以省去不少数学计算，甚至不需要 Math 库。

## 格式转换

BufferedImage 和 Mat 的相互转换，这里没有使用最快的方式而是采用了比较安全的做法。

转换时使用 bmp 格式，应该可以避免 linux 下 OpenCV 依赖外部 libjpeg 和 libpng 的问题。

没有实际编译 .so 文件，这里不确定 openpnp 的 so 文件是否包含 jpeg 和 png

在 CentOS 7 环境下，bmp 格式转换测试通过。

## jar 包依赖

OpenCV 的 jar 包，能查到比较靠谱的是
[bytedeco](https://blog.csdn.net/strangesir/article/details/93143177)
和
[openpnp](https://github.com/openpnp/opencv)

其中 bytedeco 比较新，但是依赖太多，这里使用了 openpnp 的 jar 包。

实际使用时，建议将 openpnp 的 jar 包重新打包，拆分为 linux 和 windows 的不同 64 位版本，以减少发布包大小。
