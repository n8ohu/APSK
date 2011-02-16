package net.thinkindifferent.apsk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.wa5znu.znuradio.dsp.Subsampler;
import org.wa5znu.znuradio.receiver.WaveHandler;

public class AudioInputThread extends Thread {
	private static AudioRecord audioRecord;
	private static int frameSize;
	private static boolean stopRequested = true;
	private static WaveHandler waveHandler;
	private int deviceSampleRate;
	private byte frameBytes[];
	private double data[];
	private int frame = 0;
	private ByteBuffer frameByteBuffer;
	private Subsampler subsampler;
	private static final int OUTPUT_SAMPLES = 2048;
	
	public AudioInputThread(int outputSampleRate, int deviceSampleRate) {
		this.deviceSampleRate = deviceSampleRate;
		int oversampleRate = deviceSampleRate / outputSampleRate;
		if (outputSampleRate > 1) {
			subsampler = new Subsampler(oversampleRate);
		}
		else if (oversampleRate < 1) {
			throw new RuntimeException("AudioInputThread cannot interpolate: outputSampleRate=" + outputSampleRate + " deviceSampleRate=" + deviceSampleRate);
		}
		frameSize = OUTPUT_SAMPLES * oversampleRate;
		frameBytes = new byte[frameSize * 2];
		frameByteBuffer = ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN);
		data = new double[OUTPUT_SAMPLES * oversampleRate];
	}
	
	public void start() {
		stopRequested = false;
		audioRecord.startRecording();
		super.start();
	}
	
	public void run() {
		int nInputSamples = data.length;
		while (!stopRequested) {
			frameByteBuffer.clear();
			int bytesRead = audioRecord.read(frameBytes, 0, frameBytes.length);
			if (bytesRead == frameBytes.length) {
				double data[] = new double[nInputSamples];
				for (int n = 0; n < nInputSamples; n++) {
					data[n] = (double)(frameByteBuffer.getShort()) / 32768.0;
				}
				if (subsampler != null)
						data = subsampler.subsample(data, nInputSamples);
				waveHandler.handleWave(frame, data, OUTPUT_SAMPLES);
			}
			frame++;
		}
	}
	
	public boolean startAudio(WaveHandler waveHandler) {
		stopAudio();
		{
			int mono = Integer.parseInt(android.os.Build.VERSION.SDK) < 5 ? AudioFormat.CHANNEL_CONFIGURATION_MONO : AudioFormat.CHANNEL_IN_MONO;
			audioRecord = new AudioRecord(AudioSource.DEFAULT, deviceSampleRate, mono, AudioFormat.ENCODING_PCM_16BIT, 2 * AudioRecord.getMinBufferSize(deviceSampleRate, mono, AudioFormat.ENCODING_PCM_16BIT));
		}
		if (audioRecord == null) {
			return false;
		}
		AudioInputThread.waveHandler = waveHandler;
		start();
		return true;
	}
	
	public void stopAudio() {
		if (stopRequested || audioRecord == null) return;
		stopRequested = true;
		interrupt();
		try {
			join();
		} catch (InterruptedException exp) { }
		audioRecord.stop();
		audioRecord = null;
	}
}
