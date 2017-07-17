package com.rva.mrb.vivify.Spotify;

import android.annotation.TargetApi;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import com.spotify.sdk.android.player.AudioController;
import com.spotify.sdk.android.player.AudioRingBuffer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by Bao on 7/16/17.
 */
public class AudioTrackController implements AudioController{
        private static final int DEFAULT_CHANNEL_COUNT = 2;
        private static final int AUDIO_BUFFER_SIZE_FRAMES = 2048;
        private static final int BUFFER_LATENCY_FACTOR = 2;
        private static final int AUDIO_BUFFER_SIZE_SAMPLES = 4096;
        private static final int AUDIO_BUFFER_CAPACITY = 81920;
        private final AudioRingBuffer mAudioBuffer;
        private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
        private final Object mPlayingMutex = new Object();
        private AudioTrack mAudioTrack;
        private int mSampleRate;
        private int mChannels;
        private final Runnable mAudioRunnable = new Runnable()
        {
            final short[] pendingSamples = new short[AUDIO_BUFFER_CAPACITY];

            public void run()
            {
                int itemsRead = AudioTrackController.this.mAudioBuffer.peek(this.pendingSamples);
                if (itemsRead > 0)
                {
                    int itemsWritten = AudioTrackController.this.writeSamplesToAudioOutput(this.pendingSamples, itemsRead);
                    AudioTrackController.this.mAudioBuffer.remove(itemsWritten);
                }
            }
        };

        public AudioTrackController()
        {
            this.mAudioBuffer = new AudioRingBuffer(81920);
        }

    public int onAudioDataDelivered(short[] samples, int sampleCount, int sampleRate, int channels)
    {
        if ((this.mAudioTrack != null) && ((this.mSampleRate != sampleRate) || (this.mChannels != channels))) {
            synchronized (this.mPlayingMutex)
            {
                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }
        }
        this.mSampleRate = sampleRate;
        this.mChannels = channels;
        if (this.mAudioTrack == null) {
            createAudioTrack(sampleRate, channels);
        }
        try
        {
            this.mExecutorService.execute(this.mAudioRunnable);
        }
        catch (RejectedExecutionException localRejectedExecutionException) {}
        return this.mAudioBuffer.write(samples, sampleCount);
    }

    public void onAudioFlush()
    {
        this.mAudioBuffer.clear();
        if (this.mAudioTrack != null) {
            synchronized (this.mPlayingMutex)
            {
                this.mAudioTrack.pause();
                this.mAudioTrack.flush();

                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }
        }
    }

    public void onAudioPaused()
    {
        if (this.mAudioTrack != null) {
            this.mAudioTrack.pause();
        }
    }

    public void onAudioResumed()
    {
        if (this.mAudioTrack != null) {
            this.mAudioTrack.play();
        }
    }

    public void start() {}

    public void stop()
    {
        this.mExecutorService.shutdown();
    }

    @TargetApi(21)
    private void createAudioTrack(int sampleRate, int channels)
    {
        int channelConfig;
        switch (channels)
        {
            case 0:
                throw new IllegalStateException("Input source has 0 channels");
            case 1:
                channelConfig = 4;
                break;
            case 2:
                channelConfig = 12;
                break;
            default:
                throw new IllegalArgumentException("Unsupported input source has " + channels + " channels");
        }
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2) * 2;

        float maxVolume = AudioTrack.getMaxVolume();
        synchronized (this.mPlayingMutex)
        {
            this.mAudioTrack = new AudioTrack(4, sampleRate, channelConfig, 2, bufferSize, 1);
            if (this.mAudioTrack.getState() == 1)
            {
                if (Build.VERSION.SDK_INT >= 21) {
                    this.mAudioTrack.setVolume(maxVolume);
                } else {
                    this.mAudioTrack.setStereoVolume(maxVolume, maxVolume);
                }
                this.mAudioTrack.play();
            }
            else
            {
                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }
        }
    }

    private int writeSamplesToAudioOutput(short[] samples, int samplesCount)
    {
        if (isAudioTrackPlaying())
        {
            int itemsWritten = this.mAudioTrack.write(samples, 0, samplesCount);
            if (itemsWritten > 0) {
                return itemsWritten;
            }
        }
        return 0;
    }

    private boolean isAudioTrackPlaying()
    {
        return (this.mAudioTrack != null) && (this.mAudioTrack.getPlayState() == 3);
    }
}

