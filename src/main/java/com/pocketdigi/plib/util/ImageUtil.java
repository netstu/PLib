package com.pocketdigi.plib.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;

import com.pocketdigi.plib.core.PApplication;
import com.pocketdigi.plib.core.PLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 图片处理util
 * Created by fhp on 14-9-7.
 */
public class ImageUtil {
    private static final String TAG = "ImageUtil";

    /**
     * Android api 17实现的虚化,低版本会使用stackblur
     * 某些机型上可能会Crash
     *
     * @param context
     * @param sentBitmap
     * @param radius     大于1小于等于25 ,值越大，虚化越严重
     * @return
     */
    @SuppressLint("NewApi")
    public static Bitmap fastblur(Context context, Bitmap sentBitmap, int radius) {
        if (sentBitmap == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT > 16) {
            Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);

            final RenderScript rs = RenderScript.create(context);
            final Allocation input = Allocation.createFromBitmap(rs,
                    sentBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT);
            final Allocation output = Allocation.createTyped(rs,
                    input.getType());
            final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs,
                    Element.U8_4(rs));
            script.setRadius(radius /* e.g. 3.f */);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(bitmap);
            return bitmap;
        }
        return stackblur(sentBitmap, radius);
    }

    /**
     * 纯Java实现的虚化，适用老版本api，外部只需调fastblur，会自动判断
     *
     * @param sentBitmap
     * @param radius
     * @return
     */
    private static Bitmap stackblur(Bitmap sentBitmap,
                                    int radius) {

        Bitmap bitmap = null;
        try {
            bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return sentBitmap;
        }

        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16)
                        | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return (bitmap);
    }

    /**
     * 虚化图片的下半部分，算法有待优化
     * @param sentBitmap 原图
     * @param notBlurHeight 不虚化的高度
     * @return
     */
    public static Bitmap blurBitmapPart(Bitmap sentBitmap,int notBlurHeight) {
        int radius=25;
        Bitmap bitmap = null;
        try {
            bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return sentBitmap;
        }

        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16)
                        | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, w*notBlurHeight, w, 0, notBlurHeight, w, h-notBlurHeight);
        return bitmap;
    }


    /**
     * 从文件解析出图片，可以是缩略图
     *
     * @param filePath     　文件路径
     * @param inSampleSize 　缩小系数，1为原图,如果是2,宽高均为原图的1/2,类推
     * @return
     */
    public static Bitmap decodeFromFile(String filePath, int inSampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        return bitmap;
    }

    /**
     * 通过最大高宽度计算SampleSize
     *
     * @param filePath
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private static int getSampleSizeFromFile(String filePath, int maxWidth, int maxHeight) {
        // 计算图片的真实尺寸，但不读出图片
        int[] size = getBitmapSize(filePath);
        float realWidth = size[0];
        float realHeight = size[1];

        // 如果图片尺寸比最大值小，直接返回
        if (maxWidth > realWidth && maxHeight > realHeight) {
            return 1;
        }
        // 计算宽高比
        float target_ratio = (float) maxWidth / maxHeight;
        float real_ratio = realWidth / realHeight;
        int inSampleSize = 1;
        if (real_ratio > target_ratio) {
            // 如果width太大，height太小，以width为基准，把realWidth设为maxWidth,realHeight缩放
            inSampleSize = (int)Math.rint( realWidth / maxWidth);
        } else {
            inSampleSize = (int) realHeight / maxHeight;
        }
        return inSampleSize;
    }

    /**
     * 通过最大高宽度计算SampleSize
     *
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private static int getSampleSizeFromResource(Context context, int resId, int maxWidth, int maxHeight) {
        // 计算图片的真实尺寸，但不读出图片
        int[] size = getBitmapSize(context, resId);
        return getSampleSize(size, maxWidth, maxHeight);
    }

    /**
     * 计算sampleSize
     *
     * @param bmpSize
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private static int getSampleSize(int[] bmpSize, int maxWidth, int maxHeight) {
        int realWidth = bmpSize[0];
        int realHeight = bmpSize[1];
        return getSampleSize(realWidth, realHeight, maxWidth, maxHeight);
    }

    /**
     * 计算sampleSize
     *
     * @return
     */
    private static int getSampleSize(int realWidth, int realHeight, int maxWidth, int maxHeight) {
        // 如果图片尺寸比最大值小，直接返回
        if (maxWidth > realWidth && maxHeight > realHeight) {
            return 1;
        }
        // 计算宽高比
        float target_ratio = (float) maxWidth / maxHeight;
        float real_ratio = realWidth / realHeight;
        int inSampleSize = 1;
        if (real_ratio > target_ratio) {
            // 如果width太大，height太小，以width为基准，把realWidth设为maxWidth,realHeight缩放
            inSampleSize = (int) realWidth / maxWidth;
        } else {
            inSampleSize = (int) realHeight / maxHeight;
        }
        return inSampleSize;
    }

    /**
     * 从文件解析出图片，可以是缩略图
     *
     * @param filePath
     * @param maxWidth  缩略图最大宽度
     * @param maxHeight 　缩略图最大高度
     * @return
     */
    public static Bitmap decodeFromFile(String filePath, int maxWidth, int maxHeight) {
        int inSampleSize = getSampleSizeFromFile(filePath, maxWidth, maxHeight);
        return decodeFromFile(filePath, inSampleSize);
    }

    /**
     * 从ResourceID 解析图片
     *
     * @param context
     * @param resId
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    public static Bitmap decodeFromResource(Context context, int resId, int maxWidth, int maxHeight) {
        // 计算图片的真实尺寸，但不读出图片
        int inSampleSize = getSampleSizeFromResource(context, resId, maxWidth, maxHeight);
        return decodeFromResource(context, resId, inSampleSize);
    }

    /**
     * 从ResourceID 解析图片
     *
     * @param context
     * @param resId
     * @param inSampleSize
     * @return
     */
    public static Bitmap decodeFromResource(Context context, int resId, int inSampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        options.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId, options);
        return bitmap;
    }

    /**
     * 缩小图片，使用sampleSize，因为是int所有有误差
     *
     * @param bmp
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    public static Bitmap scaleBitmap(Bitmap bmp, int maxWidth, int maxHeight) {
        if(maxWidth==0||maxHeight==0) {
            return bmp;
        }
        float scale = getCompressScale(bmp.getWidth(), bmp.getHeight(), maxWidth, maxHeight);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale); //长和宽放大缩小的比例
        Bitmap resizeBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        /**
         * 实测 Galaxy nexus Android 4.3上,bmp和resizeBmp是不同对象
         * 但 在Nexus 7 2013 Android 4.4.4上，bmp和resizeBmp是同一个对象
         * 不确定createBitmap从哪个版本开始更改
         */
        if (bmp != resizeBmp) {
            bmp.recycle();
        }
        return resizeBmp;
    }

    /**
     * 有损压缩图片，缩小图片分辨率,质量为quality
     * @param filePath
     * @return
     */
    public static byte[] compressBitmap(String filePath,Bitmap.CompressFormat format,int quality,int maxWidth, int maxHeight) {
        Bitmap sourceBmp=BitmapFactory.decodeFile(filePath);
        return  compressBitmap(sourceBmp,format,quality,maxWidth,maxHeight);
    }

    /**
     * 有损压缩图片，缩小图片分辨率,质量为quality
     * @return
     */
    public static byte[] compressBitmap(Bitmap sourceBmp,Bitmap.CompressFormat format,int quality,int maxWidth, int maxHeight) {
        if(sourceBmp!=null)
        {
            PLog.d(TAG, "原大小" + sourceBmp.getByteCount());
//            float scale = getCompressScale(sourceBmp.getWidth(), sourceBmp.getHeight(), maxWidth, maxHeight);
//            Matrix matrix = new Matrix();
//            matrix.postScale(scale, scale); //长和宽放大缩小的比例
            Bitmap bitmap = scaleBitmap(sourceBmp, maxWidth, maxHeight);
            byte[] bytes=bitmap2ByteArray(bitmap,format,quality);
            bitmap.recycle();
            PLog.d(TAG, "压缩后大小"+bytes.length);
            return bytes;
        }else{
            return null;
        }
    }


    public static byte[] bitmap2ByteArray(Bitmap bmp,Bitmap.CompressFormat format,int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(format, quality, baos);
        return baos.toByteArray();
    }

    /**
     * 获取压缩比例，返回的是小于1的浮点数，只能用于缩小，不能放大
     *
     * @param realWidth
     * @param realHeight
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private static float getCompressScale(int realWidth, int realHeight, int maxWidth, int maxHeight) {
        // 如果图片尺寸比最大值小，直接返回
        if (maxWidth > realWidth && maxHeight > realHeight) {
            return 1;
        }
        float widthScale = (float) maxWidth / realWidth;
        float heightScale = (float) maxHeight / realHeight;
        if (widthScale < 1 || heightScale < 1) {
            return Math.min(widthScale, heightScale);
        }
        return 1;
    }

    /**
     * 获取图片的尺寸,不会真正读取,省资源
     *
     * @param context
     * @param resId
     */
    public static int[] getBitmapSize(Context context, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, options);
        int[] size = new int[2];
        size[0] = options.outWidth;
        size[1] = options.outHeight;
        return size;
    }

    /**
     * 获取图片的尺寸,不会真正读取,省资源
     */
    public static int[] getBitmapSize(String jpgPath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(jpgPath, options);
        int[] size = new int[2];
        size[0] = options.outWidth;
        size[1] = options.outHeight;
        return size;
    }

    /**
     * 从内置图片资源，生成圆角或圆形图片
     *
     * @param res
     * @param resId  图片的资源id
     * @param radius 圆角半径，当半径为宽高中最大值的一半时，是圆形图片
     * @return
     */
    public static RoundedBitmapDrawable toRoundDrawable(Resources res, int resId, float radius) {
        Bitmap src = BitmapFactory.decodeResource(res, resId);
        return toRoundDrawable(res, src, radius);
    }

    /**
     * 从本地图片，生成圆角或圆形图片
     *
     * @param res
     * @param filePath 图片的文件路径
     * @param radius   圆角半径，当半径为宽高中最大值的一半时，是圆形图片
     * @return
     */
    public static RoundedBitmapDrawable toRoundDrawable(Resources res, String filePath, float radius) {
        RoundedBitmapDrawable roundedBitmapDrawable =
                RoundedBitmapDrawableFactory.create(res, filePath);
        //设置圆角半径
        roundedBitmapDrawable.setCornerRadius(radius);
        return roundedBitmapDrawable;
    }

    /**
     * 从Bitmap，生成圆角或圆形图片
     *
     * @param res
     * @param src    源图片
     * @param radius 圆角半径，当半径为宽高中最大值的一半时，是圆形图片
     * @return
     */
    public static RoundedBitmapDrawable toRoundDrawable(Resources res, Bitmap src, float radius) {
        RoundedBitmapDrawable roundedBitmapDrawable =
                RoundedBitmapDrawableFactory.create(res, src);
        //设置圆角半径
        roundedBitmapDrawable.setAntiAlias(true);
        roundedBitmapDrawable.setCornerRadius(radius);
        return roundedBitmapDrawable;
    }

    /**
     * 生成圆形图片
     *
     * @param res
     * @param resId 必须宽高1:1，否则自动裁剪
     * @return
     */
    public static RoundedBitmapDrawable toCircularDrawable(Resources res, int resId) {
        Bitmap src = BitmapFactory.decodeResource(res, resId);
        return toCircularDrawable(res, src);
    }

    /**
     * 生成圆形图片
     *
     * @param res
     * @param src 必须宽高1:1，否则自动裁剪
     * @return
     */
    public static RoundedBitmapDrawable toCircularDrawable(Resources res, Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        if(width!=height) {
            int min = Math.min(width, height);
            Bitmap bmp = Bitmap.createBitmap(src, 0, 0, min, min);
//            src.recycle();
            return toRoundDrawable(res, bmp, min / 2.0f);
        }
        return toRoundDrawable(res, src, width / 2.0f);
    }



    /**
     * 生成圆形图片
     *
     * @param res
     * @param filePath 必须宽高1:1，否则自动裁剪
     * @return
     */
    public static RoundedBitmapDrawable toCircularDrawable(Resources res, String filePath) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            Bitmap bitmap  = BitmapFactory.decodeStream(fis);
            return toCircularDrawable(res, bitmap);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 有的手机有的系统在拍照之后会自动对图片进行旋转，需要修正这个旋转的角度
     *
     * @param path
     * @return
     */
    public static int readPictureRotateDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 旋转图片
     *
     * @param bitmap
     * @param angle
     * @return
     */
    public static Bitmap rotateImageView(Bitmap bitmap, int angle) {
        if (angle == 0) {
            return bitmap;
        } else {
            // 旋转图片
            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
    }


    /**
     * 读取图片属性：旋转的角度
     * @param path 图片绝对路径
     * @return degree旋转的角度
     */
    public static int readPictureDegree(String path) {
        int degree  = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
    * 旋转图片
    * @param angle
    * @param bitmap
    * @return Bitmap
    */
    public static Bitmap rotaingImageView(int angle , Bitmap bitmap) {
        //旋转图片 动作
        Matrix matrix = new Matrix();;
        matrix.postRotate(angle);
        // 创建新的图片
        try {
            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            return resizedBitmap;
        }catch (Error e) {
        }
        return bitmap;
    }

    /**
     * 转正图片，某些手机拍出的照片旋转90度
     * @param srcPath
     * @param targetPath
     */
    public static void rotaingImage(String srcPath,String targetPath) {
        /**
         * 获取图片的旋转角度，有些系统把拍照的图片旋转了，有的没有旋转
         */
        int degree = readPictureDegree(srcPath);

        BitmapFactory.Options opts = new BitmapFactory.Options();//获取缩略图显示到屏幕上
        opts.inSampleSize = 1;
        Bitmap cbitmap = BitmapFactory.decodeFile(srcPath, opts);
        /**
         * 把图片旋转为正的方向
         */
        Bitmap newbitmap = ImageUtil.rotaingImageView(degree, cbitmap);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        newbitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos);
        byte[] bitmapdata = bos.toByteArray();
        try {
            FileOutputStream fos = new FileOutputStream(targetPath);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        newbitmap.recycle();
        if(!cbitmap.isRecycled()) {
            cbitmap.recycle();
        }
    }

    /**
     * 将activity当前视图虚拟，当做背景图片使用(虚化严重)
     * @param activity
     * @return
     */
    public static Bitmap blurActivityForBg(Activity activity) {
        //step1 截图
        Bitmap viewBmp = RuntimeUtil.screenShotNoStatusBar(activity);
        //stpe2 缩放
        Bitmap scaledBmp = ImageUtil.scaleBitmap(viewBmp, viewBmp.getWidth() / 4, viewBmp.getHeight() / 4);
        //step3 blur
        return fastblur(activity, scaledBmp, 15);
    }


//    public static Bitmap compressAccurate(String srcPath,int maxWidth, int maxHeight) {
//        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inJustDecodeBounds = true;
//        Bitmap bitmap = BitmapFactory.decodeFile(srcPath, options);
//        float compressScale = getCompressScale(bitmap.getWidth(), bitmap.getHeight(), maxWidth, maxHeight);
//        float targetWidth=bitmap.getWidth()*compressScale;
//        float targetHeight=bitmap.getHeight()*compressScale;
//        Bitmap.createScaledBitmap(bitmap)
//    }

    public static void saveBitmap2File(Bitmap bmp, String filePath, Bitmap.CompressFormat format, int quality) {
        saveBitmap2File(bmp,filePath,format,quality,0,0);
    }


    public static void saveBitmap2File(Bitmap bmp, String filePath, Bitmap.CompressFormat format, int quality,int maxWidth,int maxHeight) {
        PLog.e(TAG, "保存图片到" +filePath);
        File f = new File(filePath);
        if (f.exists()) {
            f.delete();
        }
        File parentFile = f.getParentFile();
        if(!parentFile.exists()) {
            parentFile.mkdirs();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            if(maxWidth>0) {
                bmp=scaleBitmap(bmp, maxWidth, maxHeight);
            }
            bmp.compress(format, quality, out);
            out.flush();
            out.close();
            PLog.i(TAG, "保存成功");
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}