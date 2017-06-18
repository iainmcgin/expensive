package io.demoapp.expensive.login;

import android.app.Application;
import android.content.Intent;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.os.Looper;
import android.support.annotation.Keep;
import android.support.annotation.MainThread;
import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.view.View;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.TokenResponse;

import org.openyolo.api.CredentialClient;
import org.openyolo.protocol.AuthenticationDomain;
import org.openyolo.protocol.AuthenticationMethod;
import org.openyolo.protocol.AuthenticationMethods;
import org.openyolo.protocol.Credential;
import org.openyolo.protocol.CredentialRetrieveRequest;
import org.openyolo.protocol.CredentialRetrieveResult;
import org.openyolo.protocol.CredentialSaveRequest;
import org.openyolo.protocol.CredentialSaveResult;
import org.openyolo.protocol.Hint;
import org.openyolo.protocol.HintRetrieveRequest;
import org.openyolo.protocol.HintRetrieveResult;
import org.openyolo.protocol.TokenRequestInfo;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.demoapp.expensive.ExpensiveApplication;
import io.demoapp.expensive.Injection;
import io.demoapp.expensive.R;
import io.demoapp.expensive.data.source.UsersDataSource;
import io.demoapp.expensive.util.FederatedIdentityProviders;
import io.demoapp.expensive.util.FederatedIdentityProviders.FederatedIdentityProvider;
import io.demoapp.expensive.util.ObservableViewModel;

@WorkerThread
public class LoginViewModel extends ObservableViewModel {

    private static final String TAG = "LoginViewModel";

    // values which are pushed to the UI
    public final ObservableBoolean loading = new ObservableBoolean(true);
    public final ObservableField<String> loadingLabel = new ObservableField<>();
    public final ObservableField<String> actionPrompt = new ObservableField<>();
    public final ObservableBoolean showEmailPrompt = new ObservableBoolean(false);
    public final ObservableBoolean showPasswordPrompt = new ObservableBoolean(false);
    public final ObservableBoolean errorShown = new ObservableBoolean(false);

    // values which are pushed to and pulled from the UI
    public final ObservableField<String> email = new ObservableField<>("");
    public final ObservableField<String> password = new ObservableField<>("");

    private final CredentialClient mCredentialClient;
    private final UsersDataSource mUsers;

    private LoginNavigator mNavigator;
    private AtomicBoolean firstLoad = new AtomicBoolean(true);

    void setNavigator(LoginNavigator navigator) {
        mNavigator = navigator;
    }

    @Keep
    public LoginViewModel(Application application) {
        super(application);
        Log.i(TAG, "creating view model");
        mCredentialClient = CredentialClient.getApplicationBoundInstance(application);
        mUsers = Injection.providerUsersDataSource(application);
    }

    @Override
    protected void onCleared() {
        Log.i(TAG, "tearing down");
        mNavigator = null;
    }

    void start() {
        if (!firstLoad.compareAndSet(true, false)) {
            return;
        }

        if (mUsers.getCurrentUser() != null) {
            Log.i(TAG, "already signed in");
            mNavigator.authComplete();
            return;
        }

        setLoading(R.string.loading_existing_account);
        CredentialClient client =
                CredentialClient.getApplicationBoundInstance(getApplication());


        // fetchAuthHint();
        Log.i(TAG, "starting OpenYOLO retrieve request");
        client.retrieve(
                createCredentialRetrieveRequest(),
                (queryResponse, queryError) -> {
                    if (queryResponse == null || queryResponse.getRetrieveIntent() == null) {
                        Log.i(TAG, "no OpenYOLO providers for retrieve");
                        fetchAuthHint();
                        return;
                    }

                    Log.i(TAG, "sending OpenYOLO retrieve intent");
                    mNavigator.startRetrieve(queryResponse.getRetrieveIntent());
                });
    }

    @MainThread
    public void signInButtonClicked(View view) {
        Log.i(TAG, "user clicked sign in");
        final String userEnteredEmail = email.get();
        final String userEnteredPassword = password.get();

        ((ExpensiveApplication)getApplication()).getExecutorService().execute(
                () -> handleManualSignIn(userEnteredEmail, userEnteredPassword));
    }

