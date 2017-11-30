package com.robot.olpit.loomonavigation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.segway.robot.algo.Pose2D;
import com.segway.robot.algo.dts.DTSPerson;
import com.segway.robot.algo.dts.PersonDetectListener;
import com.segway.robot.algo.dts.PersonTrackingListener;
import com.segway.robot.algo.minicontroller.CheckPoint;
import com.segway.robot.algo.minicontroller.CheckPointStateListener;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.baseconnectivity.Message;
import com.segway.robot.sdk.baseconnectivity.MessageConnection;
import com.segway.robot.sdk.baseconnectivity.MessageRouter;
import com.segway.robot.sdk.connectivity.RobotException;
import com.segway.robot.sdk.connectivity.RobotMessageRouter;
import com.segway.robot.sdk.connectivity.StringMessage;
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.vision.DTS;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.voice.Languages;
import com.segway.robot.sdk.voice.Recognizer;
import com.segway.robot.sdk.voice.Speaker;
import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.grammar.GrammarConstraint;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.segway.robot.sdk.voice.recognition.WakeupListener;
import com.segway.robot.sdk.voice.recognition.WakeupResult;
import com.segway.robot.sdk.voice.tts.TtsListener;
import com.segway.robot.support.control.HeadPIDController;

import java.util.Locale;

/**
 * Created by Alex Pitkin on 24.11.2017.
 */

