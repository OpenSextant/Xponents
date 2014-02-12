package org.opensextant.processing.progress;

public interface ProgressListener {

    public void updateProgress(double progress);
    public void markComplte();
    
}
