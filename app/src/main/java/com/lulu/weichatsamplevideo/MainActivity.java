package com.lulu.weichatsamplevideo;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import static android.hardware.Camera.getCameraInfo;
import static android.hardware.Camera.getNumberOfCameras;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnTouchListener, BothWayProgressBar.OnProgressEndListener {

    private static final int LISTENER_START = 200;
    private static final String TAG = "MainActivity";
    //预览SurfaceView
    private SurfaceView mPreview;
    private Camera mCamera;

    private int mCameraId;
    private BothWayProgressBar mProgressBar;
    private View mPressControler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        initView();


    }

    private void initView() {
        mPreview = (SurfaceView) findViewById(R.id.main_surface_view);
        mPreview.getHolder().addCallback(this);
        mPressControler = findViewById (R.id.main_press_control);
        mPressControler.setOnTouchListener(this);
        //自定义双向进度条    (这个地方差点把我急疯了!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!)
        mProgressBar = (BothWayProgressBar) findViewById(R.id.main_progress_bar);
        mProgressBar.setOnProgressEndListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////
    // SurfaceView回调
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mCamera == null) {
            initCamera();
            if (mCamera != null) {
                setCameraDisplayOrientation(this, mCameraId, mCamera);
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
                    mCamera.setParameters(parameters );
                    mCamera.startPreview();

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            //停止预览并释放摄像头资源
            mCamera.stopPreview();
            mCamera.release();
        }
    }


    /**
     * 初始化相机
     */
    private void initCamera() {
        int numberOfCameras = getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCamera = Camera.open(i);
                mCameraId = i;
                break;
            }
        }
    }

    /**
     * 根据手机屏幕方向, 来检测和设置摄像头预览的方法
     *  Copy 的API文档中的代码
     * @
     */
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        //核心设置方法: 内部传入旋转角度
        camera.setDisplayOrientation(result);
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

        switch (v.getId()) {
            case R.id.main_press_control:
                switch (action){
                    case MotionEvent.ACTION_DOWN:
                        if (ex > left && ex < right) {
                            mProgressBar.setVisibility(View.VISIBLE);
                            ret = true;
                        }

                        break;
                    case MotionEvent.ACTION_UP:
                        if (ex > left && ex < right) {
                            mProgressBar.setVisibility(View.INVISIBLE);
                            ret = false;
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
        //视频直接保存
    }
}
