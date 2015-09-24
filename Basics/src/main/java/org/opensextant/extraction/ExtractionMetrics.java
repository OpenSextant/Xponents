/**
 * Copyright 2012-2013 The MITRE Corporation.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government
 * under Contract No. W15P7T-12-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 **************************************************   */
package org.opensextant.extraction;

/**
 *
 * @author ubaldino
 */
public class ExtractionMetrics {

    private String name = null;
    private int callCount = 0;
    /**
     * total time spent for this metric in milliseconds
     */
    private long totalTime = 0;

    /**
     * A named metric
     * @param nm name of metric
     */
    public ExtractionMetrics(String nm) {
        this.name = nm;
    }

    @Override
    public String toString() {
        return "Metric " + this.name + " Calls:" + this.getCallCount()
                + " Average time(ms):" + this.getAverageTime()
                + " with Total time(ms):" + this.getTotalTime();
    }

    /**
     * avg time spent for this metric in milliseconds
     * @return average time
     */
    public int getAverageTime() {
        if (callCount == 0) return 0;
        return (int) (totalTime / callCount);
    }

    public void addTime(long time) {
        totalTime += time;
        ++callCount;
    }

    public void addTime(long time, int calls) {
        totalTime += time;
        callCount += calls;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public int getCallCount() {
        return callCount;
    }
}