    private void handleManualSignIn(String email, String password) {
        Log.i(TAG, "checking for an existing account for " + email);
        setLoading(R.string.loading_existing_account);
        SortedSet<AuthenticationMethod> existingAccountAuthMethods =
                mUsers.findExistingAccount(email);

        if (existingAccountAuthMethods.isEmpty()) {
            Log.i(TAG, "No existing account found");
            setLoaded();

            FederatedIdentityProvider providerForEmail =
                    FederatedIdentityProviders.findProviderForEmail(email);

            if (providerForEmail == null) {
                if (!password.isEmpty()) {
                    setLoading(R.string.loading_authenticating);
                    boolean authenticated = mUsers.createPasswordAccount(email, password);
                    setLoaded();

                    if (authenticated) {
                        Log.i(TAG, "Successfully authenticated with manually entered password");
                        trySaveCredential(
                                new Credential.Builder(
                                        email,
                                        AuthenticationMethods.EMAIL,
                                        AuthenticationDomain.getSelfAuthDomain(getApplication()))
                                        .setPassword(password)
                                        .build(),
                                        false);
                        return;
                    } else {
                        setErrorShown(true);
                    }
                }
                askForNewPassword();
            } else {
                setLoading(R.string.loading_authenticating);
                mNavigator.startFederatedAuth(
                        FederatedIdentityProviders.createAuthorizationRequest(
                                providerForEmail, email));
            }

            return;
        }

        Log.i(TAG, "existing account found");
        setLoaded();
        if (existingAccountAuthMethods.contains(AuthenticationMethods.GOOGLE)) {
            Log.i(TAG, "Attempting to authenticate with Google");
            setLoading(R.string.loading_authenticating);
            mNavigator.startFederatedAuth(
                    FederatedIdentityProviders.createAuthorizationRequest(
                            FederatedIdentityProviders.GOOGLE, email));
        } else if (existingAccountAuthMethods.contains(AuthenticationMethods.EMAIL)) {
            if (password.isEmpty()) {
                askForExistingPassword();
                return;
            }

            setLoading(R.string.loading_authenticating);
            if (mUsers.authWithPassword(email, password)) {
                trySaveCredential(
                        new Credential.Builder(
                                email,
                                AuthenticationMethods.EMAIL,
                                AuthenticationDomain.getSelfAuthDomain(getApplication()))
                                .setPassword(password)
                                .build(),
                                false);
            } else {
                askForPasswordRetry();
            }
        }
    }

    void handleRetrieveResult(Intent data) {
        CredentialRetrieveResult result = mCredentialClient.getCredentialRetrieveResult(data);
        if (result.getResultCode() != CredentialRetrieveResult.CODE_CREDENTIAL_SELECTED
                || result.getCredential() == null) {
            Log.i(TAG, "No credential returned for credential request");
            fetchAuthHint();
            return;
        }

        Credential credential = result.getCredential();
        Log.i(TAG, "Returned credential: " + credential.getIdentifier());
        setLoading(R.string.loading_existing_account);
        SortedSet<AuthenticationMethod> authMethods =
                mUsers.findExistingAccount(credential.getIdentifier());
        setLoaded();
        if (authMethods.isEmpty()) {
            Log.i(TAG, "No existing account for returned credential");
        }

        Log.i(TAG, "Attempting authentication to existing account with returned credential");
        tryAuthWithCredential(credential);
    }

    void handleHintResult(Intent data) {
        HintRetrieveResult result = mCredentialClient.getHintRetrieveResult(data);
        if (result.getResultCode() != HintRetrieveResult.CODE_HINT_SELECTED
                || result.getHint() == null) {
            Log.i(TAG, "No hint returned for hint request");
            setLoaded();
            updateForm(R.string.enter_email, true, false);
            return;
        }

        Hint hint = result.getHint();
        Log.i(TAG, "Hint returned: " + hint.getIdentifier());
        SortedSet<AuthenticationMethod> authMethods =
                mUsers.findExistingAccount(hint.getIdentifier());
        if (authMethods.isEmpty()) {
            Log.i(TAG, "No existing account for hint, attempting to create");
            tryCreateAccountFromHint(hint);
        } else {
            Log.i(TAG, "Existing account for hint, attempting to authenticate");
            tryAuthWithCredential(credentialBuilderFromHint(hint).build());
        }
    }

