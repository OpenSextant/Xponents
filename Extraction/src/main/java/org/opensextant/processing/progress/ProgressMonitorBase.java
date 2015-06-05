package org.opensextant.processing.progress;

import java.util.ArrayList;
import java.util.List;

public class ProgressMonitorBase implements ProgressMonitor {

    private int steps;
    private double currentStepProgress = 0;
    private double totalProgress = 0;
    private int completedSteps = 0;
    private List<ProgressListener> listeners = new ArrayList<ProgressListener>();

    public ProgressMonitorBase() {
        super();
    }

    @Override
    public void setNumberOfSteps(int steps) {
        this.steps = steps;
        currentStepProgress = 0;
        totalProgress = 0;
        completedSteps = 0;
    }

    @Override
    public int getNumberOfSteps() {
        return this.steps;
    }

    @Override
    public void updateStepProgress(double progress) {
       this.currentStepProgress = progress;
       this.totalProgress = (int)((100.0/steps)*completedSteps + currentStepProgress);
       fireProgressChanged();
    }

    @Override
    public void completeStep() {
        completedSteps++;
        this.currentStepProgress = 0;
        this.totalProgress = (int)((100.0/steps)*completedSteps);
        fireProgressChanged();
    }

    @Override
    public void completeDocument() {
        this.totalProgress = 100;
        this.completedSteps = steps;
        this.currentStepProgress = 0;
        fireProgressChanged();
        fireComplete();
    }

    @Override
    public void addProgressListener(ProgressListener listener) {
        this.listeners.add(listener);
    }
    @Override
    public void removeProgressListener(ProgressListener listener) {
        this.listeners.remove(listener);
    }

    private void fireProgressChanged() {
        for (ProgressListener listener : listeners) {
            listener.updateProgress(this.totalProgress);
        }
    }

    private void fireComplete() {
        for (ProgressListener listener : listeners) {
            listener.markComplte();
        }
    }

}
