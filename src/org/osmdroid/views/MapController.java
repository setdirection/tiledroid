// Created by plusminus on 21:37:08 - 27.09.2008
package org.osmdroid.views;

import org.osmdroid.views.util.constants.MapViewConstants;
import org.osmdroid.views.util.constants.MathConstants;

import android.graphics.Point;

/**
 *
 * @author Nicolas Gramlich
 */
public class MapController implements MapViewConstants {

	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	private final MapView mOsmv;
	private AbstractAnimationRunner mCurrentAnimationRunner;

	// ===========================================================
	// Constructors
	// ===========================================================

	public MapController(final MapView osmv) {
		this.mOsmv = osmv;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================
	/**
	 * Start animating the map towards the given point.
	 */
	public void animateTo(final Point point) {
		mOsmv.setMapCenter(point, false);
		mOsmv.postInvalidate();
	}

	/**
	 * Animates the underlying {@link MapView} that it centers the passed {@link Point} in the
	 * end. Uses: {@link MapController.ANIMATION_SMOOTHNESS_DEFAULT} and
	 * {@link MapController.ANIMATION_DURATION_DEFAULT}.
	 *
	 * @param gp
	 */
	public void animateTo(final Point gp, final AnimationType aAnimationType) {
		animateTo(gp.x, gp.y, aAnimationType,
				ANIMATION_DURATION_DEFAULT, ANIMATION_SMOOTHNESS_DEFAULT);
	}

	/**
	 * Animates the underlying {@link MapView} that it centers the passed {@link Point} in the
	 * end.
	 *
	 * @param gp
	 *            Point to be centered in the end.
	 * @param aSmoothness
	 *            steps made during animation. I.e.: {@link MapController.ANIMATION_SMOOTHNESS_LOW},
	 *            {@link MapController.ANIMATION_SMOOTHNESS_DEFAULT},
	 *            {@link MapController.ANIMATION_SMOOTHNESS_HIGH}
	 * @param aDuration
	 *            in Milliseconds. I.e.: {@link MapController.ANIMATION_DURATION_SHORT},
	 *            {@link MapController.ANIMATION_DURATION_DEFAULT},
	 *            {@link MapController.ANIMATION_DURATION_LONG}
	 */
	public void animateTo(final Point gp, final AnimationType aAnimationType,
			final int aSmoothness, final int aDuration) {
		animateTo(gp.x, gp.y, aAnimationType, aSmoothness, aDuration);
	}

	/**
	 * Animates the underlying {@link MapView} that it centers the passed coordinates in the end.
	 * Uses: {@link MapController.ANIMATION_SMOOTHNESS_DEFAULT} and
	 * {@link MapController.ANIMATION_DURATION_DEFAULT}.
	 *
	 * @param aWorldX
	 * @param aWorldY
	 */
	public void animateTo(final int aWorldX, final int aWorldY,
			final AnimationType aAnimationType) {
		animateTo(aWorldX, aWorldY, aAnimationType, ANIMATION_SMOOTHNESS_DEFAULT,
				ANIMATION_DURATION_DEFAULT);
	}

	/**
	 * Animates the underlying {@link MapView} that it centers the passed coordinates in the end.
	 *
	 * @param aWorldX
	 * @param aWorldY
	 * @param aSmoothness
	 *            steps made during animation. I.e.: {@link MapController.ANIMATION_SMOOTHNESS_LOW},
	 *            {@link MapController.ANIMATION_SMOOTHNESS_DEFAULT},
	 *            {@link MapController.ANIMATION_SMOOTHNESS_HIGH}
	 * @param aDuration
	 *            in Milliseconds. I.e.: {@link MapController.ANIMATION_DURATION_SHORT},
	 *            {@link MapController.ANIMATION_DURATION_DEFAULT},
	 *            {@link MapController.ANIMATION_DURATION_LONG}
	 */
	public void animateTo(final int aWorldX, final int aWorldY,
			final AnimationType aAnimationType, final int aSmoothness, final int aDuration) {
		this.stopAnimation(false);

		switch (aAnimationType) {
		case LINEAR:
			this.mCurrentAnimationRunner = new LinearAnimationRunner(aWorldX, aWorldY,
					aSmoothness, aDuration);
			break;
		case EXPONENTIALDECELERATING:
			this.mCurrentAnimationRunner = new ExponentialDeceleratingAnimationRunner(aWorldX,
					aWorldY, aSmoothness, aDuration);
			break;
		case QUARTERCOSINUSALDECELERATING:
			this.mCurrentAnimationRunner = new QuarterCosinusalDeceleratingAnimationRunner(
					aWorldX, aWorldY, aSmoothness, aDuration);
			break;
		case HALFCOSINUSALDECELERATING:
			this.mCurrentAnimationRunner = new HalfCosinusalDeceleratingAnimationRunner(
					aWorldX, aWorldY, aSmoothness, aDuration);
			break;
		case MIDDLEPEAKSPEED:
			this.mCurrentAnimationRunner = new MiddlePeakSpeedAnimationRunner(aWorldX,
					aWorldY, aSmoothness, aDuration);
			break;
		}

		this.mCurrentAnimationRunner.start();
	}

	public void scrollBy(final int x, final int y) {
		this.mOsmv.scrollBy(x, y);
	}

	/**
	 * Set the map view to the given center. There will be no animation.
	 */
	public void setCenter(final Point point) {
		this.mOsmv.setMapCenter(point, true);
	}

	/**
	 * Stops a running animation.
	 *
	 * @param jumpToTarget
	 */
	public void stopAnimation(final boolean jumpToTarget) {
		final AbstractAnimationRunner currentAnimationRunner = this.mCurrentAnimationRunner;

		if (currentAnimationRunner != null && !currentAnimationRunner.isDone()) {
			currentAnimationRunner.interrupt();
			if (jumpToTarget) {
				setCenter(new Point(currentAnimationRunner.mTargetWorldX,
						currentAnimationRunner.mTargetWorldY));
			}
		}
	}

	public int setZoom(final int zoomlevel) {
		return mOsmv.setZoomLevel(zoomlevel);
	}

	/**
	 * Zoom in by one zoom level.
	 */
	public boolean zoomIn() {
		return mOsmv.zoomIn();
	}

	public boolean zoomInFixing(final Point point) {
		return mOsmv.zoomInFixing(point);
	}

	public boolean zoomInFixing(final int xPixel, final int yPixel) {
		return mOsmv.zoomInFixing(xPixel, yPixel);
	}

	/**
	 * Zoom out by one zoom level.
	 */
	public boolean zoomOut() {
		return mOsmv.zoomOut();
	}

	public boolean zoomOutFixing(final Point point) {
		return mOsmv.zoomOutFixing(point);
	}

	public boolean zoomOutFixing(final int xPixel, final int yPixel) {
		return mOsmv.zoomOutFixing(xPixel, yPixel);
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	/**
	 * Choose on of the Styles of approacing the target Coordinates.
	 * <ul>
	 * <li><code>LINEAR</code>
	 * <ul>
	 * <li>Uses ses linear interpolation</li>
	 * <li>Values produced: 10%, 20%, 30%, 40%, 50%, ...</li>
	 * <li>Style: Always average speed.</li>
	 * </ul>
	 * </li>
	 * <li><code>EXPONENTIALDECELERATING</code>
	 * <ul>
	 * <li>Uses a exponential interpolation/li>
	 * <li>Values produced: 50%, 75%, 87.5%, 93.5%, ...</li>
	 * <li>Style: Starts very fast, really slow in the end.</li>
	 * </ul>
	 * </li>
	 * <li><code>QUARTERCOSINUSALDECELERATING</code>
	 * <ul>
	 * <li>Uses the first quarter of the cos curve (from zero to PI/2) for interpolation.</li>
	 * <li>Values produced: See cos curve :)</li>
	 * <li>Style: Average speed, slows out medium.</li>
	 * </ul>
	 * </li>
	 * <li><code>HALFCOSINUSALDECELERATING</code>
	 * <ul>
	 * <li>Uses the first half of the cos curve (from zero to PI) for interpolation</li>
	 * <li>Values produced: See cos curve :)</li>
	 * <li>Style: Average speed, slows out smoothly.</li>
	 * </ul>
	 * </li>
	 * <li><code>MIDDLEPEAKSPEED</code>
	 * <ul>
	 * <li>Uses the values of cos around the 0 (from -PI/2 to +PI/2) for interpolation</li>
	 * <li>Values produced: See cos curve :)</li>
	 * <li>Style: Starts medium, speeds high in middle, slows out medium.</li>
	 * </ul>
	 * </li>
	 * </ul>
	 */
	public static enum AnimationType {
		/**
		 * <ul>
		 * <li><code>LINEAR</code>
		 * <ul>
		 * <li>Uses ses linear interpolation</li>
		 * <li>Values produced: 10%, 20%, 30%, 40%, 50%, ...</li>
		 * <li>Style: Always average speed.</li>
		 * </ul>
		 * </li>
		 * </ul>
		 */
		LINEAR,
		/**
		 * <ul>
		 * <li><code>EXPONENTIALDECELERATING</code>
		 * <ul>
		 * <li>Uses a exponential interpolation/li>
		 * <li>Values produced: 50%, 75%, 87.5%, 93.5%, ...</li>
		 * <li>Style: Starts very fast, really slow in the end.</li>
		 * </ul>
		 * </li>
		 * </ul>
		 */
		EXPONENTIALDECELERATING,
		/**
		 * <ul>
		 * <li><code>QUARTERCOSINUSALDECELERATING</code>
		 * <ul>
		 * <li>Uses the first quarter of the cos curve (from zero to PI/2) for interpolation.</li>
		 * <li>Values produced: See cos curve :)</li>
		 * <li>Style: Average speed, slows out medium.</li>
		 * </ul>
		 * </li>
		 * </ul>
		 */
		QUARTERCOSINUSALDECELERATING,
		/**
		 * <ul>
		 * <li><code>HALFCOSINUSALDECELERATING</code>
		 * <ul>
		 * <li>Uses the first half of the cos curve (from zero to PI) for interpolation</li>
		 * <li>Values produced: See cos curve :)</li>
		 * <li>Style: Average speed, slows out smoothly.</li>
		 * </ul>
		 * </li>
		 * </ul>
		 */
		HALFCOSINUSALDECELERATING,
		/**
		 * <ul>
		 * <li><code>MIDDLEPEAKSPEED</code>
		 * <ul>
		 * <li>Uses the values of cos around the 0 (from -PI/2 to +PI/2) for interpolation</li>
		 * <li>Values produced: See cos curve :)</li>
		 * <li>Style: Starts medium, speeds high in middle, slows out medium.</li>
		 * </ul>
		 * </li>
		 * </ul>
		 */
		MIDDLEPEAKSPEED;
	}

	private abstract class AbstractAnimationRunner extends Thread {

		// ===========================================================
		// Fields
		// ===========================================================

		protected final int mSmoothness;
		protected final int mTargetWorldX, mTargetWorldY;
		protected boolean mDone = false;

		protected final int mStepDuration;

		protected final int mPanTotalWorldX, mPanTotalWorldY;

		// ===========================================================
		// Constructors
		// ===========================================================

		@SuppressWarnings("unused")
		public AbstractAnimationRunner(final MapController mapViewController,
				final int targetWorldX, final int targetWorldY) {
			this(targetWorldX, targetWorldY,
					MapViewConstants.ANIMATION_SMOOTHNESS_DEFAULT,
					MapViewConstants.ANIMATION_DURATION_DEFAULT);
		}

		public AbstractAnimationRunner(final int targetWorldX, final int targetWorldY,
				final int aSmoothness, final int aDuration) {
			this.mTargetWorldX = targetWorldX;
			this.mTargetWorldY = targetWorldX;
			this.mSmoothness = aSmoothness;
			this.mStepDuration = aDuration / aSmoothness;

			/* Get the current mapview-center. */
			final MapView mapview = MapController.this.mOsmv;
			final Point mapCenter = mapview.getMapCenter();

			this.mPanTotalWorldX = mapCenter.x - targetWorldX;
			this.mPanTotalWorldY = mapCenter.y - targetWorldY;
		}

		@Override
		public void run() {
			onRunAnimation();
			this.mDone = true;
		}

		public boolean isDone() {
			return this.mDone;
		}

		public abstract void onRunAnimation();
	}

	private class LinearAnimationRunner extends AbstractAnimationRunner {

		// ===========================================================
		// Fields
		// ===========================================================

		protected final int mPanPerStepWorldX, mPanPerStepWorldY;

		// ===========================================================
		// Constructors
		// ===========================================================

		@SuppressWarnings("unused")
		public LinearAnimationRunner(final int aTargetWorldX, final int aTargetWorldY) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT);
		}

		public LinearAnimationRunner(final int targetWorldX, final int targetWorldY,
				final int aSmoothness, final int aDuration) {
			super(targetWorldX, targetWorldY, aSmoothness, aDuration);

			/* Get the current mapview-center. */
			final MapView mapview = MapController.this.mOsmv;
			final Point mapCenter = mapview.getMapCenter();

			this.mPanPerStepWorldX = (mapCenter.x - targetWorldX) / aSmoothness;
			this.mPanPerStepWorldY = (mapCenter.y - targetWorldY) / aSmoothness;

			this.setName("LinearAnimationRunner");
		}

		// ===========================================================
		// Methods from SuperClass/Interfaces
		// ===========================================================

		@Override
		public void onRunAnimation() {
			final MapView mapview = MapController.this.mOsmv;
			final int panPerStepWorldX = this.mPanPerStepWorldX;
			final int panPerStepWorldY = this.mPanPerStepWorldY;
			final int stepDuration = this.mStepDuration;
			try {
				int newMapCenterWorldX;
				int newMapCenterWorldY;

				for (int i = this.mSmoothness; i > 0; i--) {
					final Point mapCenter = mapview.getMapCenter();

					newMapCenterWorldX = mapCenter.x - panPerStepWorldX;
					newMapCenterWorldY = mapCenter.y - panPerStepWorldY;
					mapview.setMapCenter(newMapCenterWorldX, newMapCenterWorldY, false);

					Thread.sleep(stepDuration);
				}
			} catch (final Exception e) {
				this.interrupt();
			}
		}
	}