    void handleSaveResult(Intent data) {
        CredentialSaveResult result = mCredentialClient.getCredentialSaveResult(data);
        Log.i(TAG, "Save result: " + result.getResultCode());
        // we don't actually care what the result of the save was, other than to log it.
        mNavigator.authComplete();
    }

    private void fetchAuthHint() {
        Intent hintIntent = mCredentialClient.getHintRetrieveIntent(createHintRetrieveRequest());
        if (hintIntent == null) {
            Log.i(TAG, "No OpenYOLO providers for hint");
            askForEmail();
        } else {
            Log.i(TAG, "Sending hint retrieve");
            mNavigator.startHint(hintIntent);
        }
    }

    void handleFederatedAuthResult(
            TokenResponse response,
            AuthorizationException exception) {
        if (exception != null) {
            Log.i(TAG, "Failed to retrieve ID token from AppAuth flow");
            setErrorShown(true);
            askForEmail();
            return;
        }

        Log.i(TAG, "Attempting to create account from AppAuth ID token");
        if (mUsers.authWithIdToken(response.idToken)) {
            Log.i(TAG, "user authenticated with AppAuth ID token");
            // TODO: save the credential
            mNavigator.authComplete();
            return;
        }

        Log.i(TAG, "AppAuth ID token failed to authenticate user");
        setErrorShown(true);
        askForEmail();
    }

    /* *********************************************************************************************
     * UI STATE MANAGEMENT
     **********************************************************************************************/

    private void setLoading(@StringRes int labelRes) {
        loadingLabel.set(getApplication().getString(labelRes));
        loading.set(true);
    }

    private void setLoaded() {
        loading.set(false);
        loadingLabel.set("");
    }

    private void askForEmail() {
        setLoaded();
        updateForm(R.string.enter_email, true, false);
    }

    private void askForNewPassword() {
        setLoaded();
        updateForm(R.string.choose_password, true, true);
    }

    private void askForExistingPassword() {
        setLoaded();
        updateForm(R.string.enter_password, true, true);
    }

    private void askForPasswordRetry() {
        setLoaded();
        updateForm(R.string.enter_password_again, true, true);
    }

    private void updateForm(
            @StringRes int actionPromptRes,
            boolean showEmail,
            boolean showPassword) {
        actionPrompt.set(getApplication().getResources().getString(actionPromptRes));
        showEmailPrompt.set(showEmail);
        showPasswordPrompt.set(showPassword);
    }

    private void setErrorShown(boolean show) {
        errorShown.set(show);
    }

    /* *********************************************************************************************
     * ACCOUNT CREATION HANDLING
     **********************************************************************************************/

    private void tryCreateAccountFromHint(Hint hint) {
        Credential newCredential = null;
        FederatedIdentityProvider providerForEmail =
                FederatedIdentityProviders.findProviderForEmail(hint.getIdentifier());

        setLoading(R.string.loading_authenticating);
        if (hint.getIdToken() != null) {
            Log.i(TAG, "Attempting to create account from hint ID token");
            if (mUsers.authWithIdToken(
                    hint.getIdToken())) {
                Log.i(TAG, "Hint ID token account creation successful");
                newCredential = credentialBuilderFromHint(hint).build();
            }
        }

        if (newCredential == null &&
                (isSupportedFederatedAuthMethod(hint.getAuthenticationMethod()) ||
                providerForEmail != null)) {
            Log.i(TAG, "Attempting federated auth based on domain match");
            setLoading(R.string.loading_authenticating);
            mNavigator.startFederatedAuth(
                    FederatedIdentityProviders.createAuthorizationRequest(
                            providerForEmail,
                            hint.getIdentifier()));
            return;
        }

        if (newCredential == null
                && isEmailCredential(hint.getAuthenticationMethod())
                && hint.getGeneratedPassword() != null) {
            Log.i(TAG, "Attempting to create account from generated password hint");
            if (mUsers.createPasswordAccount(
                    hint.getIdentifier(),
                    hint.getGeneratedPassword())) {
                Log.i(TAG, "Generated password account creation succeeded");
                newCredential = credentialBuilderFromHint(hint)
                        .setPassword(hint.getGeneratedPassword())
                        .build();
            }
        }

        setLoaded();
        if (newCredential != null) {
            Log.i(TAG, "Attempting to save new credential from hint");
            trySaveCredential(newCredential, false);
        } else {
            Log.i(TAG, "Asking for manual password account creation");
            email.set(hint.getIdentifier());
            askForNewPassword();
        }
    }

