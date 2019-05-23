package com.tappydefender;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.ArrayList;

public class TDView extends SurfaceView implements Runnable
{
    volatile boolean playing;
    Thread gameThread = null;

    // Game objects
    private PlayerShip player;
    private EnemyShip enemy1;
    private EnemyShip enemy2;
    private EnemyShip enemy3;
    public ArrayList<SpaceDust> dustList = new ArrayList<SpaceDust>();

    private float distanceRemaining;
    private long timeTaken;
    private long timeStarted;
    private long fastestTime;
    private boolean gameEnded;

    // For drawing
    private Paint paint;
    private Canvas canvas;
    private SurfaceHolder ourHolder;

    private int screenX;
    private int screenY;

    private Context context;

    private SoundPool soundPool;
    int start = -1;
    int bump = -1;
    int destroyed = -1;
    int win = -1;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public TDView(Context context, int screenX, int screenY)
    {
        super(context);
        this.context = context;
        ourHolder = getHolder();
        paint = new Paint();

        prefs = context.getSharedPreferences("HiScores", context.MODE_PRIVATE);
        editor = prefs.edit();
        fastestTime = prefs.getLong("fastestTime", 1000000);

        this.screenX = screenX;
        this.screenY = screenY;

        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC,0);
        try
        {
            AssetManager assetManager = context.getAssets();
            AssetFileDescriptor descriptor;

            descriptor = assetManager.openFd("start.ogg");
            start = soundPool.load(descriptor, 0);
            descriptor = assetManager.openFd("win.ogg");
            win = soundPool.load(descriptor, 0);
            descriptor = assetManager.openFd("bump.ogg");
            bump = soundPool.load(descriptor, 0);
            descriptor = assetManager.openFd("destroyed.ogg");
            destroyed = soundPool.load(descriptor, 0);
        }
        catch(IOException e)
        {
            Log.e("error", "failed to load sound files");
        }

