package net.thinkindifferent.apsk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import org.wa5znu.znuradio.dsp.Complex;
import org.wa5znu.znuradio.receiver.Controller;
import org.wa5znu.znuradio.receiver.Receiver;

public class APSK extends Activity implements Controller {
	private static final String TAG = "APSK";
	private Handler handler;
	private Receiver receiver;
	private double frequency = 1500.0;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	handler = new Handler();
		receiver = new Receiver(this);
    	if(!receiver.startReceiver())
    		Log.e(TAG, "startReceiver() failed!");
    	receiver.addDemodulator(this, this);
    	receiver.setFrequency(frequency);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }

	@Override
	public void setFrequency(double f) {
		setFrequency(f, false);
	}

	@Override
	public void setFrequency(double f, boolean userClick) {
		receiver.setFrequency(f);
	}

	@Override
	public void setSampleRate(int f) {
	}

	@Override
	public void showNextStage() {
		receiver.showNextStage();
	}

	@Override
	public void handleStage(int frame, double[] data, int length) {
	}

	@Override
	public void handleStage(int frame, Complex[] data, int length) {
	}

	@Override
	public void handlePhase(int frame, double phi, boolean dcd) {
	}

	@Override
	public void handleText(int frame, final String s) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				updateTextView(s);
			}
		});
	}
	
	private void updateTextView(String s) {
		TextView in = (TextView)this.findViewById(R.id.in);
		if(in != null)
			in.append(s);
	}

	@Override
	public void handleSpectrum(int frame, double[] data, int length) {
		Waterfall waterfall = (Waterfall)findViewById(R.id.waterfall);
		if(waterfall != null)
			waterfall.handleSpectrum(data, length);
	}
}