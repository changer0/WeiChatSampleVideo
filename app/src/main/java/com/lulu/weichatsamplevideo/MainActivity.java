package com.lulu.weichatsamplevideo;

import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnTouchListener, BothWayProgressBar.OnProgressEndListener {

    private static final int LISTENER_START = 200;
    private static final String TAG = "MainActivity";
    //预览SurfaceView
    private SurfaceView mPreview;
    private Camera mCamera;
    //底部"按住拍"按钮
    private View mStartButton;
    //进度条
    private BothWayProgressBar mProgressBar;
    //进度条线程
    private Thread mProgressThread;
    //录制视频
    private MediaRecorder mMediaRecorder;
    private SurfaceHolder mSurfaceHolder;
    //屏幕分辨率
    private int videoWidth, videoHeight;
    //判断是否正在录制
    private boolean isRecording;
    private File mTargetFile;
    //当前进度/时间
    private int mProgress;
    //录制最大时间
    public static final int MAX_TIME = 10;
    //
    private boolean isCancel;

    private MyHandler mHandler;
    private TextView mTvTip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        videoWidth = 640;
        videoHeight = 480;
        mPreview = (SurfaceView) findViewById(R.id.main_surface_view);

        mSurfaceHolder = mPreview.getHolder();
        //设置屏幕分辨率
        mSurfaceHolder.setFixedSize(videoWidth, videoHeight);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(this);
        mStartButton = findViewById(R.id.main_press_control);
        mTvTip = (TextView) findViewById(R.id.main_tv_tip);

        mStartButton.setOnTouchListener(this);
        //自定义双向进度条    (这个地方差点把我急疯了!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!)
        mProgressBar = (BothWayProgressBar) findViewById(R.
                id.main_progress_bar);
        mProgressBar.setOnProgressEndListener(this);

        mHandler = new MyHandler(this);
        mMediaRecorder = new MediaRecorder();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceView回调
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        startPreView(holder);
    }

    /**
     * 开启预览
     *
     * @param holder
     */
    private void startPreView(SurfaceHolder holder) {
        Log.d(TAG, "startPreView: ");

        if (mCamera == null) {
            mCamera = Camera.open();
        }
        if (mCamera != null) {
            mCamera.setDisplayOrientation(90);
            try {
                mCamera.setPreviewDisplay(holder);
                Camera.Parameters parameters = mCamera.getParameters();
                //实现Camera自动对焦
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String mode : focusModes) {
                        mode.contains("continuous-video");
                        parameters.setFocusMode("continuous-video");
                    }
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            Log.d(TAG, "surfaceDestroyed: ");
            //停止预览并释放摄像头资源
            mCamera.stopPreview();
            mCamera.release();

        }
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
        }
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean ret = false;
        int action = event.getAction();
        float ey = event.getY();
        float ex = event.getX();
        //只监听中间的按钮处
        int vW = v.getWidth();
        int left = LISTENER_START;
        int right = vW - LISTENER_START;

        float downY = 0;

        switch (v.getId()) {
            case R.id.main_press_control:
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (ex > left && ex < right) {
                            mProgressBar.setCancel(false);
                            //显示上滑取消
                            mTvTip.setVisibility(View.VISIBLE);
                            mTvTip.setText("↑ 上滑取消");
                            //记录按下的Y坐标
                            downY = ey;
                            // TODO: 2016/10/20 开始录制视频, 进度条开始走
                            mProgressBar.setVisibility(View.VISIBLE);
                            //开始录制
                            Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
                            startRecord();

                            mProgressThread = new Thread() {
                                @Override
                                public void run() {
                                    super.run();
                                    try {
                                        mProgress = 0;
                                        while (true) {
                                            mProgress++;
                                            mHandler.obtainMessage(0).sendToTarget();
                                            Thread.sleep(20);
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };

                            mProgressThread.start();
                            ret = true;
                        }

                        break;
                    case MotionEvent.ACTION_UP:
                        if (ex > left && ex < right) {
                            mTvTip.setVisibility(View.INVISIBLE);
                            mProgressBar.setVisibility(View.INVISIBLE);
                            //判断是否为录制结束, 或者为成功录制(时间过短)
                            if (!isCancel) {
                                if (mProgress < 50) {
                                    //时间太短不保存
                                    stopRecordUnSave();
                                    Toast.makeText(this, "时间太短", Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                //停止录制
                                stopRecordSave();
                            } else {
                                //现在是取消状态,不保存
                                stopRecordUnSave();
                                isCancel = false;
                                Toast.makeText(this, "取消录制", Toast.LENGTH_SHORT).show();
                                mProgressBar.setCancel(false);
                            }

                            ret = false;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (ex > left && ex < right) {
                            float currentY = event.getY();
                            if (downY - currentY > 10) {
                                isCancel = true;
                                mProgressBar.setCancel(true);
                            }
                        }
                        break;
                }
                break;
        }
        return ret;
    }

    ///////////////////////////////////////////////////////////////////////////
    // 进度条结束后的回调方法
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void onProgressEndListener() {
        //视频停止录制
    }

    /**
     * 开始录制
     */
    private void startRecord() {
        if (mMediaRecorder != null) {
            //没有外置存储, 直接停止录制
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                return;
            }
            try {
                //mMediaRecorder.reset();
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
                //从相机采集视频
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                // 从麦克采集音频信息
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                // TODO: 2016/10/20  设置视频格式
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setVideoSize(videoWidth, videoHeight);
                //每秒的帧数
                mMediaRecorder.setVideoFrameRate(24);
                //编码格式
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

                // TODO: 2016/10/20 临时写个文件地址, 稍候该!!!
                File targetDir = Environment.
                        getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                mTargetFile = new File(targetDir,
                        SystemClock.currentThreadTimeMillis() + ".mp4");
                mMediaRecorder.setOutputFile(mTargetFile.getAbsolutePath());

                mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                mMediaRecorder.prepare();
                //正式录制
                mMediaRecorder.start();
                isRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 停止录制
     */
    private void stopRecordSave() {
        if (isRecording) {
            mMediaRecorder.stop();
            isRecording = false;
            Toast.makeText(this, "视频已经放至" + mTargetFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordUnSave() {
        if (isRecording) {
            mMediaRecorder.stop();
            isRecording = false;
            if (mTargetFile.exists()) {
                //不保存直接删掉
                mTargetFile.delete();
            }
        }
    }


    //

    private static class MyHandler extends Handler {
        private WeakReference<MainActivity> mReference;
        private MainActivity mActivity;

        public MyHandler(MainActivity activity) {
            mReference = new WeakReference<MainActivity>(activity);
            mActivity = mReference.get();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    mActivity.mProgressBar.setProgress(mActivity.mProgress);
                    break;
            }

        }
    }


}
