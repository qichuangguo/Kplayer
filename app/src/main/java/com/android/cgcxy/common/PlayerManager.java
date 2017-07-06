package com.android.cgcxy.common;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;

import com.android.cgcxy.kplayer.R;
import com.android.cgcxy.widget.media.AndroidMediaController;
import com.android.cgcxy.widget.media.IRenderView;
import com.android.cgcxy.widget.media.IjkVideoView;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class PlayerManager {
    /**
     * 可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     */
    public static final String SCALETYPE_FITPARENT="fitParent";
    /**
     * 可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     */
    public static final String SCALETYPE_FILLPARENT="fillParent";
    /**
     * 将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     */
    public static final String SCALETYPE_WRAPCONTENT="wrapContent";
    /**
     * 不剪裁,非等比例拉伸画面填满整个View
     */
    public static final String SCALETYPE_FITXY="fitXY";
    /**
     * 不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     */
    public static final String SCALETYPE_16_9="16:9";
    /**
     * 不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     */
    public static final String SCALETYPE_4_3="4:3";
    private static final String TAG = "PlayerManager";

    /**
     * 状态常量
     */
    private final int STATUS_ERROR=-1;
    private final int STATUS_IDLE=0;
    private final int STATUS_LOADING=1;
    private final int STATUS_PLAYING=2;
    private final int STATUS_PAUSE=3;
    private final int STATUS_COMPLETED=4;

    private final Activity activity;
    private final IjkVideoView videoView;
    private final AudioManager audioManager;
    public GestureDetector gestureDetector;

    private boolean playerSupport;
    private boolean isLive = false;//是否为直播
    private boolean fullScreenOnly;
    private boolean portrait;

    private final int mMaxVolume;
    private int screenWidthPixels;
    private int currentPosition;
    private int status=STATUS_IDLE;
    private long pauseTime;
    private String url;

    private float brightness=-1;
    private int volume=-1;
    private long newPosition = -1;
    private long defaultRetryTime=5000;

    private OrientationEventListener orientationEventListener;
    private PlayerStateListener playerStateListener;
    private final ImageView imageView;
    private final TextView textView;
    private final View slide_hint;
    private final ImageView iv_play;

    public void setPlayerStateListener(PlayerStateListener playerStateListener) {
        this.playerStateListener = playerStateListener;
    }

    private OnErrorListener onErrorListener=new OnErrorListener() {
        @Override
        public void onError(int what, int extra) {
        }
    };

    private OnCompleteListener onCompleteListener=new OnCompleteListener() {
        @Override
        public void onComplete() {
        }
    };

    private OnInfoListener onInfoListener=new OnInfoListener(){
        @Override
        public void onInfo(int what, int extra) {

        }
    };
    private OnControlPanelVisibilityChangeListener onControlPanelVisibilityChangeListener=new OnControlPanelVisibilityChangeListener() {
        @Override
        public void change(boolean isShowing) {
        }
    };

    /**
     * try to play when error(only for live video)
     * @param defaultRetryTime millisecond,0 will stop retry,default is 5000 millisecond
     */
    public void setDefaultRetryTime(long defaultRetryTime) {
        this.defaultRetryTime = defaultRetryTime;
    }

    public PlayerManager(final Activity activity) {
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
            playerSupport=true;
        } catch (Throwable e) {
            Log.e("GiraffePlayer", "loadLibraries error", e);
        }
        this.activity=activity;
        screenWidthPixels = activity.getResources().getDisplayMetrics().widthPixels;

        videoView = (IjkVideoView) activity.findViewById(R.id.video_view);

        ClickListener clickListener = new ClickListener();

        slide_hint = activity.findViewById(R.id.slide_hint);
        imageView = (ImageView) activity.findViewById(R.id.iv_ico);
        textView = (TextView) activity.findViewById(R.id.tv_name);
        slide_hint.setVisibility(View.GONE);
        iv_play = (ImageView) activity.findViewById(R.id.iv_play);
        iv_play.setOnClickListener(clickListener);

        videoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                statusChange(STATUS_COMPLETED);
                onCompleteListener.onComplete();
            }
        });
        videoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                statusChange(STATUS_ERROR);
                onErrorListener.onError(what,extra);
                return true;
            }
        });
        videoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                Log.i(TAG, "MEDIA_INFO_BUFFERING_END: "+extra);
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        statusChange(STATUS_LOADING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:

                        statusChange(STATUS_PLAYING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                        //显示下载速度
                        Log.i(TAG, "onInfo: "+extra);
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        statusChange(STATUS_PLAYING);
                        break;
                }
                onInfoListener.onInfo(what,extra);
                return false;
            }
        });

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        gestureDetector = new GestureDetector(activity, new PlayerGestureListener());

        if (fullScreenOnly) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        portrait=getScreenOrientation()== ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        if (!playerSupport) {
            DebugLog.e("播放器不支持此设备");
        }
    }

    public class ClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id==R.id.iv_play){
                if (videoView.getCurrentState()==videoView.STATE_PAUSED){
                    videoView.start();
                    iv_play.setImageResource(R.drawable.ic_play_circle_filled_white_white_48dp);

                }else if(videoView.getCurrentState()==videoView.STATE_PLAYING){
                    iv_play.setImageResource(R.drawable.ic_pause_circle_filled_white_48dp);
                    videoView.pause();
                }
            }
        }
    }

    private void statusChange(int newStatus) {
        status = newStatus;
        if (!isLive && newStatus==STATUS_COMPLETED) {
            DebugLog.d("statusChange STATUS_COMPLETED...");
            iv_play.setImageResource(R.drawable.ic_pause_circle_filled_white_48dp);
            if (playerStateListener != null){
                playerStateListener.onComplete();
            }
        }else if (newStatus == STATUS_ERROR) {
            DebugLog.d("statusChange STATUS_ERROR...");
            if (playerStateListener != null){
                playerStateListener.onError();
            }
        } else if(newStatus==STATUS_LOADING){
//            $.id(R.id.app_video_loading).visible();
            if (playerStateListener != null){
                playerStateListener.onLoading();
            }
            DebugLog.d("statusChange STATUS_LOADING...");
        } else if (newStatus == STATUS_PLAYING) {
            DebugLog.d("statusChange STATUS_PLAYING...");
            if (playerStateListener != null){
                playerStateListener.onPlay();
            }
        }
    }

    public void onPause() {
        pauseTime= System.currentTimeMillis();
        if (status==STATUS_PLAYING) {
            videoView.pause();
            if (!isLive) {
                currentPosition = videoView.getCurrentPosition();
            }
        }
    }

    public void onResume() {
        pauseTime=0;
        if (status==STATUS_PLAYING) {
            if (isLive) {
                videoView.seekTo(0);
            } else {
                if (currentPosition>0) {
                    videoView.seekTo(currentPosition);
                }
            }
            videoView.start();
        }
    }

    public void onDestroy() {
        orientationEventListener.disable();
        videoView.stopPlayback();
    }

    public void play(String url) {
        this.url = url;
        if (playerSupport) {
            videoView.setVideoPath(url);
            videoView.start();
        }
    }

    public void setPath(String path){

        if (playerSupport){
            videoView.setVideoPath(path);
        }
    }



    private String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    private int getScreenOrientation() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }
        return orientation;
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {

        if (volume == -1) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volume < 0)
                volume = 0;
        }
        int index = (int) (percent * mMaxVolume*1/10) + volume;
        if (index > mMaxVolume) {
            index = mMaxVolume;
        } else if (index < 0){
            index = 0;
        }

        volume=index;
        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        // 变更进度条
        int i = (int) (index * 1.0 / mMaxVolume * 100);
        String s = i + "%";
        setLuminanceOrVolume(View.VISIBLE,R.drawable.ic_volume_up_white_18dp,s+"");
        if (i == 0) {
            s = "off";
        }
        DebugLog.d("onVolumeSlide:"+s);
    }

    private void onProgressSlide(float percent) {
        long position = videoView.getCurrentPosition();
        long duration = videoView.getDuration();
        long deltaMax = Math.min(100 * 1000, duration - position);
        long delta = (long) (deltaMax * percent);

        newPosition = delta + position;
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition=0;
            delta=-position;
        }
        int showDelta = (int) delta / 1000;
        if (showDelta != 0) {
            String text = showDelta > 0 ? ("+" + showDelta) : "" + showDelta;
            DebugLog.d("onProgressSlide:" + text);
        }
    }

    /**
     * 滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {

        if (brightness < 0) {
            brightness = activity.getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f){
                brightness = 0.50f;
            }else if (brightness < 0.01f){
                brightness = 0.01f;
            }
        }
        DebugLog.d("brightness:"+brightness+",percent:"+ percent);
        WindowManager.LayoutParams lpa = activity.getWindow().getAttributes();
        lpa.screenBrightness = (brightness + percent*1/10);
        if (lpa.screenBrightness > 1.0f){
            lpa.screenBrightness = 1.0f;
        }else if (lpa.screenBrightness < 0.01f){
            lpa.screenBrightness = 0.00f;
        }
        brightness= lpa.screenBrightness;
        setLuminanceOrVolume(View.VISIBLE,R.drawable.ic_wb_sunny_white_24dp,(int)(lpa.screenBrightness*100)+"%");
        Log.i(TAG, "onBrightnessSlide: "+(int)(lpa.screenBrightness*100));
        activity.getWindow().setAttributes(lpa);
    }

    public void setFullScreenOnly(boolean fullScreenOnly) {
        this.fullScreenOnly = fullScreenOnly;
        tryFullScreen(fullScreenOnly);
        if (fullScreenOnly) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    private void tryFullScreen(boolean fullScreen) {
        if (activity instanceof AppCompatActivity) {
            ActionBar supportActionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (supportActionBar != null) {
                if (fullScreen) {
                    supportActionBar.hide();
                } else {
                    supportActionBar.show();
                }
            }
        }
        setFullScreen(fullScreen);
    }

    private void setFullScreen(boolean fullScreen) {
        if (activity != null) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            if (fullScreen) {
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                activity.getWindow().setAttributes(attrs);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                activity.getWindow().setAttributes(attrs);
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }
    }

    /**
     * <pre>
     *     fitParent:可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     *     fillParent:可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     *     wrapContent:将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     *     fitXY:不剪裁,非等比例拉伸画面填满整个View
     *     16:9:不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     *     4:3:不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     * </pre>
     * @param scaleType
     */
    public void setScaleType(String scaleType) {
        if (SCALETYPE_FITPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FIT_PARENT);
        }else if (SCALETYPE_FILLPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FILL_PARENT);
        }else if (SCALETYPE_WRAPCONTENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_WRAP_CONTENT);
        }else if (SCALETYPE_FITXY.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_MATCH_PARENT);
        }else if (SCALETYPE_16_9.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_16_9_FIT_PARENT);
        }else if (SCALETYPE_4_3.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_4_3_FIT_PARENT);
        }
    }

    public void start() {
        videoView.start();
    }

    public void pause() {
        videoView.pause();
    }

    public boolean onBackPressed() {
        if (!fullScreenOnly && getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return true;
        }
        return false;
    }

    class Query {
        private final Activity activity;
        private View view;

        public Query(Activity activity) {
            this.activity=activity;
        }

        public Query id(int id) {
            view = activity.findViewById(id);
            return this;
        }

        public Query image(int resId) {
            if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(resId);
            }
            return this;
        }

        public Query visible() {
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
            return this;
        }

        public Query gone() {
            if (view != null) {
                view.setVisibility(View.GONE);
            }
            return this;
        }

        public Query invisible() {
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
            return this;
        }

        public Query clicked(View.OnClickListener handler) {
            if (view != null) {
                view.setOnClickListener(handler);
            }
            return this;
        }

        public Query text(CharSequence text) {
            if (view!=null && view instanceof TextView) {
                ((TextView) view).setText(text);
            }
            return this;
        }

        public Query visibility(int visible) {
            if (view != null) {
                view.setVisibility(visible);
            }
            return this;
        }

        private void size(boolean width, int n, boolean dip){
            if(view != null){
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if(n > 0 && dip){
                    n = dip2pixel(activity, n);
                }
                if(width){
                    lp.width = n;
                }else{
                    lp.height = n;
                }
                view.setLayoutParams(lp);
            }
        }

        public void height(int height, boolean dip) {
            size(false,height,dip);
        }

        public int dip2pixel(Context context, float n){
            int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, context.getResources().getDisplayMetrics());
            return value;
        }

        public float pixel2dip(Context context, float n){
            Resources resources = context.getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float dp = n / (metrics.densityDpi / 160f);
            return dp;
        }
    }

    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean firstTouch;
        private boolean volumeControl;
        private boolean toSeek;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.i("qichuangguo", "onDoubleTap: ");
            videoView.toggleAspectRatio();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            Log.i("qichuangguo", "onDown: ");
            firstTouch = true;
            return super.onDown(e);
        }


        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            float mOldX = e1.getX(), mOldY = e1.getY();
            float deltaY = mOldY - e2.getY();
            float deltaX = mOldX - e2.getX();
            Log.i("chuangguo.qi", "onScroll: "+deltaX);
            if (firstTouch) {
                toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
                volumeControl=mOldX > screenWidthPixels * 0.5f;
                firstTouch = false;
            }
            Log.i("chuangguo.qi", "onScroll: ");
            if (toSeek) {
                if (!isLive) {
                    onProgressSlide(-deltaX / videoView.getWidth());
                }
            } else {
                float percent = deltaY / videoView.getHeight();
                if (volumeControl) {
                    onVolumeSlide(percent);
                } else {
                    onBrightnessSlide(percent);
                }
            }

            return super.onScroll(e1, e2, distanceX, distanceY);
        }


        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            System.out.println("==onSingleTapUp===");
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.i(TAG, "onSingleTapConfirmed: ");
            return super.onSingleTapConfirmed(e);
        }
    }

    /**
     * is player support this device
     * @return
     */
    public boolean isPlayerSupport() {
        return playerSupport;
    }

    /**
     * 是否正在播放
     * @return
     */
    public boolean isPlaying() {
        return videoView!=null?videoView.isPlaying():false;
    }

    public void stop(){
        videoView.stopPlayback();
    }

    public int getCurrentPosition(){
        return videoView.getCurrentPosition();
    }

    /**
     * get video duration
     * @return
     */
    public int getDuration(){
        return videoView.getDuration();
    }

    public PlayerManager playInFullScreen(boolean fullScreen){
        if (fullScreen) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        return this;
    }

    public PlayerManager onError(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    public PlayerManager onComplete(OnCompleteListener onCompleteListener) {
        this.onCompleteListener = onCompleteListener;
        return this;
    }

    public PlayerManager onInfo(OnInfoListener onInfoListener) {
        this.onInfoListener = onInfoListener;
        return this;
    }

    public PlayerManager onControlPanelVisibilityChange(OnControlPanelVisibilityChangeListener listener){
        this.onControlPanelVisibilityChangeListener = listener;
        return this;
    }

    public void setLuminanceOrVolume(int i,int imgId,String tv_name){
        slide_hint.setVisibility(i);
        if (imgId!=0) {
            imageView.setImageResource(imgId);
            textView.setText(tv_name);
        }
    }


    /**
     * set is live (can't seek forward)
     * @param isLive
     * @return
     */
    public PlayerManager live(boolean isLive) {
        this.isLive = isLive;
        return this;
    }

    public PlayerManager toggleAspectRatio(){
        if (videoView != null) {
            videoView.toggleAspectRatio();
        }
        return this;
    }

    public interface PlayerStateListener{
        void onComplete();
        void onError();
        void onLoading();
        void onPlay();
    }

    public interface OnErrorListener{
        void onError(int what, int extra);
    }

    public interface OnCompleteListener{
        void onComplete();
    }

    public interface OnControlPanelVisibilityChangeListener{
        void change(boolean isShowing);
    }

    public interface OnInfoListener{
        void onInfo(int what, int extra);
    }
}