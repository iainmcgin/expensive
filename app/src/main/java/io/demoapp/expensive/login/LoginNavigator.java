package io.demoapp.expensive.login;

import android.content.Intent;
import android.support.annotation.AnyThread;

import net.openid.appauth.AuthorizationRequest;

interface LoginNavigator {

    @AnyThread
    void startRetrieve(Intent retrieveIntent);

    @AnyThread
    void startHint(Intent hintIntent);

    @AnyThread
    void authComplete();

    @AnyThread
    void startSave(Intent saveIntent);

    @AnyThread
    void startFederatedAuth(AuthorizationRequest authRequest);
}
