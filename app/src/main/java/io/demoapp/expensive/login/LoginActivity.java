package io.demoapp.expensive.login;

import android.app.PendingIntent;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;

import java.util.concurrent.ExecutorService;

import io.demoapp.expensive.ExpensiveApplication;
import io.demoapp.expensive.R;
import io.demoapp.expensive.tasks.TasksActivity;
import io.demoapp.expensive.util.FragmentProviders;

public class LoginActivity extends AppCompatActivity implements LoginNavigator {

    private static final int RC_RETRIEVE = 100;
    private static final int RC_HINT = 101;
    private static final int RC_SAVE = 102;

    private static final int RC_AUTH_RESULT = 200;

    private static String EXTRA_CANCEL = "cancelResponse";

    private LoginViewModel mViewModel;
    private AuthorizationService mAuthService;
    private String TAG = "LoginActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_act);

        LoginFragment loginFragment = FragmentProviders.of(this).get(
                R.id.contentFrame, LoginFragment::new);

        mViewModel = ViewModelProviders.of(this).get(LoginViewModel.class);
        loginFragment.setViewModel(mViewModel);

        mViewModel.setNavigator(this);

        mAuthService = new AuthorizationService(this);

        checkAuthResult(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAuthService.dispose();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "new intent (likely AppAuth response)");
        checkAuthResult(intent);
    }

    private void checkAuthResult(Intent intent) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException exception = AuthorizationException.fromIntent(intent);

        if (exception != null) {
            Log.i(TAG, "Authorization request failed", exception);
            getExecutorService().execute(
                    () -> mViewModel.handleFederatedAuthResult(null, exception));
        } else if (response != null) {
            Log.i(TAG, "Authorization request completed; exchanging code");
            mAuthService.performTokenRequest(
                    response.createTokenExchangeRequest(),
                    (tokenResponse, tokenException) -> {
                        getExecutorService().execute(() -> {
                            mViewModel.handleFederatedAuthResult(tokenResponse, tokenException);
                        });
                    });
        } else if (intent.getBooleanExtra(EXTRA_CANCEL, false)) {
            Log.i(TAG, "User canceled auth flow");
            getExecutorService().execute(
                    () -> mViewModel.handleFederatedAuthResult(
                            null,
                            AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW));
        } else {
            Log.i(TAG, "No auth response to interpret");
        }
    }

    @Override
    public void startRetrieve(@NonNull Intent retrieveIntent) {
        runOnUiThread(() -> startActivityForResult(retrieveIntent, RC_RETRIEVE));
    }

    @Override
    public void startHint(@NonNull Intent hintIntent) {
        runOnUiThread(() -> startActivityForResult(hintIntent, RC_HINT));
    }

    @Override
    public void startSave(@NonNull Intent saveIntent) {
        runOnUiThread(() -> startActivityForResult(saveIntent, RC_SAVE));
    }

    @Override
    public void startFederatedAuth(AuthorizationRequest request) {
        runOnUiThread(() -> {
            Intent selfIntent = new Intent(this, this.getClass());
            selfIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            selfIntent.putExtra(EXTRA_CANCEL, false);
            selfIntent.setAction("AUTH_DONE");

            Intent cancelIntent = new Intent(this, this.getClass());
            cancelIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            cancelIntent.putExtra(EXTRA_CANCEL, true);
            cancelIntent.setAction("AUTH_CANCELED");

            PendingIntent completionPendingIntent = PendingIntent.getActivity(
                    this,
                    RC_AUTH_RESULT,
                    selfIntent,
                    0);

            PendingIntent cancelPendingIntent = PendingIntent.getActivity(
                    this,
                    RC_AUTH_RESULT,
                    cancelIntent,
                    0);

            mAuthService.performAuthorizationRequest(
                    request,
                    completionPendingIntent,
                    cancelPendingIntent);
        });
    }

    @Override
    public void authComplete() {
        startActivity(new Intent(this, TasksActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_RETRIEVE:
                getExecutorService().execute(() -> mViewModel.handleRetrieveResult(data));
                break;
            case RC_HINT:
                getExecutorService().execute(() -> mViewModel.handleHintResult(data));
                break;
            case RC_SAVE:
                getExecutorService().execute(() -> mViewModel.handleSaveResult(data));
        }
    }

    private ExecutorService getExecutorService() {
        return ((ExpensiveApplication) getApplication()).getExecutorService();
    }
}
