package com.tomkowapp.eulen;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Gravity;
import android.widget.Toast;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

// handle one time pad related encryption, decryption and input validation

public class EulenOTP {
    //one time pad character set
    final static public char[] charset = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n',
            'o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I',
            'J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z','0','1','2','3',
            '4','5','6','7','8','9',' ','.',',','!','?','@','#','$','%','&','^','*','~','`','-',
            '+','=',':',';','(',')','<','>','[',']','{','}','|','_','\\','/','\'','\"'};


    // create keys using secure random
    public KeyOTP generateKey(String userID, int amount, int size) {
        final int total = amount * 2;

        KeyOTP keys = new KeyOTP(userID, total);

        try {
            SecureRandom random = SecureRandom.getInstance();

            for (int i = 0; i <= total; i++) {
                char key[] = new char[size];

                int flag = 0;

                if (i == 0) {
                    flag = 0;
                } else if (i <= amount) {
                    flag = 1;
                } else if (i > amount) {
                    flag = 2;
                }

                for(int c = 0; c < size; c++) {
                   key[c] = charset[random.nextInt(charset.length)];
                }

                keys.add(UUID.randomUUID().toString(), new String(key), flag);
            }
        } catch (NoSuchAlgorithmException e) {
            // do nothing
        }

        keys.finish();

        return keys;
    }

    private boolean validate(String s) { //validate message to ensure it conforms to charset
        for (char c : s.toCharArray()) {
            if (new String(charset).indexOf(c) == -1) {
                return false;
            }
        }

        return true;
    }

    // OTP encryption
    public String encrypt(final String message, final char[] key) {
        final boolean valid = validate(message);

        if(!valid || key == null || key.length < 1) {
            return null;
        }

        final String messageToEncrypt;

        // ensure size is correct
        if(message.length() > key.length) {
            messageToEncrypt = message.substring(0, key.length - 1);
        } else {
            messageToEncrypt = message;
        }

        String result;

        // use a portion of the key for hash storage to allow for integrity checks
        char hashKey[] = new char[40];
        System.arraycopy(key, 0, hashKey, 0, 40);

        int messageLength = key.length - 40;

        // use a portion of the key for the message
        char messageKey[] = new char[messageLength];
        System.arraycopy(key, 40, messageKey, 0, messageLength);
        String messageEncrypted = encode(messageToEncrypt, messageKey); // encode the message

        String hash = new EulenUtils().sha(messageEncrypted); // hash the encoded message
        String hashEncrypted = encode(hash, hashKey); // encode the hash
        result = hashEncrypted + messageEncrypted; // combine the encoded hash and encoded message

        return result;
    }

    // OTP decryption
    public final String[] decrypt(final String data, final char[] key) {
        final boolean valid = validate(data);

        if(!valid || key == null || key.length < 1) {
            return null;
        }

        final String dataToDecrypt;

        if(data.length() > key.length) { // ensure lengths are correct
            dataToDecrypt = data.substring(0, key.length - 1);
        } else {
            dataToDecrypt = data;
        }

        EulenUtils eulenUtils = new EulenUtils();
        String encryptedHash = eulenUtils.sha(dataToDecrypt.substring(40)); // get hash of encoded message
        final String decryptedData = decode(dataToDecrypt, key); // decode message
        final String result[] = new String[2];
        String decryptedHash = decryptedData.substring(0, 40); // get hash from decoded data
        result[0] = decryptedData.substring(40); // get message from decoded data

        // trim up hashes
        final String remoteHash = decryptedHash.trim();
        final String localHash = encryptedHash.trim();

        if(localHash.equals(remoteHash)) { // check if hash is correct and return results
            result[1] = CONST.SIGNED;
            return result;
        } else {
            result[1] = CONST.UNSIGNED;
            return result;
        }
    }

    // encoding method for OTP
    private String encode(String s, char[] key) {
        String result = "";
        int i = 0;

        if(s.length() < key.length) {  //pad with spaces
            s = String.format("%1$-" + key.length + "s", s);
        }

        for (char character: s.toCharArray()) {
            int messageIndex = new String(charset).indexOf(character);
            int keyIndex = new String(charset).indexOf(key[i]);

            messageIndex++;
            keyIndex++;

            int encrypted = messageIndex + keyIndex;

            if(encrypted > charset.length) {
                encrypted = encrypted - charset.length;
            }

            encrypted--;

            result = result + charset[encrypted];
            i++;
        }

        return result;
    }

    // decoding method for OTP
    private String decode(String s, char[] key) {
        String result = null;

        int i = 0;

        for (char character: s.toCharArray()) {
            int messageIndex = new String(charset).indexOf(character);
            int keyIndex = new String(charset).indexOf(key[i]);
            messageIndex++;
            keyIndex++;

            int decrypted = messageIndex - keyIndex;

            if(decrypted < 1) {
                decrypted = decrypted + charset.length;
            }

            decrypted--;

            if(result == null) {
                result = String.valueOf(charset[decrypted]);
            } else {
                result = result + charset[decrypted];
            }

            i++;
        }

        return result;
    }

    // input filter for OTP text input to prevent invalid entries
    public InputFilter[] inputFilter(final Context context) {
        InputFilter filters[] = new InputFilter[2];
        filters[0] = new InputFilter.LengthFilter(context.getResources().getInteger(R.integer.config_message_size));
        filters[1] = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for(int index = start; index < end; index++) {
                    if(!new String(charset).contains(String.valueOf(source.charAt(index)))) {
                        Toast toast = Toast.makeText(context, context.getString(R.string.invalid_char), Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        return "";
                    }
                }

                return null;
            }
        };

        return filters;
    }
}
