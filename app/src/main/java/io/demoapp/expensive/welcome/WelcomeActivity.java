package io.demoapp.expensive.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import io.demoapp.expensive.Injection;
import io.demoapp.expensive.R;
import io.demoapp.expensive.data.source.UsersDataSource;
import io.demoapp.expensive.login.LoginActivity;
import io.demoapp.expensive.tasks.TasksActivity;

public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UsersDataSource users = Injection.providerUsersDataSource(getApplication());
        if (users.getCurrentUser() != null) {
            Log.i(TAG, "Already authenticated");
            startActivity(new Intent(this, TasksActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.welcome_act);

        findViewById(R.id.sign_in).setOnClickListener((view) -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
