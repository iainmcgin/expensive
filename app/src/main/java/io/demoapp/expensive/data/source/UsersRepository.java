package io.demoapp.expensive.data.source;

import android.app.Application;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.common.collect.ImmutableSortedSet;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.ProviderQueryResult;

import org.json.JSONObject;
import org.openyolo.protocol.AuthenticationMethod;
import org.openyolo.protocol.AuthenticationMethods;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.demoapp.expensive.util.FederatedIdentityProviders;
import io.demoapp.expensive.util.FederatedIdentityProviders.FederatedIdentityProvider;
import io.demoapp.expensive.util.IdTokenUtil;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class UsersRepository implements UsersDataSource {

    private static final String TAG = "UsersRepository";

    @NonNull
    public static UsersRepository getInstance(Application application) {
        return new UsersRepository(application);
    }

    private final Application mApplication;
    private FirebaseAuth mFirebaseAuth;
    private Retrofit mRetrofit;
    private CustomTokenGenerator mCustomTokenGenerator;

    private UsersRepository(Application application) {
        mApplication = application;
        mFirebaseAuth = FirebaseAuth.getInstance();
    }

    private FirebaseAuth getFirebaseAuth() {
        if (mFirebaseAuth == null) {
            mFirebaseAuth = FirebaseAuth.getInstance();
        }

        return mFirebaseAuth;
    }

    private CustomTokenGenerator getCustomTokenGenerator() {
        if (mRetrofit == null) {
            mRetrofit = new Retrofit.Builder()
                    .baseUrl("https://us-central1-expensive-167322.cloudfunctions.net")
                    .build();
            mCustomTokenGenerator = mRetrofit.create(CustomTokenGenerator.class);
        }

        return mCustomTokenGenerator;
    }

    @Override
    public User getCurrentUser() {
        if (getFirebaseAuth().getCurrentUser() != null) {
            return new FirebaseUserWrapper(getFirebaseAuth().getCurrentUser());
        }

        return null;
    }

    @Override
    public void signOut() {
        getFirebaseAuth().signOut();
    }

    @Override
    public SortedSet<AuthenticationMethod> findExistingAccount(String email) {
        try {
            Log.i(TAG, "Looking for existing account for " + email + " in firebase");
            ProviderQueryResult queryResult =
                    blockOnTask(getFirebaseAuth().fetchProvidersForEmail(email));
            if (queryResult.getProviders() == null || queryResult.getProviders().isEmpty()) {
                Log.i(TAG, "No existing account for " + email);
                return ImmutableSortedSet.of();
            }

            Log.i(TAG, "Existing account for " + email + ", enumerating auth methods");
            SortedSet<AuthenticationMethod> authMethods = new TreeSet<>();
            for (String provider : queryResult.getProviders()) {
                if (GoogleAuthProvider.PROVIDER_ID.equals(provider)) {
                    authMethods.add(AuthenticationMethods.GOOGLE);
                } else if (EmailAuthProvider.PROVIDER_ID.equals(provider)) {
                    authMethods.add(AuthenticationMethods.EMAIL);
                }
            }

            return authMethods;
        } catch (Exception e) {
            return ImmutableSortedSet.of();
        }
    }

    @Override
    public boolean createPasswordAccount(String email, String password) {
        try {
            Log.i(TAG, "Attempting to create password account in Firebase");
            AuthResult authResult =
                    blockOnTask(getFirebaseAuth().createUserWithEmailAndPassword(email, password));
            return authResult.getUser() != null;
        } catch (Exception ex) {
            Log.w(TAG, "Password account creation failed", ex);
            return false;
        }
    }

    @Override
    public boolean authWithIdToken(String idToken) {
        String issuer = IdTokenUtil.extractIssuer(idToken);
        if (issuer == null) {
            Log.i(TAG, "Issuer could not be determined for ID token");
            return false;
        }

        FederatedIdentityProvider provider = FederatedIdentityProviders.getByIssuer(issuer);
        if (provider == null) {
            Log.i(TAG, "Unknown issuer: " + issuer);
            return false;
        }

        // we use our backend service to validate the ID token and provide a firebase custom token
        Log.i(TAG, "Asking for backend validation of ID token");
        Call<ResponseBody> tokenCall = getCustomTokenGenerator().generateToken(
                new GenerateTokenRequest(
                        idToken,
                        provider.issuer())
                .toRequestBody());

        try {
            Response<ResponseBody> tokenResponse = tokenCall.execute();
            if (tokenResponse.isSuccessful()) {
                Log.i(TAG, "Backend validated ID token, attempting auth");
                GenerateTokenResponse responseData =
                        GenerateTokenResponse.fromResponseBody(tokenResponse.body());
                if (responseData == null) {
                    return false;
                }

                AuthResult authResult = blockOnTask(
                        getFirebaseAuth().signInWithCustomToken(responseData.token));
                return authResult.getUser() != null;
            } else {
                Log.i(TAG, "Backend refused ID token");
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean authWithPassword(String email, String password) {
        try {
            Log.i(TAG, "Attempting to authenticate with password account in Firebase");
            AuthResult result = blockOnTask(
                    getFirebaseAuth().signInWithEmailAndPassword(email, password));

            return result.getUser() != null;
        } catch (Exception ex) {
            Log.w(TAG, "Password creation failed", ex);
            return false;
        }
    }

    private class FirebaseUserWrapper implements User {

        private final FirebaseUser mUser;

        FirebaseUserWrapper(FirebaseUser user) {
            mUser = user;
        }

        @Override
        public String getEmail() {
            return mUser.getEmail();
        }

        @Override
        public AuthenticationMethod getPrimaryAuthMethod() {
            // TODO: translate firebase provider ID to auth method
            return AuthenticationMethods.EMAIL;
        }
    }

    private <T> T blockOnTask(Task<T> task) throws Exception {
        final AtomicReference<T> resultRef = new AtomicReference<>();
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        final CountDownLatch completeLatch = new CountDownLatch(1);

        task.addOnCompleteListener((result) -> {
            if (result.getException() != null) {
                Log.i(TAG, "Task failed");
                exceptionRef.set(result.getException());
            } else {
                Log.i(TAG, "Task complete");
                resultRef.set(result.getResult());
            }
            completeLatch.countDown();
        });

        if (!completeLatch.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }

        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
        } else {
            return resultRef.get();
        }
    }

    public interface CustomTokenGenerator {
        @POST("/generateToken")
        Call<ResponseBody> generateToken(@Body RequestBody requestBody);
    }

    public static class GenerateTokenRequest {
        public final String jwt;
        public final String provider;

        public GenerateTokenRequest(
                String jwt,
                String provider) {
            this.jwt = jwt;
            this.provider = provider;
        }

        public RequestBody toRequestBody() {
            try {
                JSONObject jsonBody = new JSONObject()
                        .put("jwt", jwt)
                        .put("provider", provider);

                return RequestBody.create(
                        MediaType.parse("application/json"), jsonBody.toString());
            } catch (Exception ex) {
                throw new IllegalStateException("cannot format request");
            }
        }
    }

    public static class GenerateTokenResponse {
        public final int statusCode;
        public final String token;

        public static GenerateTokenResponse fromResponseBody(ResponseBody body) {
            try {
                String bodyString = body.string();
                JSONObject bodyJson = new JSONObject(bodyString);
                return new GenerateTokenResponse(
                        bodyJson.getInt("code"),
                        bodyJson.getString("token"));
            } catch (Exception ex) {
                Log.w(TAG, "Failed to parse response body from backend");
                return null;
            }
        }

        public GenerateTokenResponse(int statusCode, String token) {
            this.statusCode = statusCode;
            this.token = token;
        }
    }
}