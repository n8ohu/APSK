package net.thinkindifferent.apsk;

import java.util.concurrent.LinkedBlockingQueue;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;

import net.thinkindifferent.apsk.APSK;

public class Waterfall extends SurfaceView implements Callback {
	private static final String TAG = "APSK/Waterfall";
	class WaterfallThread extends Thread {
		class Palette {
			int r, b, g;
			Palette(int r, int b, int g) {
				this.r = r; this.b = b; this.g = g;
			}
		}
		
		class Tic {
			boolean major;
			double freq;
			String str;
			int strw;
			int strh;
			int strx;
			int x;
			Tic next;
		  }
		
		private static final int RULER_HEIGHT = 20;
		private static final int BACKGROUND_COLOR = Color.BLACK;
		private static final int FREQUENCY_COLOR = Color.GREEN;
		private static final int FREQUENCY_SCALE_COLOR = Color.WHITE;
		private static final double BANDWIDTH = 31.25;
		private static final double START_FREQUENCY = 0.0;
		private SurfaceHolder holder;
		private int canvasWidth = 1;
		private int canvasHeight = 1;
		private double frequency = 1500.0;
		private double hertzPerTick = (4000.0 / canvasWidth);
		private int frequencyInTicks = (int)Math.round(frequency / hertzPerTick);
		private int halfBandwidthInTicks = (int)Math.round(BANDWIDTH / 2 / hertzPerTick);
		private boolean running = false;
		private boolean lsb = false;
		private double carrierfreq = 0;
		private Bitmap waterfallBitmap;
		private Canvas waterfallCanvas;
		private int[] palette;
		private LinkedBlockingQueue<Pair<Integer, double[]>> dataQueue;
		
		public WaterfallThread(SurfaceHolder holder) {
			this.holder = holder;
			dataQueue = new LinkedBlockingQueue<Pair<Integer, double[]>>();
		}
		
		@Override
		public void start() {
			initCanvas();
			super.start();
		}
		
		@Override
		public void run() {
			while (running) {
				Canvas canvas = holder.lockCanvas();
				synchronized (holder) {
					Paint paint = new Paint();
					while (!dataQueue.isEmpty()) {
						Pair<Integer, double[]> p = dataQueue.poll();
						int length = p.first;
						double[] data = p.second;
						scroll();
						for(int x = 0; x < length; x++) {
							double b = data[x];
							plot(x, 0, (int)Math.round(b * 8));
						}
					}
					canvas.drawBitmap(waterfallBitmap, 0, RULER_HEIGHT, paint);
					drawTicks(canvas);
					drawRuler(canvas);
				}
				holder.unlockCanvasAndPost(canvas);
				try {
					sleep(50);
				} catch (InterruptedException e) { }
			}
		}
				
		private void initCanvas() {
			Paint paint = new Paint();
			paint.setColor(BACKGROUND_COLOR);
			waterfallBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight - RULER_HEIGHT > 0 ? canvasHeight - RULER_HEIGHT : 1, Bitmap.Config.ARGB_8888);
			waterfallCanvas = new Canvas(waterfallBitmap);
			waterfallCanvas.drawPaint(paint);
			Canvas canvas = holder.lockCanvas();
			synchronized (holder) {
				canvas.drawPaint(paint);
				drawTicks(canvas);
				drawRuler(canvas);
			}
			holder.unlockCanvasAndPost(canvas);			
		}
		
		public void handleSpectrum(double[] data, int length) {
			if (running) {
				dataQueue.add(new Pair<Integer, double[]>(length, data));
			}
		}
		
		private void drawTicks(Canvas canvas) {
			if(frequencyInTicks > 0) {
				Paint paint = new Paint();
				paint.setColor(FREQUENCY_COLOR);
				canvas.drawLine(frequencyInTicks - halfBandwidthInTicks, RULER_HEIGHT, frequencyInTicks - halfBandwidthInTicks, canvasHeight, paint);
				canvas.drawLine(frequencyInTicks + halfBandwidthInTicks, RULER_HEIGHT, frequencyInTicks + halfBandwidthInTicks, canvasHeight, paint);
			}
		}
			