	private class ExponentialDeceleratingAnimationRunner extends AbstractAnimationRunner {

		// ===========================================================
		// Fields
		// ===========================================================

		// ===========================================================
		// Constructors
		// ===========================================================

		@SuppressWarnings("unused")
		public ExponentialDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT);
		}

		public ExponentialDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final int aSmoothness, final int aDuration) {
			super(aTargetWorldX, aTargetWorldY, aSmoothness, aDuration);

			this.setName("ExponentialDeceleratingAnimationRunner");
		}

		// ===========================================================
		// Methods from SuperClass/Interfaces
		// ===========================================================

		@Override
		public void onRunAnimation() {
			final MapView mapview = MapController.this.mOsmv;
			final int stepDuration = this.mStepDuration;
			try {
				int newMapCenterWorldX;
				int newMapCenterWorldY;

				for (int i = 0; i < this.mSmoothness; i++) {

					final Point mapCenter = mapview.getMapCenter();
					final double delta = Math.pow(0.5, i + 1);
					final int deltaWorldX = (int) (this.mPanTotalWorldX * delta);
					final int detlaWorldY = (int) (this.mPanTotalWorldY * delta);

					newMapCenterWorldX = mapCenter.x - deltaWorldX;
					newMapCenterWorldY = mapCenter.y - detlaWorldY;
					mapview.setMapCenter(newMapCenterWorldX, newMapCenterWorldY, false);

					Thread.sleep(stepDuration);
				}
				mapview.setMapCenter(mTargetWorldX, mTargetWorldY, false);
			} catch (final Exception e) {
				this.interrupt();
			}
		}
	}

	private class CosinusalBasedAnimationRunner extends AbstractAnimationRunner implements
			MathConstants {
		// ===========================================================
		// Fields
		// ===========================================================

		protected final float mStepIncrement, mAmountStretch;
		protected final float mYOffset, mStart;

		// ===========================================================
		// Constructors
		// ===========================================================

		@SuppressWarnings("unused")
		public CosinusalBasedAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final float aStart, final float aRange,
				final float aYOffset) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT, aStart, aRange, aYOffset);
		}

		public CosinusalBasedAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final int aSmoothness, final int aDuration,
				final float aStart, final float aRange, final float aYOffset) {
			super(aTargetWorldX, aTargetWorldY, aSmoothness, aDuration);
			this.mYOffset = aYOffset;
			this.mStart = aStart;

			this.mStepIncrement = aRange / aSmoothness;

			/* We need to normalize the amount in the end, so wee need the the: sum^(-1) . */
			float amountSum = 0;
			for (int i = 0; i < aSmoothness; i++) {
				amountSum += aYOffset + Math.cos(this.mStepIncrement * i + aStart);
			}

			this.mAmountStretch = 1 / amountSum;

			this.setName("QuarterCosinusalDeceleratingAnimationRunner");
		}

		// ===========================================================
		// Methods from SuperClass/Interfaces
		// ===========================================================

		@Override
		public void onRunAnimation() {
			final MapView mapview = MapController.this.mOsmv;
			final int stepDuration = this.mStepDuration;
			final float amountStretch = this.mAmountStretch;
			try {
				int newMapCenterWorldX;
				int newMapCenterWorldY;

				for (int i = 0; i < this.mSmoothness; i++) {

					final Point mapCenter = mapview.getMapCenter();
					final double delta = (this.mYOffset + Math.cos(this.mStepIncrement * i
							+ this.mStart))
							* amountStretch;
					final int deltaWorldX = (int) (this.mPanTotalWorldX * delta);
					final int deltaWorldY = (int) (this.mPanTotalWorldY * delta);

					newMapCenterWorldX = mapCenter.x - deltaWorldX;
					newMapCenterWorldY = mapCenter.y - deltaWorldY;
					mapview.setMapCenter(newMapCenterWorldX, newMapCenterWorldY, false);

					Thread.sleep(stepDuration);
				}
				mapview.setMapCenter(mTargetWorldX, mTargetWorldY, false);
			} catch (final Exception e) {
				this.interrupt();
			}
		}
	}

	protected class QuarterCosinusalDeceleratingAnimationRunner extends
			CosinusalBasedAnimationRunner implements MathConstants {
		// ===========================================================
		// Constructors
		// ===========================================================

		protected QuarterCosinusalDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT);
		}

		protected QuarterCosinusalDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final int aSmoothness, final int aDuration) {
			super(aTargetWorldX, aTargetWorldY, aSmoothness, aDuration, 0, PI_2, 0);
		}
	}

	protected class HalfCosinusalDeceleratingAnimationRunner extends CosinusalBasedAnimationRunner
			implements MathConstants {
		// ===========================================================
		// Constructors
		// ===========================================================

		protected HalfCosinusalDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT);
		}

		protected HalfCosinusalDeceleratingAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final int aSmoothness, final int aDuration) {
			super(aTargetWorldX, aTargetWorldY, aSmoothness, aDuration, 0, PI, 1);
		}
	}

	protected class MiddlePeakSpeedAnimationRunner extends CosinusalBasedAnimationRunner implements
			MathConstants {
		// ===========================================================
		// Constructors
		// ===========================================================

		protected MiddlePeakSpeedAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY) {
			this(aTargetWorldX, aTargetWorldY, ANIMATION_SMOOTHNESS_DEFAULT,
					ANIMATION_DURATION_DEFAULT);
		}

		protected MiddlePeakSpeedAnimationRunner(final int aTargetWorldX,
				final int aTargetWorldY, final int aSmoothness, final int aDuration) {
			super(aTargetWorldX, aTargetWorldY, aSmoothness, aDuration, -PI_2, PI, 0);
		}
	}
}
