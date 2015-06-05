package com.vladli.android.mediacodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by vladlichonos on 6/5/15.
 */
public class VideoEncoder implements VideoCodec {

    Worker mWorker;
    int mWidth, mHeight;

    public VideoEncoder(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    protected void onSurfaceCreated(Surface surface) {
    }

    protected void onSurfaceDestroyed(Surface surface) {
    }

    protected void onEncodedSample(MediaCodec.BufferInfo info, ByteBuffer data) {
    }

    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }
    }

    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }
    }

    class Worker extends Thread {

        MediaCodec.BufferInfo mBufferInfo;
        MediaCodec mCodec;
        volatile boolean mRunning;
        Surface mSurface;
        final long mTimeoutUsec;

        public Worker() {
            mBufferInfo = new MediaCodec.BufferInfo();
            mTimeoutUsec = 10000l;
        }

        public void setRunning(boolean running) {
            mRunning = running;
        }

        @Override
        public void run() {
            prepare();
            try {
                while (mRunning) {
                    encode();
                }
                encode();
            } finally {
                release();
            }
        }

        @SuppressWarnings("deprecation")
        void encode() {
            if (!mRunning) {
                // if not running anymore, complete stream
                mCodec.signalEndOfInputStream();
            }
            // New api is nicer, see below
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ByteBuffer[] outputBuffers = mCodec.getOutputBuffers();
                for (; ; ) {
                    // MediaCodec is asynchronous, that's why we have a blocking check
                    // to see if we have something to do
                    int status = mCodec.dequeueOutputBuffer(mBufferInfo, mTimeoutUsec);
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!mRunning) break;
                    } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = mCodec.getOutputBuffers();
                    } else if (status >= 0) {
                        // encoded sample
                        ByteBuffer data = outputBuffers[status];
                        data.position(mBufferInfo.offset);
                        data.limit(mBufferInfo.offset + mBufferInfo.size);
                        final int endOfStream = mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        // pass to whoever listens to
                        if (endOfStream == 0) onEncodedSample(mBufferInfo, data);
                        // releasing buffer is important
                        mCodec.releaseOutputBuffer(status, false);
                        if (endOfStream == MediaCodec.BUFFER_FLAG_END_OF_STREAM) break;
                    }
                }
            } else {
                for (; ; ) {
                    int status = mCodec.dequeueOutputBuffer(mBufferInfo, mTimeoutUsec);
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!mRunning) break;
                    } else if (status >= 0) {
                        // encoded sample
                        ByteBuffer data = mCodec.getOutputBuffer(status);
                        if (data != null) {
                            final int endOfStream = mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            // pass to whoever listens to
                            if (endOfStream == 0) onEncodedSample(mBufferInfo, data);
                            // releasing buffer is important
                            mCodec.releaseOutputBuffer(status, false);
                            if (endOfStream == MediaCodec.BUFFER_FLAG_END_OF_STREAM) break;
                        }
                    }
                }
            }
        }

        void release() {
            // notify about destroying surface first before actually destroying it
            // otherwise unexpected exceptions can happen, since we working in multiple threads
            // simultaneously
            onSurfaceDestroyed(mSurface);

            mCodec.stop();
            mCodec.release();
            mSurface.release();
        }

        void prepare() {
            // configure video output
            MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                              MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_PER_SECOND);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL);

            try {
                mCodec = MediaCodec.createEncoderByType(VIDEO_FORMAT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            // create surface associated with code
            mSurface = mCodec.createInputSurface();
            // notify codec to start watch surface and encode samples
            mCodec.start();

            onSurfaceCreated(mSurface);
        }
    }
}
