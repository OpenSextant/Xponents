package org.opensextant.processing.progress;

public interface ProgressMonitor {

    public void setNumberOfSteps(int steps);
    public int getNumberOfSteps();
    public void updateStepProgress(double progress);
    public void completeStep();
    public void completeDocument();
    public void addProgressListener(ProgressListener listener);
    public void removeProgressListener(ProgressListener listener);
    
}
