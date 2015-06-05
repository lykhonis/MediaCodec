package com.vladli.android.mediacodec;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;

/**
 * Created by vladlichonos on 6/5/15.
 */
public class RenderActivity extends Activity implements SurfaceHolder.Callback {

    // video output dimension
    static final int OUTPUT_WIDTH = 640;
    static final int OUTPUT_HEIGHT = 480;

    VideoEncoder mEncoder;
    VideoDecoder mDecoder;
    SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mSurfaceView.getHolder().addCallback(this);

        mEncoder = new MyEncoder();
        mDecoder = new VideoDecoder();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // surface is fully initialized on the activity
        mDecoder.start();
        mEncoder.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mEncoder.stop();
        mDecoder.stop();
    }

    class MyEncoder extends VideoEncoder {

        SurfaceRenderer mRenderer;
        byte[] mBuffer = new byte[0];

        public MyEncoder() {
            super(OUTPUT_WIDTH, OUTPUT_HEIGHT);
        }

        // Both of onSurfaceCreated and onSurfaceDestroyed are called from codec's thread,
        // non-UI thread

        @Override
        protected void onSurfaceCreated(Surface surface) {
            // surface is created and codec is ready to accept input (Canvas)
            mRenderer = new MyRenderer(surface);
            mRenderer.start();
        }

        @Override
        protected void onSurfaceDestroyed(Surface surface) {
            // need to make sure to block this thread to fully complete drawing cycle
            // otherwise unpredictable exceptions will be thrown (aka IllegalStateException)
            mRenderer.stopAndWait();
            mRenderer = null;
        }

        @Override
        protected void onEncodedSample(MediaCodec.BufferInfo info, ByteBuffer data) {
            // Here we could have just used ByteBuffer, but in real life case we might need to
            // send sample over network, etc. This requires byte[]
            if (mBuffer.length < info.size) {
                mBuffer = new byte[info.size];
            }
            data.position(info.offset);
            data.limit(info.offset + info.size);
            data.get(mBuffer, 0, info.size);

            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                // this is the first and only config sample, which contains information about codec
                // like H.264, that let's configure the decoder
                mDecoder.configure(mSurfaceView.getHolder().getSurface(),
                                   OUTPUT_WIDTH,
                                   OUTPUT_HEIGHT,
                                   mBuffer,
                                   0,
                                   info.size);
            } else {
                // pass byte[] to decoder's queue to render asap
                mDecoder.decodeSample(mBuffer,
                                      0,
                                      info.size,
                                      info.presentationTimeUs,
                                      info.flags);
            }
        }
    }

    // All drawing is happening here
    // We draw on virtual surface size of 640x480
    // it will be automatically encoded into H.264 stream
    class MyRenderer extends SurfaceRenderer {

        TextPaint mPaint;
        long mTimeStart;

        public MyRenderer(Surface surface) {
            super(surface);
        }

        @Override
        public void start() {
            super.start();
            mTimeStart = System.currentTimeMillis();
        }

        String formatTime() {
            int now = (int) (System.currentTimeMillis() - mTimeStart);
            int minutes = now / 1000 / 60;
            int seconds = now / 1000 % 60;
            int millis = now % 1000;
            return String.format("%02d:%02d:%03d", minutes, seconds, millis);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // non-UI thread
            canvas.drawColor(Color.BLACK);

            // setting some text paint
            if (mPaint == null) {
                mPaint = new TextPaint();
                mPaint.setAntiAlias(true);
                mPaint.setColor(Color.WHITE);
                mPaint.setTextSize(30f * getResources().getConfiguration().fontScale);
                mPaint.setTextAlign(Paint.Align.CENTER);
            }

            canvas.drawText(formatTime(),
                            OUTPUT_WIDTH / 2,
                            OUTPUT_HEIGHT / 2,
                            mPaint);
        }
    }
}
