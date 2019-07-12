package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


import static java.lang.Math.max;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko, Malvika
 *
 */

/*
  PRIORITY QUEUE REFERENCES

   1) https://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html
   The above reference was used to impement priority queue and understand the functionalities
   provided

   2)https://stackoverflow.com/questions/683041/how-do-i-use-a-priorityqueue
   The above reference was used to understand how to implement the comparator
   of a priority queue and the same logic is implemented from the link

   3)https://stackoverflow.com/questions/52802592/how-can-i-write-a-comparator-comparing-multiple-arguments
   The above reference was used to implemet tie breaks in message final proposed sequence,
   in case of equality additional comparison with sender id was used to sort the messages.
   The logic to implement the case of equality is used from the reference
 */
class Message {
    public Message(String message, Float processid,int processseq,char deliverornot) {
        msg = message; pid=processid; senderid=processseq;deliver=deliverornot;
    }
    public String msg;
    public Float pid;
    public int senderid;
    public char deliver;
}
//Ref - https://stackoverflow.com/questions/683041/how-do-i-use-a-priorityqueue
class PidComp implements Comparator<Message> {
    public int compare(Message s1, Message s2) {
        if (s1.pid < s2.pid)
        {
            return -1;
        }
        else if (s1.pid > s2.pid)
        {
            return 1;
        }
        // Ref: https://stackoverflow.com/questions/52802592/how-can-i-write-a-comparator-comparing-multiple-arguments
        else
        {
            if(s1.senderid < s2.senderid)
            {
                return -1;
            }
            else if(s1.senderid > s2.senderid)
            {
                return 1;
            }
            return 0;
        }

    }
}

//Used for the fifo queue on the sender side
class MsgQ {
    public MsgQ(String message,int btnclick) {
        msg = message; Bclick =btnclick;
    }
    public String msg;
    public int Bclick;
}
class BtComp implements Comparator<MsgQ> {
    public int compare(MsgQ s1, MsgQ s2) {
        if (s1.Bclick< s2.Bclick)
        {
            return -1;
        }
        if (s1.Bclick > s2.Bclick)
        {
            return 1;
        }
        return 0;
    }
}

