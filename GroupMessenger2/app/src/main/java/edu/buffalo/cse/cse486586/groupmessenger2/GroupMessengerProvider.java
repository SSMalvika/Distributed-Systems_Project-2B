package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Set;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko , Malvika
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    //Declaring the key and value field
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        //Deleting all the files created by the previous run
        Log.i("File","inside Delete");
        File op[]=getContext().getFilesDir().listFiles();
        Log.i("FILE DIR",Integer.toString(op.length));
        for(int i=0;i<op.length;i++)
        {
            if(op[i].exists())
            {
                op[i].delete();
                Log.i("File Deleted",op[i].getAbsolutePath());
            }
        }



        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         *
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */

        /*
           *  REFERENCES:
              ------------
              1)PA - 1 to learn how to use files and use them
              2)https://developer.android.com/reference/android/content/Context
                 To understand the concept of context and for using openFileOutput, and also to check various write
                 modes which provides delete contents and overwrite(mode is MODE_PRIVATE)

       */
        //Values are retrieved from the keys(which is key and value) and written to the file
        String value=values.getAsString(VALUE_FIELD);
        String filename=values.getAsString(KEY_FIELD);
        FileOutputStream outputStream;
        try {
            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();

        } catch (Exception e) {
            Log.e("error", "File write failed");
        }

        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */

        /*
         *  REFERENCES:
         *   ------------
         *   1)PA - 1 to learn how to use files
         *   2)https://developer.android.com/reference/android/content/Context
         *   3)https://developer.android.com/reference/android/content/Context.html#openFileInput(java.lang.String)
         *     For using openFileInput and the return object associated with it
         *   4)https://developer.android.com/reference/java/io/FileInputStream.html
         *     To understand FileInputStream and its member functions,to read the file contents which is in bytes
         *     and convert them to string.
         *   5)https://developer.android.com/reference/java/io/FileInputStream.html#available()
         *     The available method returns the number of bytes available to read
         *     and the byte array is converted to string
         *   6)http://developer.android.com/reference/android/database/MatrixCursor.html
         *     To understand how to use matrix cursor and the function addRow

         */
        String[] columnNames={KEY_FIELD,VALUE_FIELD};
        MatrixCursor mc=new MatrixCursor(columnNames);
        try {
            //Thread.sleep(1000);
            FileInputStream in = getContext().openFileInput(selection);
            int n=in.available();
            byte[] result=new byte[n];
            in.read(result);
            String value=new String(result);
            mc.addRow(new Object[]{selection,value});
           // Thread.sleep(1000);
        }catch(Exception e)
        {
            Log.e("error","File not found");
        }
        Log.v("query", selection);
        return mc;
    }
}
