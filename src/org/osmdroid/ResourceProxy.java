package org.osmdroid;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public interface ResourceProxy {

	public static enum string {
		unknown

	}

	public static enum bitmap {

		/**
		 * For testing - the image doesn't exist.
		 */
		unknown,

		center, marker_default, marker_default_focused_base, navto_small, next, previous, person,
	}

	String getString(string pResId);

	/**
	 * Use a string resource as a format definition, and format using the supplied format arguments.
	 *
	 * @param pResId
	 * @param formatArgs
	 * @return
	 */
	String getString(string pResId, Object... formatArgs);

	Bitmap getBitmap(bitmap pResId);

	/**
	 * Get a bitmap as a {@link Drawable}
	 *
	 * @param pResId
	 * @return
	 */
	Drawable getDrawable(bitmap pResId);
}
