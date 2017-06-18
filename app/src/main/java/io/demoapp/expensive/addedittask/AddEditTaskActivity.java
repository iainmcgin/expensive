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

package io.demoapp.expensive.addedittask;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import io.demoapp.expensive.R;
import io.demoapp.expensive.util.FragmentProviders;

import static io.demoapp.expensive.addedittask.AddEditTaskFragment.ARGUMENT_EDIT_TASK_ID;

/**
 * Displays an add or edit task screen.
 */
public class AddEditTaskActivity extends AppCompatActivity implements AddEditTaskNavigator {

    public static final int REQUEST_CODE = 1;

    public static final int ADD_EDIT_RESULT_OK = RESULT_FIRST_USER + 1;

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onTaskSaved() {
        setResult(ADD_EDIT_RESULT_OK);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addtask_act);

        // Set up the toolbar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        AddEditTaskFragment addEditTaskFragment =
                FragmentProviders.of(this).get(
                        R.id.contentFrame,
                        () -> {
                            AddEditTaskFragment fragment = new AddEditTaskFragment();
                            Bundle bundle = new Bundle();
                            bundle.putString(
                                    ARGUMENT_EDIT_TASK_ID,
                                    getIntent().getStringExtra(ARGUMENT_EDIT_TASK_ID));
                            return fragment;
                        });

        AddEditTaskViewModel viewModel =
                ViewModelProviders.of(this).get(AddEditTaskViewModel.class);

        // Link View and ViewModel
        addEditTaskFragment.setViewModel(viewModel);

        viewModel.setNavigator(this);
    }
}
