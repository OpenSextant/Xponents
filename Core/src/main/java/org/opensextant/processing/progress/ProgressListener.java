package org.opensextant.processing.progress;

public interface ProgressListener {

    void updateProgress(double progress);

    void markComplete();

}
