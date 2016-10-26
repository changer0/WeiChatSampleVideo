**目录**

- [开发之前](#1)
	- [开发环境](#1.1)
	- [相关知识点](#1.2)
- [开始开发](#2)
	- [案例预览](#2.1)
	- [案例分析](#2.2)
	- [搭建布局](#2.3)
	- [视频预览的实现](#2.4)
	- [自定义双向缩减的进度条](#2.5)
	- [录制事件的处理](#2.6)
		- [长按录制](#2.6.1)
		- [抬起保存](#2.6.2)
		- [上滑取消](#2.6.3)
		- [双击放大(变焦)](#2.6.4)
	- [实现视频的录制](#2.7)
	- [实现视频的停止](#2.8)
- [完整代码](#3)
- [总结](#4)


<h3 id="1">开发之前</h3>

> 这几天接触了一下和视频相关的控件, 所以, 继之前的[微信摇一摇](http://www.jianshu.com/p/bc5298651b30), 我想到了来实现一下微信小视频录制的功能, 它的功能点比较多, 我每天都抽出点时间来写写, 说实话, 有些东西还是比较费劲, 希望大家认真看看, 说得不对的地方还请大家在评论中指正. 废话不多说, 进入正题.

<h4 id="1.1">开发环境</h4>

> 最近刚更新的, 没更新的小伙伴们抓紧了

- Android Studio 2.2.2
- JDK1.7
- API 24
- Gradle 2.2.2

<h4 id="1.2">相关知识点</h4>

- 视频录制界面 SurfaceView 的使用

- Camera的使用

- 相机的对焦, 变焦

- 视频录制控件MediaRecorder的使用

- 简单自定义View

- GestureDetector(手势检测)的使用

> 用到的东西真不少, 不过别着急, 咱们一个一个来.


<h3 id="2">开始开发</h3>

<h4 id="2.1">案例预览</h4>

> 请原谅Gif图的粗糙

![微信小视频](http://upload-images.jianshu.io/upload_images/3118842-ecf6a66008f70486.gif?imageMogr2/auto-orient/strip)


<h4 id="2.2">案例分析</h4>

> 大家可以打开自己微信里面的小视频, 一块简单的分析一下它的功能点有哪些 ?

- 基本的视频预览功能

- 长按 "按住拍" 实现视频的录制

- 录制过程中的进度条从两侧向中间变短

- 当松手或者进度条走到尽头视频停止录制 并保存

- 从 "按住拍" 上滑取消视频的录制

- 双击屏幕 变焦 放大

> 根据上述的分析, 我们一步一步的完成


<h4 id="2.3">搭建布局</h4>

> 布局界面的实现还可以, 难度不大

``` xml

<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:id="@+id/main_tv_tip"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="150dp"
        android:elevation="1dp"
        android:text="双击放大"
        android:textColor="#FFFFFF"/>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">
        <SurfaceView
            android:id="@+id/main_surface_view"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="3"/>
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:background="@color/colorApp"
            android:orientation="vertical">
            <RelativeLayout
                android:id="@+id/main_press_control"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.lulu.weichatsamplevideo.BothWayProgressBar
                    android:id="@+id/main_progress_bar"
                    android:layout_width="match_parent"
                    android:layout_height="2dp"
                    android:background="#000"/>
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:text="按住拍"
                    android:textAppearance="@style/TextAppearance.AppCompat.Large"
                    android:textColor="#00ff00"/>
            </RelativeLayout>
        </LinearLayout>
    </LinearLayout>
</FrameLayout>

```


<h4 id="2.4">视频预览的实现</h4>

> step1:  得到SufaceView控件, 设置基本属性和相应监听(该控件的创建是异步的, 只有在真正"准备"好之后才能调用)

``` java

mSurfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
 //设置屏幕分辨率
mSurfaceHolder.setFixedSize(videoWidth, videoHeight);
mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
mSurfaceHolder.addCallback(this);

```

> step2: 实现接口的方法, surfaceCreated方法中开启视频的预览, 在surfaceDestroyed中销毁


``` java

//////////////////////////////////////////////
// SurfaceView回调
/////////////////////////////////////////////
@Override
public void surfaceCreated(SurfaceHolder holder) {
    mSurfaceHolder = holder;
    startPreView(holder);
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
        mCamera = null;
    }
    if (mMediaRecorder != null) {
        mMediaRecorder.release();
        mMediaRecorder = null;
    }
}

```

> step3: 实现视频预览的方法

``` java

/**
 * 开启预览
 *
 * @param holder
 */
private void startPreView(SurfaceHolder holder) {
    Log.d(TAG, "startPreView: ");

    if (mCamera == null) {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
    }
    if (mMediaRecorder == null) {
        mMediaRecorder = new MediaRecorder();
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

```
> Note: 上面添加了自动对焦的代码, 但是部分手机可能不支持

<h4 id="2.5">自定义双向缩减的进度条</h4>

> 有些像我一样的初学者一看到自定义某某View, 就觉得比较牛X. 其实呢, Google已经替我们写好了很多代码, 所以我们用就行了.而且咱们的这个进度条也没啥, 不就是一根线, 今天咱就来说说.

> step1: 继承View, 完成初始化

``` java

private static final String TAG = "BothWayProgressBar";
//取消状态为红色bar, 反之为绿色bar
private boolean isCancel = false;
private Context mContext;
//正在录制的画笔
private Paint mRecordPaint;
//上滑取消时的画笔
private Paint mCancelPaint;
//是否显示
private int mVisibility;
// 当前进度
private int progress;
//进度条结束的监听
private OnProgressEndListener mOnProgressEndListener;

public BothWayProgressBar(Context context) {
     super(context, null);
}
public BothWayProgressBar(Context context, AttributeSet attrs) {
   super(context, attrs);
   mContext = context;
   init();
}
private void init() {
   mVisibility = INVISIBLE;
   mRecordPaint = new Paint();
   mRecordPaint.setColor(Color.GREEN);
   mCancelPaint = new Paint();
   mCancelPaint.setColor(Color.RED);
}

```

> Note: OnProgressEndListener, 主要用于当进度条走到中间了, 好通知相机停止录制, 接口如下:


``` java

public interface OnProgressEndListener{
    void onProgressEndListener();
}
/**
 * 当进度条结束后的 监听
 * @param onProgressEndListener
 */
public void setOnProgressEndListener(OnProgressEndListener onProgressEndListener) {
    mOnProgressEndListener = onProgressEndListener;
}

```

> step2 :设置Setter方法用于通知我们的Progress改变状态

``` java


/**
 * 设置进度
 * @param progress
 */
public void setProgress(int progress) {
    this.progress = progress;
    invalidate();
}

/**
 * 设置录制状态 是否为取消状态
 * @param isCancel
 */
public void setCancel(boolean isCancel) {
    this.isCancel = isCancel;
    invalidate();
}
/**
 * 重写是否可见方法
 * @param visibility
 */
@Override
public void setVisibility(int visibility) {
    mVisibility = visibility;
    //重新绘制
    invalidate();
}

```
> step3 :最重要的一步, 画出我们的进度条,使用的就是View中的onDraw(Canvas canvas)方法

``` java
@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mVisibility == View.VISIBLE) {
        int height = getHeight();
        int width = getWidth();
        int mid = width / 2;


        //画出进度条
        if (progress < mid){
            canvas.drawRect(progress, 0, width-progress, height, isCancel ? mCancelPaint : mRecordPaint);
        } else {
            if (mOnProgressEndListener != null) {
                mOnProgressEndListener.onProgressEndListener();
            }
        }
    } else {
        canvas.drawColor(Color.argb(0, 0, 0, 0));
    }
}

```

<h4 id="2.6">录制事件的处理</h4>

> 录制中触发的事件包括四个:

1. 长按录制
2. 抬起保存
3. 上滑取消
4. 双击放大(变焦)
> 现在对这4个事件逐个分析:
> 前三这个事件, 我都放在了一个onTouch()回调方法中了
> 对于第4个, 我们待会谈
> 我们先把onTouch()中局部变量列举一下:

``` java

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
   // ...
}
```

<h5 id="2.6.1">长按录制</h5>

> 长按录制我们需要监听ACTION_DOWN事件, 使用线程延迟发送Handler来实现进度条的更新

``` java
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
                      isRunning = true;
                      while (isRunning) {
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
      // ...
      return true;
}

```
> Note: startRecord()这个方法先不说, 我们只需要知道执行了它就可以录制了, 但是Handler事件还是要说的, 它只负责更新进度条的进度

``` java


////////////////////////////////////////////////////
// Handler处理
/////////////////////////////////////////////////////
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
```


<h5 id="2.6.2">抬起保存</h5>

> 同样我们这儿需要监听ACTION_UP事件, 但是要考虑当用户抬起过快时(录制的时间过短), 不需要保存. 而且, 在这个事件中包含了取消状态的抬起, 解释一下: 就是当上滑取消时抬起的一瞬间取消录制, 大家看代码

``` java
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
```

> Note: 同样的, 内部的stopRecordUnSave()和stopRecordSave();大家先不要考虑, 我们会在后面介绍, 他俩从名字就能看出 前者用来停止录制但不保存, 后者停止录制并保存

<h5 id="2.6.3">上滑取消</h5>

> 配合上一部分说得抬起取消事件, 实现上滑取消

``` java

case MotionEvent.ACTION_MOVE:
  if (ex > left && ex < right) {
      float currentY = event.getY();
      if (downY - currentY > 10) {
          isCancel = true;
          mProgressBar.setCancel(true);
      }
  }
  break;

```

> Note: 主要原理不难, 只要按下并且向上移动一定距离 就会触发,当手抬起时视频录制取消

<h5 id="2.6.4">双击放大(变焦)</h5>

> 这个事件比较特殊, 使用了Google提供的GestureDetector手势检测 来判断双击事件
> 
> step1: 对SurfaceView进行单独的Touch事件监听, why? 因为GestureDetector需要Touch事件的完全托管, 如果只给它传部分事件会造成某些事件失效

``` java
mDetector = new GestureDetector(this, new ZoomGestureListener());
/**
 * 单独处理mSurfaceView的双击事件
 */
mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mDetector.onTouchEvent(event);
        return true;
    }
});

```

> step2: 重写GestureDetector.SimpleOnGestureListener, 实现双击事件

``` java

///////////////////////////////////////////////////////////////////////////
// 变焦手势处理类
///////////////////////////////////////////////////////////////////////////
class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener {
    //双击手势事件
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        super.onDoubleTap(e);
        Log.d(TAG, "onDoubleTap: 双击事件");
        if (mMediaRecorder != null) {
            if (!isZoomIn) {
                setZoom(20);
                isZoomIn = true;
            } else {
                setZoom(0);
                isZoomIn = false;
            }
        }
        return true;
    }
}

```
> step3: 实现相机的变焦的方法

``` java

/**
 * 相机变焦
 *
 * @param zoomValue
 */
public void setZoom(int zoomValue) {
    if (mCamera != null) {
        Camera.Parameters parameters = mCamera.getParameters();
        if (parameters.isZoomSupported()) {//判断是否支持
            int maxZoom = parameters.getMaxZoom();
            if (maxZoom == 0) {
                return;
            }
            if (zoomValue > maxZoom) {
                zoomValue = maxZoom;
            }
            parameters.setZoom(zoomValue);
            mCamera.setParameters(parameters);
        }
    }

}

```

Note: 至此我们已经完成了对所有事件的监听, 看到这里大家也许有些疲惫了, 不过不要灰心, 现在完成我们的核心部分, 实现视频的录制

<h4 id="2.7">实现视频的录制</h4>

> 说是核心功能, 也只不过是我们不知道某些API方法罢了, 下面代码中我已经加了详细的注释, 部分不能理解的记住就好^v^

``` java
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
            // 设置帧频率，然后就清晰了
            mMediaRecorder.setVideoEncodingBitRate(1 * 1024 * 1024 * 100);
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


```

<h4 id="2.8">实现视频的停止</h4>

> 大家可能会问, 视频的停止为什么单独抽出来说呢? 仔细的同学看上面代码会看到这两个方法: stopRecordSave和stopRecordUnSave, 一个停止保存, 一个是停止不保存, 接下来我们就补上这个坑

> 停止并保存

``` java

private void stopRecordSave() {
    if (isRecording) {
        isRunning = false;
        mMediaRecorder.stop();
        isRecording = false;
        Toast.makeText(this, "视频已经放至" + mTargetFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }
}
```

> 停止不保存

``` java

private void stopRecordUnSave() {
    if (isRecording) {
        isRunning = false;
        mMediaRecorder.stop();
        isRecording = false;
        if (mTargetFile.exists()) {
            //不保存直接删掉
            mTargetFile.delete();
        }
    }
}

```

> Note: 这个停止不保存是我自己的一种想法, 如果大家有更好的想法, 欢迎大家到评论中指出, 不胜感激

<h3 id="3">完整代码</h3>

源码我已经放在了[github](https://github.com/changer0/WeiChatSampleVideo)上了, 写博客真是不易! 写篇像样的博客更是不易, 希望大家多多支持

<h3 id="4">总结</h3>

> 终于写完了!!! 这是我最想说得话, 从案例一开始到现在已经过去很长时间. 这是我写得最长的一篇博客, 发现能表达清楚自己的想法还是很困难的, 这是我最大的感受!!!
> 实话说这个案例不是很困难, 但是像我这样的初学者拿来练练手还是非常好的, 在这里还要感谢[再见杰克](http://blog.csdn.net/u012227600/article/details/50835633)的博客, 也给我提供了很多帮助
> 最后, 希望大家共同进步!