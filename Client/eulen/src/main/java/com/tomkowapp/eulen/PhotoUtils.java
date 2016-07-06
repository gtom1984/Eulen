package com.tomkowapp.eulen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

// photo encryption and modification class

public class PhotoUtils {
    static final String AES_CIPHER = "AES/CBC/PKCS5Padding";
    static final String AES_KEY_FACTORY = "PBKDF2WithHmacSHA1";
    static final String AES_KEY_SPEC = "AES";

    // image scaling
    public static Bitmap scaleImage(File photoFile, int height, int width) {
        // get source bitmap size
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoFile.getAbsolutePath(), bitmapOptions);
        int photo_width = bitmapOptions.outWidth;
        int photo_height = bitmapOptions.outHeight;

        // set scale size
        int size = Math.min(photo_width/width, photo_height/height);

        bitmapOptions.inJustDecodeBounds = false;
        bitmapOptions.inSampleSize = size;

        return BitmapFactory.decodeFile(photoFile.getAbsolutePath(), bitmapOptions);
    }

    // image flip
    public static Bitmap flipImage(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        return Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    // encrypt photo and return base64 string
    public static String encrypt(Bitmap photo, String encryptionKey, int quality) {
        try {
            // split key for salt and password
            String salt = encryptionKey.substring(0, 40);
            String password = encryptionKey.substring(40);

            // convert image to bytes
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, quality, stream);
            byte[] byteArray = stream.toByteArray();

            // generate IV
            Cipher cipher = Cipher.getInstance(AES_CIPHER);
            SecureRandom random = new SecureRandom();
            byte[] realIV = new byte[cipher.getBlockSize()];
            random.nextBytes(realIV);

            // create key
            IvParameterSpec ivSpec = new IvParameterSpec(realIV);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(AES_KEY_FACTORY);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 1000, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), AES_KEY_SPEC);

            // encrypt data
            cipher.init(Cipher.ENCRYPT_MODE, secret, ivSpec);
            byte[] cipherText  = cipher.doFinal(byteArray);

            // add IV as first 16 bytes of cipher text
            byte[] combined = new byte[realIV.length + cipherText.length];
            System.arraycopy(realIV, 0,combined, 0, realIV.length);
            System.arraycopy(cipherText, 0, combined, realIV.length, cipherText.length);

            // encode as string
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception ex) {
            return null;
        }
    }

    // decrypt photo and return base64 string
    public static String decrypt(String encoded, String encryptionKey) {
        try {
            // split key for password and salt
            String salt = encryptionKey.substring(0, 40);
            String password = encryptionKey.substring(40);

            // convert base64 to byte array
            byte[] byteArray = Base64.decode(encoded, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(AES_CIPHER);

            // get IV from byte array
            byte[] realIV = new byte[cipher.getBlockSize()];
            System.arraycopy(byteArray, 0, realIV, 0, realIV.length);

            // get cipher text from byte array
            byte[] cipherText = new byte[byteArray.length - cipher.getBlockSize()];
            System.arraycopy(byteArray, realIV.length, cipherText, 0, cipherText.length);

            // create key
            IvParameterSpec ivSpec = new IvParameterSpec(realIV);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(AES_KEY_FACTORY);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 1000, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), AES_KEY_SPEC);

            // decrypt data
            cipher.init(Cipher.DECRYPT_MODE, secret, ivSpec);
            byte[] decryptedData  = cipher.doFinal(cipherText);

            return Base64.encodeToString(decryptedData, Base64.NO_WRAP);
        } catch (Exception ex) {
            return null;
        }
    }

    // convert base64 string to bitmap
    public static Bitmap base64toBitmap(String encoded) {
        byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
