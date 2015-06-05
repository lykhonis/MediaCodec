package com.vladli.android.mediacodec;

import android.graphics.Canvas;
import android.view.Surface;

/**
 * Created by vladlichonos on 6/5/15.
 */
public class SurfaceRenderer {

    Surface mSurface;
    Renderer mRenderer;

    public SurfaceRenderer(Surface surface) {
        mSurface = surface;
    }

    protected void onDraw(Canvas canvas) {
    }

    public void start() {
        if (mRenderer == null) {
            mRenderer = new Renderer();
            mRenderer.setRunning(true);
            mRenderer.start();
        }
    }

    public void stopAndWait() {
        if (mRenderer != null) {
            mRenderer.setRunning(false);
            // we want to make sure complete drawing cycle, otherwise
            // unlockCanvasAndPost() will be the one who may or may not throw
            // IllegalStateException
            try {
                mRenderer.join();
            } catch (InterruptedException ignore) {
            }
            mRenderer = null;
        }
    }

    class Renderer extends Thread {

        volatile boolean mRunning;

        public void setRunning(boolean running) {
            mRunning = running;
        }

        @Override
        public void run() {
            while (mRunning) {
                Canvas canvas = mSurface.lockCanvas(null);
                try {
                    onDraw(canvas);
                } finally {
                    mSurface.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
