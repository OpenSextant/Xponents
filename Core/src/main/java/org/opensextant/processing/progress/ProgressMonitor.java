package org.opensextant.processing.progress;

public interface ProgressMonitor {

    void setNumberOfSteps(int steps);

    int getNumberOfSteps();

    void updateStepProgress(double progress);

    void completeStep();

    void completeDocument();

    void addProgressListener(ProgressListener listener);

    void removeProgressListener(ProgressListener listener);

}
