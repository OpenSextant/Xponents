package org.opensextant.giscore.utils;

/**
 * Cancelable interface so callers can cancel long running processes.
 * Long running tasks should throw {@link java.util.concurrent.CancellationException}
 * when canceled by this mechanism.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Nov 10, 2009 1:21:53 PM
 */
public interface ICancelable {
	boolean isCanceled();
}
