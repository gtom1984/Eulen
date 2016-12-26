package com.tomkowapp.eulen;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.Gravity;
import android.widget.Toast;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

// various reusable utlities for the application

class EulenUtils {

    // hashing method
    final String sha(String input) {

        String hash = null;

        if (null == input) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            digest.reset();
            digest.update(input.getBytes(), 0, input.length());
            hash = new BigInteger(1, digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            // do nothing
        }

        return hash;
    }

    // simple password generator
    static String passwordGenerator() {
        SecureRandom random = new SecureRandom();
        return new BigInteger(130, random).toString(32);
    }

    // network connection check
    boolean networkConnection(Context context) {
        boolean isConnected;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            Toast toast = Toast.makeText(context, context.getString(R.string.error_network), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }

        return isConnected;
    }

    // cursor copier to allow for closing within database object
    MatrixCursor copyCursor(Cursor cursor) {
        MatrixCursor matrixCursor;

        if (cursor != null) {
            matrixCursor = new MatrixCursor(cursor.getColumnNames());
            int columnCount = cursor.getColumnCount();
            try {
                while (cursor.moveToNext()) {
                    Object rowObj[] = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        rowObj[i] = cursor.getString(i);
                    }
                    matrixCursor.addRow(rowObj);
                }
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }

        return matrixCursor;
    }
}