        startGame();
    }

    private void startGame()
    {
        gameEnded = false;

        soundPool.play(start, 1, 1, 0, 0, 1);

        player = new PlayerShip(context, screenX, screenY);
        enemy1 = new EnemyShip(context, screenX, screenY);
        enemy2 = new EnemyShip(context, screenX, screenY);
        enemy3 = new EnemyShip(context, screenX, screenY);

        int numSpecs = 150;
        for (int i = 0; i < numSpecs; i++)
            dustList.add(new SpaceDust(screenX, screenY));

        distanceRemaining = 10000;// 10 km
        timeTaken = 0;
        timeStarted = System.currentTimeMillis();
    }

    @Override
    public void run()
    {
        while (playing)
        {
            update();
            draw();
            control();
        }
    }

    private void update()
    {
        player.update();
        enemy1.update(player.getSpeed());
        enemy2.update(player.getSpeed());
        enemy3.update(player.getSpeed());
        for (SpaceDust sd : dustList)
        {
            sd.update(player.getSpeed());
        }

        boolean hitDetected = false;
        if(Rect.intersects(player.getHitbox(), enemy1.getHitbox()))
        {
            enemy1.setX(-300);
            hitDetected = true;
        }
        if(Rect.intersects(player.getHitbox(), enemy2.getHitbox()))
        {
            enemy2.setX(-300);
            hitDetected = true;
        }
        if(Rect.intersects(player.getHitbox(), enemy3.getHitbox()))
        {
            enemy3.setX(-300);
            hitDetected = true;
        }
        if(hitDetected)
        {
            player.reduceShieldStrength();
            soundPool.play(bump, 1, 1, 0, 0, 1);
            if (player.getShieldStrength() < 0)
            {
                soundPool.play(destroyed, 1, 1, 0, 0, 1);
                gameEnded = true;
            }
        }
        if(!gameEnded)
        {
            distanceRemaining -= player.getSpeed();
            timeTaken = System.currentTimeMillis() - timeStarted;
        }
        if(distanceRemaining < 0)
        {
            soundPool.play(win, 1, 1, 0, 0, 1);
            if (timeTaken < fastestTime)
            {
                fastestTime = timeTaken;
                editor.putLong("fastestTime", timeTaken);
                editor.commit();
            }
            distanceRemaining = 0;
            gameEnded = true;
        }

    }

    private void draw()
    {
        if (ourHolder.getSurface().isValid())
        {
            canvas = ourHolder.lockCanvas();
            canvas.drawColor(Color.argb(255, 0, 0, 0));

            paint.setColor(Color.argb(255, 255, 255, 255));
            for (SpaceDust sd : dustList)
                canvas.drawPoint(sd.getX(), sd.getY(), paint);

            paint.setColor(Color.argb(255, 255, 255, 255));
// Draw Hit boxes
            canvas.drawRect(player.getHitbox().left,
                    player.getHitbox().top,
                    player.getHitbox().right,
                    player.getHitbox().bottom,
                    paint);
            canvas.drawRect(enemy1.getHitbox().left,
                    enemy1.getHitbox().top,
                    enemy1.getHitbox().right,
                    enemy1.getHitbox().bottom,
                    paint);
            canvas.drawRect(enemy2.getHitbox().left,
                    enemy2.getHitbox().top,
                    enemy2.getHitbox().right,
                    enemy2.getHitbox().bottom,
                    paint);
            canvas.drawRect(enemy3.getHitbox().left,
                    enemy3.getHitbox().top,
                    enemy3.getHitbox().right,
                    enemy3.getHitbox().bottom,
                    paint);

            canvas.drawBitmap(player.getBitmap(), player.getX(), player.getY(), paint);
            canvas.drawBitmap(enemy1.getBitmap(), enemy1.getX(), enemy1.getY(), paint);
            canvas.drawBitmap(enemy2.getBitmap(), enemy2.getX(), enemy2.getY(), paint);
            canvas.drawBitmap(enemy3.getBitmap(), enemy3.getX(), enemy3.getY(), paint);

            if(!gameEnded)
            {
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(Color.argb(255, 255, 255, 255));
                paint.setTextSize(40);
                canvas.drawText("Fastest:" + fastestTime + "s", 10, 40, paint);
                canvas.drawText("Time:" + timeTaken + "s", screenX / 2, 40,
                        paint);
                canvas.drawText("Distance:" +
                        distanceRemaining / 1000 +
                        " KM", screenX / 3, screenY - 20, paint);
                canvas.drawText("Shield:" +
                        player.getShieldStrength(), 10, screenY - 20, paint);
                canvas.drawText("Speed:" +
                        player.getSpeed() * 60 +
                        " MPS", (screenX / 3) * 2, screenY - 20, paint);
            }
            else
            {
                paint.setTextSize(100);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Game Over", screenX/2, 100, paint);
                paint.setTextSize(40);
                canvas.drawText("Fastest:"+
                        fastestTime + "s", screenX/2, 160, paint);
                canvas.drawText("Time:" + timeTaken +
                        "s", screenX / 2, 200, paint);
                canvas.drawText("Distance remaining:" +
                        distanceRemaining/1000 + " KM",screenX/2, 240, paint);
                paint.setTextSize(100);
                canvas.drawText("Tap to replay!", screenX/2, 350, paint);
            }

            ourHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void control()
    {
        try
        {
            gameThread.sleep(17);
        } catch (InterruptedException e) {}
    }

    public void pause()
    {
        playing = false;
        try
        {
            gameThread.join();
        } catch (InterruptedException e) {}
    }

    public void resume()
    {
        playing = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK)
        {
            case MotionEvent.ACTION_UP:
                player.stopBoosting();
                break;
            case MotionEvent.ACTION_DOWN:
                player.setBoosting();
                if(gameEnded)
                    startGame();
                break;
        }
        return true;
    }
}