    /* *********************************************************************************************
     * EXISTING ACCOUNT AUTHENTICATION HANDLING
     **********************************************************************************************/

    private void tryAuthWithCredential(Credential credential) {
        // ID token auth is the most streamlined, so prioritize this if available.
        boolean authenticated = false;
        if (credential.getIdToken() != null) {
            Log.i(TAG, "Attempting to authenticate with retrieved ID token");
            authenticated = mUsers.authWithIdToken(credential.getIdToken());
        } else if (isEmailCredential(credential.getAuthenticationMethod())
                && credential.getPassword() != null) {
            Log.i(TAG, "Attempting to authenticate with retrieved password");
            authenticated = mUsers.authWithPassword(
                    credential.getIdentifier(),
                    credential.getPassword());
        } else if (isSupportedFederatedAuthMethod(credential.getAuthenticationMethod())) {
            Log.i(TAG, "Attempting to authenticate with federation provider");
            mNavigator.startFederatedAuth(
                    FederatedIdentityProviders.createAuthorizationRequest(
                            credential.getAuthenticationMethod(),
                            credential.getIdentifier()));
            return;
        }

        if (authenticated) {
            Log.i(TAG, "Authentication success, attempting to save credential");
            trySaveCredential(credential, true);
        } else {
            Log.i(TAG, "Authentication failed");
        }
    }

    /* *********************************************************************************************
     * OPENYOLO REQUEST UTILS
     **********************************************************************************************/

    private Credential.Builder credentialBuilderFromHint(Hint hint) {
        return new Credential.Builder(
                hint.getIdentifier(),
                hint.getAuthenticationMethod(),
                AuthenticationDomain.getSelfAuthDomain(getApplication()))
                .setDisplayName(hint.getDisplayName())
                .setDisplayPicture(hint.getDisplayPictureUri());
    }

    private void trySaveCredential(Credential credential, boolean update) {
        CredentialSaveRequest saveRequest = new CredentialSaveRequest.Builder(credential)
                .build();
        Intent saveIntent = mCredentialClient.getSaveIntent(saveRequest);
        if (saveIntent != null) {
            // bug workaround for demo - if the provider is 1Password, don't try and re-save a
            // credential that was previously known. 1Password currently saves a duplicate.
            if (update && saveIntent.getComponent().getPackageName().equals("com.agilebits.onepassword")) {
                mNavigator.authComplete();
            } else {
                mNavigator.startSave(saveIntent);
            }
        } else {
            Log.i(TAG, "No save provider available");
            mNavigator.authComplete();
        }
    }

    private boolean isEmailCredential(AuthenticationMethod authMethod) {
        // bug workaround: 1Password is always saving credentials with the USER_NAME type right now,
        // so treat that as equivalent to email.
        return AuthenticationMethods.EMAIL.equals(authMethod)
                || AuthenticationMethods.USER_NAME.equals(authMethod);
    }

    private boolean isSupportedFederatedAuthMethod(AuthenticationMethod authMethod) {
        return AuthenticationMethods.GOOGLE.equals(authMethod);
    }

    private HintRetrieveRequest createHintRetrieveRequest() {
        return new HintRetrieveRequest.Builder(
                getSupportedAuthenticationMethods())
                .setTokenProviders(getSupportedTokenProviders())
                .build();
    }

    private CredentialRetrieveRequest createCredentialRetrieveRequest() {
        return new CredentialRetrieveRequest.Builder(
                getSupportedAuthenticationMethods())
                .setTokenProviders(getSupportedTokenProviders())
                .build();
    }

    private Set<AuthenticationMethod> getSupportedAuthenticationMethods() {
        return ImmutableSet.of(
                AuthenticationMethods.EMAIL,
                AuthenticationMethods.GOOGLE);
    }

    private Map<String, TokenRequestInfo> getSupportedTokenProviders() {
        return ImmutableMap.of(
                "https://accounts.google.com",
                new TokenRequestInfo.Builder()
                        .setClientId(FederatedIdentityProviders.GOOGLE.serverClientId())
                        .build());
    }
}
