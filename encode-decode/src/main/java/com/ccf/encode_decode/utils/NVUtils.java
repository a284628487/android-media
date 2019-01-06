package com.ccf.encode_decode.utils;

public class NVUtils {

    public static byte[] NV21_rotate_to_270(byte[] nv21_data, int width, int height) {
        int y_size = width * height;
        int buffser_size = y_size * 3 / 2;
        byte[] nv21_rotated = new byte[buffser_size];
        int i = 0;

        // Rotate the Y luma
        for (int x = width - 1; x >= 0; x--) {
            int offset = 0;
            for (int y = 0; y < height; y++) {
                nv21_rotated[i] = nv21_data[offset + x];
                i++;
                offset += width;
            }
        }

        // Rotate the U and V color components
        i = y_size;
        for (int x = width - 1; x > 0; x = x - 2) {
            int offset = y_size;
            for (int y = 0; y < height / 2; y++) {
                nv21_rotated[i] = nv21_data[offset + (x - 1)];
                i++;
                nv21_rotated[i] = nv21_data[offset + x];
                i++;
                offset += width;
            }
        }
        return nv21_rotated;
    }


    public static byte[] NV21_rotate_to_180(byte[] nv21_data, int width, int height) {
        int y_size = width * height;
        int buffser_size = y_size * 3 / 2;
        byte[] nv21_rotated = new byte[buffser_size];
        int i = 0;
        int count = 0;


        for (i = y_size - 1; i >= 0; i--) {
            nv21_rotated[count] = nv21_data[i];
            count++;
        }


        for (i = buffser_size - 1; i >= y_size; i -= 2) {
            nv21_rotated[count++] = nv21_data[i - 1];
            nv21_rotated[count++] = nv21_data[i];
        }
        return nv21_rotated;
    }


    public static byte[] NV21_rotate_to_90(byte[] nv21_data, int width, int height) {
        int y_size = width * height;
        int buffser_size = y_size * 3 / 2;
        byte[] nv21_rotated = new byte[buffser_size];
        // Rotate the Y luma

        int i = 0;
        int startPos = (height - 1) * width;
        for (int x = 0; x < width; x++) {
            int offset = startPos;
            for (int y = height - 1; y >= 0; y--) {
                nv21_rotated[i] = nv21_data[offset + x];
                i++;
                offset -= width;
            }
        }

        // Rotate the U and V color components
        i = buffser_size - 1;
        for (int x = width - 1; x > 0; x = x - 2) {
            int offset = y_size;
            for (int y = 0; y < height / 2; y++) {
                nv21_rotated[i] = nv21_data[offset + x];
                i--;
                nv21_rotated[i] = nv21_data[offset + (x - 1)];
                i--;
                offset += width;
            }
        }
        return nv21_rotated;
    }


    public static byte[] NV21ToNV12(byte[] nv21, int width, int height) {
        byte[] nv12 = new byte[nv21.length /*width * height * 3 / 2 */];

        if (nv21 == null || nv12 == null) return nv12;
        int framesize = width * height;
        int j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);

//        System.arraycopy(nv21, 0, nv12, 0, width * height);
//        for (int i = width * height; i < nv21.length; i += 2) {
//            nv12[i] = nv21[i + 1];
//            nv12[i + 1] = nv21[i];
//        }

        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
        return nv12;
    }
}

// https://blog.csdn.net/quantum7/article/details/79762714
// https://blog.csdn.net/baidu_31872269/article/details/70315193
