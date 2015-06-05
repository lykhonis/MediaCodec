package com.vladli.android.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by vladlichonos on 6/5/15.
 */
public class VideoDecoder implements VideoCodec {

    Worker mWorker;

    public void decodeSample(byte[] data, int offset, int size, long presentationTimeUs, int flags) {
        if (mWorker != null) {
            mWorker.decodeSample(data, offset, size, presentationTimeUs, flags);
        }
    }

    public void configure(Surface surface, int width, int height, ByteBuffer csd0) {
        if (mWorker != null) {
            mWorker.configure(surface, width, height, csd0);
        }
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

        volatile boolean mRunning;
        MediaCodec mCodec;
        volatile boolean mConfigured;
        long mTimeoutUs;

        public Worker() {
            mTimeoutUs = 10000l;
        }

        public void setRunning(boolean running) {
            mRunning = running;
        }

        public void configure(Surface surface, int width, int height, ByteBuffer csd0) {
            if (mConfigured) {
                throw new IllegalStateException("Decoder is already configured");
            }
            MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, width, height);
            format.setByteBuffer("csd-0", csd0);
            try {
                mCodec = MediaCodec.createDecoderByType(VIDEO_FORMAT);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create codec", e);
            }
            mCodec.configure(format, surface, null, 0);
            mCodec.start();
            mConfigured = true;
        }

        @SuppressWarnings("deprecation")
        public void decodeSample(byte[] data, int offset, int size, long presentationTimeUs, int flags) {
            if (mConfigured) {
                int index = mCodec.dequeueInputBuffer(mTimeoutUs);
                if (index >= 0) {
                    ByteBuffer buffer;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        buffer = mCodec.getInputBuffers()[index];
                        buffer.clear();
                    } else {
                        buffer = mCodec.getInputBuffer(index);
                    }
                    if (buffer != null) {
                        buffer.put(data, offset, size);
                        mCodec.queueInputBuffer(index, 0, size, presentationTimeUs, flags);
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (mRunning) {
                    if (mConfigured) {
                        int index = mCodec.dequeueOutputBuffer(info, mTimeoutUs);
                        if (index >= 0) {
                            mCodec.releaseOutputBuffer(index, info.size > 0);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                                break;
                            }
                        }
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            } finally {
                if (mConfigured) {
                    mCodec.stop();
                    mCodec.release();
                }
            }
        }
    }
}
