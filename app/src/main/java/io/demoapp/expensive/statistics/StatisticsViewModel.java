/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.demoapp.expensive.statistics;

import android.app.Application;
import android.databinding.Bindable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.support.annotation.Keep;
import android.support.annotation.VisibleForTesting;

import io.demoapp.expensive.Injection;
import io.demoapp.expensive.R;
import io.demoapp.expensive.data.Task;
import io.demoapp.expensive.data.source.TasksDataSource;
import io.demoapp.expensive.data.source.TasksRepository;
import io.demoapp.expensive.util.ObservableViewModel;

import java.util.List;

/**
 * Exposes the data to be used in the statistics screen.
 * <p>
 * This ViewModel uses both {@link ObservableField}s ({@link ObservableBoolean}s in this case) and
 * {@link Bindable} getters. The values in {@link ObservableField}s are used directly in the layout,
 * whereas the {@link Bindable} getters allow us to add some logic to it. This is
 * preferable to having logic in the XML layout.
 */
public class StatisticsViewModel extends ObservableViewModel {

    public final ObservableBoolean dataLoading = new ObservableBoolean(false);

    final ObservableBoolean error = new ObservableBoolean(false);

    @VisibleForTesting
    int mNumberOfActiveTasks = 0;

    @VisibleForTesting
    int mNumberOfCompletedTasks = 0;

    private final TasksRepository mTasksRepository;

    @Keep
    public StatisticsViewModel(Application application) {
        super(application);
        mTasksRepository = Injection.provideTasksRepository(application);
    }

    public void start() {
        loadStatistics();
    }

    public void loadStatistics() {
        dataLoading.set(true);

        mTasksRepository.getTasks(new TasksDataSource.LoadTasksCallback() {
            @Override
            public void onTasksLoaded(List<Task> tasks) {
                computeStats(tasks);
            }

            @Override
            public void onDataNotAvailable() {
                error.set(true);
            }
        });
    }
    /**
     * Returns a String showing the number of active tasks.
     */
    @Bindable
    public String getNumberOfActiveTasks() {
        return getApplication().getString(
                R.string.statistics_active_tasks,
                mNumberOfActiveTasks);
    }

    /**
     * Returns a String showing the number of completed tasks.
     */
    @Bindable
    public String getNumberOfCompletedTasks() {
        return getApplication().getString(
                R.string.statistics_completed_tasks,
                mNumberOfCompletedTasks);
    }

    /**
     * Controls whether the stats are shown or a "No data" message.
     */
    @Bindable
    public boolean isEmpty() {
        return mNumberOfActiveTasks + mNumberOfCompletedTasks == 0;
    }

    /**
     * Called when new data is ready.
     */
    private void computeStats(List<Task> tasks) {
        int completed = 0;
        int active = 0;

        for (Task task : tasks) {
            if (task.isCompleted()) {
                completed += 1;
            } else {
                active += 1;
            }
        }
        mNumberOfActiveTasks = active;
        mNumberOfCompletedTasks = completed;

        // There are multiple @Bindable fields in this ViewModel, calling notifyChange() will
        // update all the UI elements that depend on them.
        notifyChange();

        // To update just one of them and avoid unnecessary UI updates,
        // use notifyPropertyChanged(BR.field)

        // Observable fields don't need to be notified. set() will trigger an update.
        dataLoading.set(false);
        error.set(false);
    }
}