		private void drawRuler(Canvas canvas) {
			Paint paint = new Paint();
			paint.setColor(FREQUENCY_SCALE_COLOR);
			paint.setTextSize(10);
			Tic tics = buildTics(paint);
			while (tics != null) {
				if (tics.major) {
					canvas.drawLine(tics.x, RULER_HEIGHT - 9, tics.x, RULER_HEIGHT - 1, paint);
					canvas.drawText(tics.str, tics.strx, RULER_HEIGHT - 10, paint);
				} else {
					canvas.drawLine(tics.x, RULER_HEIGHT - 5, tics.x, RULER_HEIGHT - 1, paint);
				}
				tics = tics.next;
			}
		}
		
		private void scroll() {
			waterfallCanvas.translate(0, 1);
		}
		
		private void plot(int x, int y, int color) {
			Paint paint = new Paint();
			readPalette();
			paint.setColor(palette[color]);
			waterfallCanvas.drawRect(x, 0, x+1, 1, paint);
		}
		
		private void readPalette() {
			palette = new int[9];
			palette[0] = Color.rgb(  0,   0,   0);
			palette[1] = Color.rgb(  0,   0,  62);
			palette[2] = Color.rgb(  0,   0, 126);
			palette[3] = Color.rgb(  0,   0, 214);
			palette[4] = Color.rgb(145, 142,  96);
			palette[5] = Color.rgb(181, 184,  48);
			palette[6] = Color.rgb(223, 226, 105);
			palette[7] = Color.rgb(254, 254,   4);
			palette[8] = Color.rgb(255, 258,   0);
		}
		
		private Tic buildTics(Paint paint) {
			Tic list = null, p;
			double f = START_FREQUENCY, realFreq;
			int i, ifreq;
			
			for (i = 0; i < canvasWidth; i++) {
				if (lsb)
					realFreq = f - carrierfreq;
				else
					realFreq = f + carrierfreq;
				realFreq = Math.abs(realFreq);
				ifreq = (int)(100 * Math.floor(realFreq / 100.0 + 0.5));
				if (ifreq < realFreq || ifreq >= realFreq + hertzPerTick) {
					f += hertzPerTick;
					continue;
				}
				p = new Tic();
				p.major = false;
				p.freq = ifreq;
				p.x = i;
				if ((ifreq % 500) == 0) {
					int khz = ifreq  / 1000;
					int hz = ifreq % 1000;
					if (khz > 9)
						p.str= khz + "." + hz;
					else
						p.str= "" + ifreq;
					p.strw = Math.round(paint.measureText(p.str));
					p.strh = Math.round(paint.getFontMetrics().ascent);
					p.strx = clamp(i - p.strw / 2, 0, canvasWidth - p.strw);
					p.major = true;
				}
				f += hertzPerTick;
				p.next = list;
				list = p;
			}
			return list;
		}
		
		private int clamp(int x, int low, int high) {
			return (((x)>(high))?(high):(((x)<(low))?(low):(x)));
		}
		
		public void setFrequency(double f) {
			Log.i(TAG, "WaterfallThread.setFrequency, f = " + f);
			frequency = f;
			frequencyInTicks = (int)Math.round(frequency / hertzPerTick);
		}
		
		public void setSurfaceSize(int width, int height) {
			canvasWidth = width;
			canvasHeight = height;
			hertzPerTick = (4000.0 / canvasWidth);
			frequencyInTicks = (int)Math.round(frequency / hertzPerTick);
			halfBandwidthInTicks = (int)Math.round(BANDWIDTH / 2 / hertzPerTick);
			initCanvas();
		}
		
		public void setRunning(boolean b) {
			running = b;
		}
	}
	
	private WaterfallThread thread;
	private APSK context;
	
	public Waterfall(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = (APSK)context;
		setWillNotDraw(false);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		thread = new WaterfallThread(holder);
		setFocusable(true);
	}
	
	public WaterfallThread getThread() {
		return thread;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		thread.setSurfaceSize(width, height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		thread.setRunning(true);
		thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) { }
		}
	}
	
	public void handleSpectrum(double[] data, int length) {
		thread.handleSpectrum(data, length);
	}
	
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
			DisplayMetrics dm = new DisplayMetrics();
			context.getWindowManager().getDefaultDisplay().getMetrics(dm);
			int width = dm.widthPixels;
			int x = Math.round(event.getX());
			double f = 4000.0 * x / width;
			context.setFrequency(f);
			thread.setFrequency(f);
		}
		return true;
	}
}
