/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.extraction;

/**
 * This is a holder for tracking various common measures: No. of Calls, Amount
 * of time, No. of bytes
 * Delta quantities (addBytes, addTime, addTimeSince), etc. cannot represent
 * negative quantities.
 *
 * @author ubaldino
 */
public class ExtractionMetrics {

    private String name = null;
    private int callCount = 0;
    private long byteCount = 0L;
    /**
     * total time spent for this metric in milliseconds
     */
    private long totalTime = 0;

    /**
     * A named metric
     *
     * @param nm
     *           name of metric
     */
    public ExtractionMetrics(String nm) {
        this.name = nm;
    }

    @Override
    public String toString() {
        return String.format("Metric %s calls=%d,  avg time=%3d (ms), tot time=%d (ms), tot bytes=%d", name,
                getCallCount(), getAverageTime(), getTotalTime(), getByteCount());
    }

    /**
     * avg time spent for this metric in milliseconds
     *
     * @return average time
     */
    public int getAverageTime() {
        if (callCount == 0)
            return 0;
        return (int) (totalTime / callCount);
    }

    /**
     * Add just a time delta.
     *
     * @param delta
     *              milliseconds span
     */
    public void addTime(long delta) {
        if (delta >= 0) {
            totalTime += delta;
            ++callCount;
        }
    }

    /**
     * Add time delta using NOW - time.
     *
     * @param epoch
     *              milliseconds epoch
     */
    public void addTimeSince(long epoch) {
        long delta = System.currentTimeMillis() - epoch;
        if (delta > 0) {
            totalTime += delta;
            ++callCount;
        }
    }

    /**
     * Add just a time delta.
     *
     * @param delta
     *              milliseconds span
     * @param calls
     *              number of calls for this metric that occured over delta
     */

    public void addTime(long delta, int calls) {
        if (delta >= 0) {
            totalTime += delta;
            callCount += calls;
        }
    }

    public long getTotalTime() {
        return totalTime;
    }

    public int getCallCount() {
        return callCount;
    }

    public void addBytes(long delta) {
        if (delta > 0) {
            byteCount += delta;
        }
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long c) {
        this.byteCount = c;
    }
}