public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    //Setting up the remote ports as an Array
    static final String[] REMOTE_PORTS=new String[]{"11108","11112","11116","11120","11124"};

    //To check the messages need to be sent and how many sent
    private int btnClicks=0;
    int msgCount=0;

    //Initialising Priority Queue for ISIS algorithm and for FIFO ordering
    Comparator<Message> comparator = new PidComp();
    PriorityQueue<Message> queue =
            new PriorityQueue<Message>(100, comparator);

    Comparator<MsgQ> fifocomp = new BtComp();
    PriorityQueue<MsgQ> fifoqueue =
            new PriorityQueue<MsgQ>(100, fifocomp);

    //Initialising agreed and proposed priority
    float agreedP=0,PropP=0;
    //Message delivery sequence number for content provider
    int delseq=0;

    static final int SERVER_PORT = 10000;
    //Declaring the column names of the Content Provider
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";


    //Uri Builder module to build the uri in the required format for Content Provider
    //The module is taken from the OnPTestClickListener Code
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);


        //Creating content resolver to delete any message created during the previous run
        ContentResolver mContentResolver1= getContentResolver();
        //Creating the provider URI
        final Uri mUri1= buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        int res=mContentResolver1.delete(mUri1,null,null);


        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        // The below snippets are taken from PA -1

        //To get the information about the current AVD port from the telephony manager
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        //Create a server task which listens on port 10000
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);


            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        //Creating the client task that runs infinitely till the app is closed or killed
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,myPort);

        //Registering the send button with onclicklistener and sending the message(i.e creating a clientTask)
        final EditText editText = (EditText) findViewById(R.id.editText1);
        final View sendbtn=(View)  findViewById(R.id.button4);
        sendbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                Log.i("client message",msg);
                btnClicks+=1;
                MsgQ msgObject=new MsgQ(msg,btnClicks);
                fifoqueue.add(msgObject);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    // The below code snippet is also taken from PA-1

    /************************************************************
     ---------------
     References Used
     ---------------

     1) https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
     The above reference was used to understand the concept of sockets,client,server sockets and how to implement
     input and output streams along with BufferedReader and PrintWriter respectively

     2) https://docs.oracle.com/javase/7/docs/api/java/net/ServerSocket.html
     The above reference was used to understand the methods available for ServerSocket and their functionality

     3) https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html
     The above reference was used to understand the methods available for Socket and their functionality

     4) https://stackoverflow.com/questions/10240694/java-socket-api-how-to-tell-if-a-connection-has-been-closed/10241044#10241044
     The above reference was to understand the concept of socket closing and how to identify that with readLine

     5)https://docs.oracle.com/javase/7/docs/api/java/io/BufferedReader.html
     The above docs was used to understand the BufferedReader Class and their functionality

     6)https://docs.oracle.com/javase/7/docs/api/java/io/PrintWriter.html
     The above docs was used to understand the PrintWriter Class and their functionality

     7)https://docs.oracle.com/javase/tutorial/essential/concurrency/sleep.html
     The above docs was used to understand sleep in a thread


     SOCKET TIMEOUT REFERENCES
     8)https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#connect(java.net.SocketAddress,%20int)
     9)https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#getSoTimeout()

     Both the above links was used as reference to implement socket timeout while creating connection
     and for read timeouts

     ************************************************************************/

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            float agreedmax=Float.MIN_VALUE;

        try
        {
            //Server keeps listening for connections and accepting them and passing the message to Progress update
            while(true) {

                //Accepts an incoming client connection
                Socket client = serverSocket.accept();
                try {
                    //setting the socket timeout for read
                    client.setSoTimeout(3000);


                    //Reads the message from the input stream and sends to Progress Update through publish progress
                    BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String message = input.readLine();



                    if (message.contains("MESSAGE")) {


                        //process id alias receiver id
                        int prid = Integer.parseInt(message.split("-")[1].split(":")[0]);
                        //sender id
                        int sendid = Integer.parseInt(message.split(":")[1].split(";")[0]);

                        message = message.split(":")[1].split(";")[1];


                        float Pnumber = 0;

                        if (agreedP == 0 && PropP == 0) {
                            //PropP = max(agreedmax, PropP) + 1;
                            PropP = PropP + 1;
                            Pnumber = PropP + (prid * 0.1f);
                            PropP = Pnumber;
                        } else {

                            PropP = max(agreedmax, PropP) + 1;
                            Pnumber = PropP;
                        }
                        Message m1 = new Message(message, Pnumber,sendid,'N');
                        queue.add(m1);
                        Log.i("SERVER TASK", "Added Msg to Q: "+Integer.toString(queue.size()));

                        String propNumber = "Proposed Number:" + Float.toString(Pnumber);
                        PrintWriter outserverProp = new PrintWriter(client.getOutputStream(), true);
                        outserverProp.println(propNumber);

                        Log.i("SERVER TASK", "sent proposal");
                    } else if (message.contains("Final Proposal")) {


                        String refmsg = message.split("-")[1].split(":")[1];
                        Float AgreedNum = Float.parseFloat(message.split("-")[1].split(":")[0]);
                        int failed= Integer.parseInt(message.split(";")[1].split(";")[0]);

                        Log.i("SERVER TASK", refmsg);

                        agreedP = AgreedNum;
                        agreedmax=max(agreedP,agreedmax);
                        Iterator<Message> it = queue.iterator();
                        while (it.hasNext()) {
                            Message m = it.next();
                            if (m.msg.contentEquals(refmsg)) {
                                queue.remove(m);
                                m.pid = agreedP;
                                m.deliver = 'Y';
                                queue.add(m);
                                Log.i("SERVER TASK", "updating message: "+m.msg);
                                break;
                            }
                        }

                        //Log.i("Agreed Number:", Float.toString(agreedP));

                        while (!queue.isEmpty()) {
                            Message deliverMes = queue.peek();
                            Log.i("First Message", "Failed".concat(Integer.toString(deliverMes.senderid)).concat(Float.toString(deliverMes.pid)).concat(":").concat(deliverMes.msg));

                            if (deliverMes.deliver == 'Y') {
                                String mesdel = deliverMes.msg;
                                queue.remove(deliverMes);
                                publishProgress(mesdel, Integer.toString(delseq));
                                Log.i("Message delivered", "Failed:".concat(Integer.toString(deliverMes.senderid)).concat("-").concat(Float.toString(deliverMes.pid)).concat(":").concat(mesdel));
                                Log.i("Message in Queue",Integer.toString(queue.size()));
                                delseq += 1;
                            }
                            else if(failed>0 && deliverMes.deliver == 'N')
                            {
                                if(deliverMes.senderid==failed) {
                                    queue.remove(deliverMes);
                                    Log.i("Message Failed", "Failed".concat(Integer.toString(deliverMes.senderid)).concat(Float.toString(deliverMes.pid)).concat(":").concat(deliverMes.msg));
                                    Log.i("Failed Message in Queue", Integer.toString(queue.size()));
                                }else{
                                    break;
                                }
                            }
                            else {
                                break;
                            }
                        }

                    }
                    Thread.sleep(500);
                    client.close();


                }catch(SocketTimeoutException e)
                {
                    Log.e(TAG,e.toString());
                    client.close();
                }
            }
        }
        catch(Exception e)
        {
            Log.e(TAG,"Server couldn't accept a Client");
            Log.e(TAG, e.toString());
        }


            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            //Receiving the message
            String strReceived = strings[0].trim();
            String key=strings[1].trim();
            //Log.i("key",key);
            //Displaying the message in the textView
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            //Appending the key and message(value) to the content values
            final ContentValues mContentValues=new ContentValues();
            //Getting the contentResolver for this context
            ContentResolver mContentResolver= getContentResolver();
            mContentValues.put(KEY_FIELD,key);
            mContentValues.put(VALUE_FIELD,strReceived);
            //Creating the provider URI
            final Uri mUri= buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            try {
                //Inserting the value to content provider and in turn to the file
                mContentResolver.insert(mUri, mContentValues);
            }catch(Exception e)
            {
                Log.e(TAG,e.toString());
            }
            return;
        }

    }

    // The below code snippet is also taken from PA-1 and PA-2A

    /************************************************************
     ---------------
     References Used
     ---------------
     1) https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
     The above reference was used to understand the concept of sockets,client,server sockets and how to implement
     input and output streams along with BufferedReader and PrintWriter respectively

     2) https://docs.oracle.com/javase/7/docs/api/java/net/ServerSocket.html
     The above reference was used to understand the methods available for ServerSocket and their functionality

     3) https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html
     The above reference was used to understand the methods available for Socket and their functionality

     4) https://stackoverflow.com/questions/10240694/java-socket-api-how-to-tell-if-a-connection-has-been-closed/10241044#10241044
     The above reference was to understand the concept of socket closing and how to identify that with readLine

     5)https://docs.oracle.com/javase/7/docs/api/java/io/BufferedReader.html
     The above docs was used to understand the BufferedReader Class and their functionality

     6)https://docs.oracle.com/javase/7/docs/api/java/io/PrintWriter.html
     The above docs was used to understand the PrintWriter Class and their functionality

     7)https://docs.oracle.com/javase/tutorial/essential/concurrency/sleep.html
     The above docs was used to understand sleep in a thread

     SOCKET TIMEOUT REFERENCES
     8) https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#connect(java.net.SocketAddress,%20int)
     9) https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html#getSoTimeout()

     Both the above links was used as reference to implement socket timeout while creating connection
     and for read timeouts


     ************************************************************************/

    private class ClientTask extends AsyncTask<String, Void, Void> {

        boolean done=true;

        @Override
        protected Void doInBackground(String... msgs) {

        String remoteport = msgs[0];
        //Log.i("REMOTE PORT",remoteport);
        int j = 0;

        for (int i = 0; i < 5; i++) {
            if (REMOTE_PORTS[i].contentEquals(remoteport)) {
                j = i + 1;
                break;
            }
        }
        int sender=j;
        Log.i("CLIENT TASK ID", Integer.toString(sender));
        int failed=0;
        do {
            int propCount = 0;
            try {


            if (!fifoqueue.isEmpty()) {
                MsgQ msg = fifoqueue.peek();
                if (msgCount + 1 == msg.Bclick) {

                    MsgQ message = fifoqueue.poll();
                    String msgToSend = message.msg;
                    float maxprop = Float.MIN_VALUE;
                    for (int i = 0; i < 5; i++) {
                        if (failed != i + 1) {
                            try {

                                Socket socket = new Socket();
                                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(REMOTE_PORTS[i])), 4000);


                                //Sending the message to the receivers
                                PrintWriter output0 = new PrintWriter(socket.getOutputStream(), true);
                                String msg0 = "MESSAGE-".concat(Integer.toString(i + 1)).concat(":").concat(Integer.toString(sender)).concat(";").concat(msgToSend);
                                output0.println(msg0);

                                try {

                                    //Waiting for the proposals from all the five receivers
                                    socket.setSoTimeout(2500);
                                    BufferedReader inserver0 = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                    String msgRcvd0 = inserver0.readLine();
                                    if (msgRcvd0.contains("Proposed Number")) {
                                        Float pn = Float.parseFloat(msgRcvd0.split(":")[1]);
                                        maxprop = max(maxprop, pn);
                                        propCount += 1;
                                        Log.i(TAG, "Received Proposed Number" + msg0.concat(": ").concat(Float.toString(pn)));

                                    }
                                    Thread.sleep(100);
                                    socket.close();
                                } catch (SocketTimeoutException e) {
                                    Log.e("Socket Timeout excep 1", e.toString());
                                    Log.e("Socket Timeout 1 at", Integer.toString(i));
                                    socket.close();
                                    failed = i + 1;

                                } catch (IOException e) {
                                    Log.e("IO Exception", e.toString());
                                    socket.close();
                                    failed = i + 1;
                                } catch (NullPointerException e) {
                                    Log.e("Null Pointer Exception", e.toString());
                                    socket.close();
                                    failed = i + 1;
                                } catch (InterruptedException e) {
                                    Log.e("Interrupted Exception", e.toString());
                                }

                            } catch (SocketTimeoutException e) {
                                Log.e("Socket Timeout excep 2", e.toString());
                                Log.e("Socket timeout at", Integer.toString(i));
                                failed = i + 1;

                            } catch (IOException e) {

                                Log.e("Socket IOexception 2", e.toString());
                                Log.e("Socket IO at", Integer.toString(i));
                                failed = i + 1;

                            }
                        }

                    }

                        Thread.sleep(100);
                        //Sending the final proposals to the receivers
                        if (propCount == 5 || failed > 0) {
                            Float maxval = maxprop;
                            Log.i("MAX", Float.toString(maxprop));
                            propCount = 0;
                            for (int i = 0; i < 5; i++) {
                                try {
                                    if (failed != i + 1) {
                                        Socket socket00 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                                Integer.parseInt(REMOTE_PORTS[i]));

                                        PrintWriter out0 = new PrintWriter(socket00.getOutputStream(), true);
                                        String prop0 = "Failed;".concat(Integer.toString(failed)).concat(";Final Proposal-").concat(Float.toString(maxval)).concat(":").concat(msgToSend);
                                        out0.println(prop0);
                                        Log.i("prop", prop0);

                                        Thread.sleep(100);
                                        socket00.close();
                                    }
                                } catch (SocketTimeoutException e) {
                                    Log.e("Socket Timeout Send", e.toString());
                                } catch (IOException e) {
                                    Log.e("Socket creation Send", e.toString());
                                } catch (InterruptedException e) {
                                    Log.e("Interrupted Exception", e.toString());
                                }

                            }
                            //Incrementing the messages
                            msgCount += 1;
                        } else {
                            Log.e("CLIENT TASK", "No finalprop received");
                        }

                    }

                }

                }catch(InterruptedException e)
                {
                    Log.e(TAG,"Interrupted Exception");
                }
            }while(done);


            return null;
        }

        /*
          https://stackoverflow.com/questions/6373826/execute-asynctask-several-times
          The above reference was used to understand how to clean up Asynctask as it runs
          infinitely when a process crashes
        */
        public void terminateTask() {
            // The task will only finish when we call this method
            done = false;
        }

        @Override
        protected void onCancelled() {
            // Make sure we clean up if the task is killed
            terminateTask();
        }

    }

}
