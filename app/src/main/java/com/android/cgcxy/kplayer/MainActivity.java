package com.android.cgcxy.kplayer;

import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.android.cgcxy.common.PlayerManager;
import com.android.cgcxy.widget.media.IjkVideoView;

import java.io.File;

import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;

public class MainActivity extends AppCompatActivity implements PlayerManager.PlayerStateListener{

    private PlayerManager player;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
       String path = "http://183.207.255.190:80/live/program/live/hnws/1300000/mnf.m3u8";
        //String path2 = Environment.getExternalStorageDirectory().getPath()+ File.separator+"aaa/111.mp4";
        /*IjkVideoView videoView= (IjkVideoView) findViewById(R.id.video_view);
        videoView.setVideoURI(Uri.parse(path));
        videoView.start();*/

        player = new PlayerManager(this);
        player.setFullScreenOnly(true);
        player.setScaleType(PlayerManager.SCALETYPE_FITXY);
        player.playInFullScreen(true);
        player.setPlayerStateListener(this);
        player.play(path);
       // System.out.println("==path2===="+path2+"-------"+new File(path2).exists());
       // player.setPath(path);
//        player.start();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        player.gestureDetector.onTouchEvent(event);
        if (event.getAction()==MotionEvent.ACTION_UP){
            player.setLuminanceOrVolume(View.GONE,0,null);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void onError() {

    }

    @Override
    public void onLoading() {

    }

    @Override
    public void onPlay() {

    }
}