public class DtsFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "DtsFragment";
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;

    private Vision mVision;
    private DTS mDTS;
    private Recognizer mRecognizer;
    private Speaker mSpeaker;
    private RobotMessageRouter mRobotMessageRouter = null;
    private MessageConnection mMessageConnection = null;
    private Head mHead;
    private Base mBase;
    private HeadPIDController mHeadPIDController = new HeadPIDController();

    private boolean isDetectionStarted = false;
    private boolean isTrackingStarted = false;
    private boolean mBaseBind;
    private boolean mHeadBind;
    private Boolean isMoving = true;
    boolean mHeadFollow;
    boolean mBaseFollow;
    boolean mBaseGuide = true;
    boolean threadRunning = false;

    public float personDistance;

    private ServiceBinder.BindStateListener mBindStateListener;
    private ServiceBinder.BindStateListener mRecognitionBindStateListener;
    private ServiceBinder.BindStateListener mSpeakerBindStateListener;
    private CheckPointStateListener mCheckPointStateListener;
    private ServiceBinder.BindStateListener mVisionBindStateListener;
    private TtsListener mTtsListener;
    private ServiceBinder.BindStateListener mHeadBindStateListener;
    private ServiceBinder.BindStateListener mBaseBindStateListener;
    private ServiceBinder.BindStateListener mBaseListner;
    private PersonDetectListener mPersonDetectListener;
    private PersonTrackingListener mPersonTrackingListener;
    private MessageRouter.MessageConnectionListener mMessageConnectionListener;
    private MessageConnection.MessageListener mMessageListener;
    private MessageConnection.ConnectionStateListener mConnectionStateListener;
    private WakeupListener mWakeupListener;
    private RecognitionListener mRecognitionListener;

    enum DtsState {
        STOP,
        DETECTING,
        TRACKING
    }

    DtsState mDtsState;

    private int controlSignal = 0;
    private int mSpeakerLanguage;
    private int mRecognitionLanguage;
    private GrammarConstraint mThreeSlotGrammar;

    private TextView hintTv;
    private AutoFitDrawableView mTextureView;

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static DtsFragment newInstance() {
        return new DtsFragment();
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            bindServices();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.detect).setOnClickListener(this);
        view.findViewById(R.id.track).setOnClickListener(this);
        view.findViewById(R.id.head_follow).setOnClickListener(this);
        mTextureView = (AutoFitDrawableView) view.findViewById(R.id.texture);
        hintTv = (TextView) view.findViewById(R.id.hint_tv);
        threadRunning = false;
        Button debug = (Button) view.findViewById(R.id.debug);
        debug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendString("debug");
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mVision = Vision.getInstance();
        mHead = Head.getInstance();
        mBase = Base.getInstance();
        switchLanguage(Locale.getDefault());
        mRecognizer = Recognizer.getInstance();
        mSpeaker = Speaker.getInstance();
        mRobotMessageRouter = RobotMessageRouter.getInstance();
        mBase = Base.getInstance();
        hintTv.setText(getDeviceIp());
        initListners();
    }

    @Override
    public void onPause() {
        unbindServices();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mRecognizer != null) {
            mRecognizer = null;
        }
        if (mSpeaker != null) {
            mSpeaker = null;
        }
        super.onDestroy();
    }

    private void initListners() {

        mCheckPointStateListener = new CheckPointStateListener() {
            @Override
            public void onCheckPointArrived(CheckPoint checkPoint, Pose2D realPose, boolean isLast) {
            }

            @Override
            public void onCheckPointMiss(CheckPoint checkPoint, Pose2D realPose, boolean isLast, int reason) {
            }
        };

        mVisionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mDTS = mVision.getDTS();
                mDTS.setVideoSource(DTS.VideoSource.CAMERA);
                Surface surface = new Surface(mTextureView.getPreview().getSurfaceTexture());
                mDTS.setPreviewDisplay(surface);
                mDTS.start();
            }

            @Override
            public void onUnbind(String reason) {
                mDTS = null;
            }
        };

        mHeadBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mHeadBind = true;
                mHead.setMode(Head.MODE_ORIENTATION_LOCK);
                mHead.setWorldPitch(0.3f);
                mHeadPIDController.init(new HeadControlHandlerImpl(mHead));
                mHeadPIDController.setHeadFollowFactor(1.0f);
            }

            @Override
            public void onUnbind(String reason) {
                mHeadBind = false;
            }
        };

        mBaseBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mBaseBind = true;
            }

            @Override
            public void onUnbind(String reason) {
                mBaseBind = false;
            }
        };

        mPersonDetectListener = new PersonDetectListener() {
            @Override
            public void onPersonDetected(DTSPerson[] person) {
                if (person == null) {
                    return;
                }
                if (person.length > 0) {
                    mTextureView.drawRect(person);
                }
            }

            @Override
            public void onPersonDetectionResult(DTSPerson[] person) {

            }

            @Override
            public void onPersonDetectionError(int errorCode, String message) {

            }
        };

        mPersonTrackingListener = new PersonTrackingListener() {
            @Override
            public void onPersonTracking(final DTSPerson person) {
                Log.d(TAG, "onPersonTracking: " + person);
                if (person == null) {
                    return;
                }
                mTextureView.drawRect(person.getId(), person.getDrawingRect());
                //mTextureView.drawRect(person.getDrawingRect());

                if (mHeadFollow) {
                    mHeadPIDController.updateTarget(person.getTheta(), person.getDrawingRect(), 480);
                }
                if (mBaseFollow) {
                    float personDistance = person.getDistance();
                    // There is a bug in DTS, while using person.getDistance(), please check the result
                    // The correct distance is between 0.35 meters and 5 meters
                    if (personDistance > 0.35 && personDistance < 5) {
                        float followDistance = (float) (personDistance - 1.2);
                        float theta = person.getTheta();
                        Log.d(TAG, "onPersonTracking: update base follow distance=" + followDistance + " theta=" + theta);
                        mBase.updateTarget(followDistance, theta);
                    }
                } else if (mBaseGuide) {
                    personDistance = person.getDistance();

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hintTv.setText(String.format("%.2f", personDistance) + "m");
                        }
                    });
                }
            }

            @Override
            public void onPersonTrackingResult(DTSPerson person) {
                //  Log.d(TAG, "onPersonTrackingResult() called with: person = [" + person + "]");
            }

            @Override
            public void onPersonTrackingError(int errorCode, String message) {
                showToast("Person tracking error: code=" + errorCode + " message=" + message);
                mDtsState = DtsState.STOP;
            }
        };

        mBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "onBind");
                Toast.makeText(getActivity().getApplicationContext(), "Service bind success", Toast.LENGTH_SHORT).show();
                try {
                    //register MessageConnectionListener in the RobotMessageRouter
                    mRobotMessageRouter.register(mMessageConnectionListener);
                } catch (RobotException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnbind(String reason) {
                Log.e(TAG, "onUnbind: " + reason);
                Toast.makeText(getActivity().getApplicationContext(), "Service bind FAILED", Toast.LENGTH_SHORT).show();
            }
        };

        mConnectionStateListener = new MessageConnection.ConnectionStateListener() {
            @Override
            public void onOpened() {
                Log.d(TAG, "onOpened: ");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity().getApplicationContext(), "connected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onClosed(String error) {
                Log.e(TAG, "onClosed: " + error);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity().getApplicationContext(), "disconnected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };

        mMessageListener = new MessageConnection.MessageListener() {
            @Override
            public void onMessageSentError(Message message, String error) {
                Log.d(TAG, "Message send error");
            }

            @Override
            public void onMessageSent(Message message) {
                Log.d(TAG, "Message sent");
            }

            @Override
            public void onMessageReceived(final Message message) {
                if (message instanceof StringMessage) {
                    String m = message.getContent().toString();
                    Log.i("MESSAGE", m + threadRunning);
                    if (m.equals("STOP")) {
                        controlSignal = 0;
                        mBase.setLinearVelocity(0);
                        mBase.setAngularVelocity(0);
                        isMoving = false;
                    }
                    else if (m.equals("GO")) {
                        isMoving = true;
                    }
                    else if(Long.parseLong(m) == 0) {
                        //STOP
                        controlSignal = 0;
                    }
                    else if(Long.parseLong(m) == 1) {
                        controlSignal = 1;
                    }
                    else if(Long.parseLong(m) == 2) {
                        controlSignal = 2;
                    }
                    else if(Long.parseLong(m) == 3) {
                        controlSignal = 3;
                    }
                    else if(Long.parseLong(m) == 4) {
                        controlSignal = 4;
                    }
                    else if(Long.parseLong(m) == 5) {
                        controlSignal = 5;
                    }
                    else if(Long.parseLong(m) == 6) {
                        controlSignal = 6;
                    }
                }
                if (!threadRunning) {
                    threadRunning = true;
                    processControl();
                }
            }
        };

        mMessageConnectionListener = new RobotMessageRouter.MessageConnectionListener() {
            @Override
            public void onConnectionCreated(final MessageConnection connection) {
                Log.d(TAG, "onConnectionCreated: " + connection.getName());
                mMessageConnection = connection;
                try {
                    mMessageConnection.setListeners(mConnectionStateListener, mMessageListener);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        };

        mBaseListner = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "Base bind success");
                mBase.setControlMode(Base.CONTROL_MODE_RAW);
            }

            @Override
            public void onUnbind(String reason) {
                Log.d(TAG, "Base bind failed");
            }
        };

        mRecognitionListener = new RecognitionListener() {
            @Override
            public void onRecognitionStart() {
                Log.d(TAG, "onRecognitionStart");
            }

            @Override
            public boolean onRecognitionResult(RecognitionResult recognitionResult) {
                //show the recognition result and recognition result confidence.
                Log.d(TAG, "recognition phase: " + recognitionResult.getRecognitionResult() +
                        ", confidence:" + recognitionResult.getConfidence());
                String result = recognitionResult.getRecognitionResult();

                if (result.contains("bring") || result.contains("guide") || result.contains("get")) {
                    if (result.contains("Weber")) {
                        sendString("Weber");
                    } else if (result.contains("room")) {
                        sendString("room");
                    } else if (result.contains("toilet")) {
                        sendString("toilet");
                    } else if (result.contains("secretary")) {
                        sendString("secretary");
                    }
                    try {
                        mSpeaker.speak("follow me", mTtsListener);
                    } catch (VoiceException e) {
                        e.printStackTrace();
                    }
                    return true;
                    //true means continuing to recognition, false means wakeup.
                }
                return false;
            }

            @Override
            public boolean onRecognitionError(String s) {
                Log.d(TAG, "onRecognitionError: " + s);

                try {
                    Log.e("TAG", "speech");
                    mSpeaker.speak("i don't understand", mTtsListener);
                } catch (VoiceException e) {
                    e.printStackTrace();
                }

                return false; //to wakeup
            }
        };

        mWakeupListener = new WakeupListener() {
            @Override
            public void onStandby() {
                Log.d(TAG, "Wakeuplistner onStandby");
            }

            @Override
            public void onWakeupResult(WakeupResult wakeupResult) {
                Log.d(TAG, "wakeup word:" + wakeupResult.getResult() + ", angle " + wakeupResult.getAngle());
            }

            @Override
            public void onWakeupError(String s) {
                Log.d(TAG, "onWakeupError");
            }
        };

        mRecognitionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.i(TAG,"recognition bind");
                try {
                    mRecognitionLanguage = mRecognizer.getLanguage();
                    switch (mRecognitionLanguage) {
                        case Languages.EN_US:
                            addEnglishGrammar();
                            break;
                    }
                    mRecognizer.setSoundEnabled(true);
                    mRecognizer.startWakeupAndRecognition(mWakeupListener, mRecognitionListener);
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
            }

            @Override
            public void onUnbind(String s) {
                Log.d(TAG, "recognition service onUnbind");
            }
        };

        mSpeakerBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "speaker service onBind");
                try {
                    mSpeakerLanguage = mSpeaker.getLanguage();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
            }

            @Override
            public void onUnbind(String s) {
                Log.d(TAG, "speaker service onUnbind");
                //speaker service or recognition service unbind, disable function buttons.
            }
        };

        mTtsListener = new TtsListener() {
            @Override
            public void onSpeechStarted(String s) {
                //s is speech content, callback this method when speech is starting.
                Log.d(TAG, "onSpeechStarted() called with: s = [" + s + "]");
            }

            @Override
            public void onSpeechFinished(String s) {
                //s is speech content, callback this method when speech is finish.
                Log.d(TAG, "onSpeechFinished() called with: s = [" + s + "]");
            }

            @Override
            public void onSpeechError(String s, String s1) {
                //s is speech content, callback this method when speech occurs error.
                Log.d(TAG, "onSpeechError() called with: s = [" + s + "], s1 = [" + s1 + "]");
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTextureView.getPreview().isAvailable()) {
            bindServices();
        } else {
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            mTextureView.setPreviewSizeAndRotation(PREVIEW_WIDTH, PREVIEW_HEIGHT, rotation);
            mTextureView.setSurfaceTextureListenerForPerview(mSurfaceTextureListener);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.detect: {
                if (!isDetectionStarted) {
                    mDTS.startDetectingPerson(mPersonDetectListener);
                    isDetectionStarted = true;
                    isTrackingStarted = false;
                } else {
                    mDTS.stopDetectingPerson();
                    isDetectionStarted = false;
                }
                break;
            }
            case R.id.track: {
                if (!isTrackingStarted) {
                    mDTS.startPersonTracking(null, 15L * 60 * 1000 * 1000, mPersonTrackingListener);
                    isTrackingStarted = true;
                    isDetectionStarted = false;
                } else {
                    mDTS.stopPersonTracking();
                    isTrackingStarted = false;
                }
                break;
            }
            case R.id.head_follow: {
                if (!mHeadBind) {
                    return;
                }
                if (!mHeadFollow) {
                    mHeadFollow = true;
                } else {
                    mHeadFollow = false;
                    mHead.setWorldPitch(0.3f);
                    mHead.setWorldYaw(0.0f);
                }
                break;
            }
        }
    }

    private void bindServices() {
        mVision.bindService(this.getActivity(), mVisionBindStateListener);
        mHead.bindService(this.getActivity(), mHeadBindStateListener);
        mBase.bindService(this.getActivity(), mBaseBindStateListener);
        mRobotMessageRouter.bindService(this.getActivity(), mBindStateListener);
        mBase.bindService(this.getActivity(),mBaseListner);
        mRecognizer.bindService(this.getActivity(), mRecognitionBindStateListener);
        mSpeaker.bindService(this.getActivity(), mSpeakerBindStateListener);
    }

    private void unbindServices() {
        if (mDTS != null) {
            mDTS.stop();
            mDTS = null;
        }
        mVision.unbindService();
        mHead.unbindService();
        mHeadBind = false;
        mBase.unbindService();
        mRecognizer.unbindService();
        mSpeaker.unbindService();
        mRobotMessageRouter.unbindService();
    }

    private String getDeviceIp() {
        @SuppressLint("WifiManagerLeak") WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null ) {
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return (ipAddress & 0xFF) + "." + ((ipAddress >> 8) & 0xFF) + "." + ((ipAddress >> 16) & 0xFF) + "." + (ipAddress >> 24 & 0xFF);
        }
        return "unknown";
    }

    private void processControl() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (isMoving) {
                    //  if (mDtsState == TRACKING && personDistance > 0.5 && personDistance < 5) {
                    switch (controlSignal) {
                        case 0 :
                            mBase.setLinearVelocity(0);
                            mBase.setAngularVelocity(0);
                            break;
                        case 1 :
                            //LEFT TURN
                            mBase.setLinearVelocity(0);
                            mBase.setAngularVelocity(0.5f);
                            break;
                        case 2 :
                            // LEFT + F
                            mBase.setLinearVelocity(-0.5f);
                            mBase.setAngularVelocity(0.2f);
                            break;
                        case 3 :
                            // AHEAD
                            mBase.setLinearVelocity(-0.5f);
                            mBase.setAngularVelocity(0);
                            break;
                        case 4 :
                            // RIGHT + F
                            mBase.setLinearVelocity(-0.5f);
                            mBase.setAngularVelocity(-0.2f);
                            break;
                        case 5 :
                            // RIGHT
                            mBase.setLinearVelocity(0);
                            mBase.setAngularVelocity(-0.5f);
                            break;
                        case 6 :
                            // BACK
                            mBase.setLinearVelocity(0.3f);
                            mBase.setAngularVelocity(0);
                            break;
                        default:
                              mBase.setLinearVelocity(0);
                              mBase.setAngularVelocity(0);
                              break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void sendString(String c) {
        try {
            //message sent is StringMessage
            mMessageConnection.sendMessage(new StringMessage(c));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addEnglishGrammar() throws VoiceException {
        String grammarJson = "{\n" +
                "         \"name\": \"destination\",\n" +
                "         \"slotList\": [\n" +

                "             {\n" +
                "                 \"name\": \"word1\",\n" +
                "                 \"isOptional\": false,\n" +
                "                 \"word\": [\n" +
                "                     \"take me to\",\n" +
                "                     \"bring me to\",\n" +
                "                     \"get me to \"\n" +
                "                 ]\n" +
                "             },\n" +

                "             {\n" +
                "                 \"name\": \"word2\",\n" +
                "                 \"isOptional\": true,\n" +
                "                 \"word\": [\n" +
                "                     \"the room\",\n" +
                "                     \"the \",\n" +
                "                     \"professor \"\n" +
                "                 ]\n" +
                "             },\n" +

                "             {\n" +
                "                 \"name\": \"word3\",\n" +
                "                 \"isOptional\": true,\n" +
                "                 \"word\": [\n" +
                "                     \"David\",\n" +
                "                     \"Weber \",\n" +
                "                     \"toilet \"\n" +
                "                 ]\n" +
                "             }\n" +

                "         ]\n" +
                "     }";

        //TODO SIMPLE COMMANDS TURN LEFT RIGHT STOP GO
        mThreeSlotGrammar = mRecognizer.createGrammarConstraint(grammarJson);
        mRecognizer.addGrammarConstraint(mThreeSlotGrammar);
    }

    public void switchLanguage(Locale locale) {
        Configuration config = getResources().getConfiguration();
        Resources resources = getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.locale = locale;
        resources.updateConfiguration(config, dm);
    }
}
