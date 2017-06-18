package io.demoapp.expensive.util;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

import org.openyolo.protocol.AuthenticationMethod;
import org.openyolo.protocol.AuthenticationMethods;

import java.util.Map;

public final class FederatedIdentityProviders {

    private static final String TAG = "FederatedIDPs";

    public static final FederatedIdentityProvider GOOGLE = FederatedIdentityProvider.builder()
            .setOpenYoloAuthMethod(AuthenticationMethods.GOOGLE)
            .setIssuer("https://accounts.google.com")
            .setAuthorizationEndpoint("https://accounts.google.com/o/oauth2/v2/auth")
            .setTokenEndpoint("https://www.googleapis.com/oauth2/v4/token")
            .setClientId("173065469067-85s1bkdaf60pusf6gs0iugnukba46m3s.apps.googleusercontent.com")
            .setServerClientId("173065469067-b1f62os2f4q24lakjs79c5jn3k8610q8.apps.googleusercontent.com")
            .setRedirectUri("com.googleusercontent.apps.173065469067-85s1bkdaf60pusf6gs0iugnukba46m3s:/oauth2redirect")
            .setKnownDomains(
                    "gmail.com",
                    "google.com")
            .build();

    // this is our example enterprise IDP, which we fake up using a Ping Federate test instance.
    // email addresses are presented as ...@srsbsns.com, and the email address is stripped when
    // we pass this to Ping as a login hint.
    public static final FederatedIdentityProvider PING = FederatedIdentityProvider.builder()
            .setOpenYoloAuthMethod(new AuthenticationMethod("https://srsbsns.com"))
            .setIssuer("https://token-provider-bc.ping-eng.com:9031")
            .setAuthorizationEndpoint("https://token-provider-bc.ping-eng.com:9031/as/authorization.oauth2")
            .setTokenEndpoint("https://token-provider-bc.ping-eng.com:9031/as/token.oauth2")
            .setClientId("expensive")
            .setRedirectUri("io.demoapp.expensive://oauth2redirect")
            .setKnownDomains("srsbsns.com")
            .setLoginHintRemapper((email) -> email.split("\\@")[0])
            .build();

    public static final Map<AuthenticationMethod, FederatedIdentityProvider> PROVIDERS =
            ImmutableMap.of(
                    GOOGLE.openYoloAuthMethod(), GOOGLE,
                    PING.openYoloAuthMethod(), PING);

    public static final Map<String, FederatedIdentityProvider> PROVIDERS_BY_ISSUER =
            ImmutableMap.of(
                    GOOGLE.issuer(), GOOGLE,
                    PING.issuer(), PING);

    @Nullable
    public static FederatedIdentityProvider getByIssuer(String issuer) {
        return PROVIDERS_BY_ISSUER.get(issuer);
    }

    public static AuthorizationRequest createAuthorizationRequest(
            AuthenticationMethod openYoloAuthMethod,
            String loginHint) {
        return createAuthorizationRequest(PROVIDERS.get(openYoloAuthMethod), loginHint);
    }

    @NonNull
    public static AuthorizationRequest createAuthorizationRequest(
            FederatedIdentityProvider provider,
            String loginHint) {
        return new AuthorizationRequest.Builder(
                createConfig(provider),
                provider.clientId(),
                ResponseTypeValues.CODE,
                provider.redirectUri())
                .setLoginHint(provider.loginHintRemapper().remap(loginHint))
                .setScopes(
                        AuthorizationRequest.Scope.OPENID,
                        AuthorizationRequest.Scope.EMAIL,
                        AuthorizationRequest.Scope.PROFILE)
                .build();
    }

    @Nullable
    public static FederatedIdentityProvider findProviderForEmail(String email) {
        for (FederatedIdentityProvider provider : PROVIDERS.values()) {
            if (provider.knownDomains().contains(extractDomainFromEmail(email))) {
                return provider;
            }
        }

        return null;
    }

    @NonNull
    private static AuthorizationServiceConfiguration createConfig(
            FederatedIdentityProvider provider) {
        return new AuthorizationServiceConfiguration(
                    provider.authorizationEndpoint(),
                    provider.tokenEndpoint(),
                    null);
    }

    private static String extractDomainFromEmail(String email) {
        String[] emailParts = email.split("\\@");
        if (emailParts.length != 2) {
            Log.i(TAG, "Malformed email: " + email);
            return null;
        }
        return emailParts[1];
    }

    @AutoValue
    public static abstract class FederatedIdentityProvider {
        public abstract AuthenticationMethod openYoloAuthMethod();
        public abstract String issuer();
        public abstract Uri authorizationEndpoint();
        public abstract Uri tokenEndpoint();
        public abstract String clientId();
        public abstract String serverClientId();

        public abstract Uri redirectUri();
        public abstract ImmutableSet<String> knownDomains();
        public abstract LoginHintRemapper loginHintRemapper();

        static Builder builder() {
            return new AutoValue_FederatedIdentityProviders_FederatedIdentityProvider.Builder()
                    .setKnownDomains(ImmutableSet.of())
                    .setServerClientId("")
                    .setLoginHintRemapper((email) -> email);
        }

        @AutoValue.Builder
        abstract static class Builder {
            public abstract Builder setOpenYoloAuthMethod(AuthenticationMethod authMethod);
            public abstract Builder setIssuer(String issuer);

            public Builder setAuthorizationEndpoint(String authorizationEndpoint) {
                return this.setAuthorizationEndpoint(Uri.parse(authorizationEndpoint));
            }

            public abstract Builder setAuthorizationEndpoint(Uri authorizationEndpoint);

            public Builder setTokenEndpoint(String tokenEndpoint) {
                return this.setTokenEndpoint(Uri.parse(tokenEndpoint));
            }

            public abstract Builder setTokenEndpoint(Uri tokenEndpoint);
            public abstract Builder setClientId(String clientId);
            public abstract Builder setServerClientId(String serverClientId);

            public Builder setRedirectUri(String redirectUri) {
                return setRedirectUri(Uri.parse(redirectUri));
            }

            public abstract Builder setRedirectUri(Uri redirectUri);

            public Builder setKnownDomains(String... knownDomains) {
                return setKnownDomains(ImmutableSet.copyOf(knownDomains));
            }

            public abstract Builder setKnownDomains(ImmutableSet<String> knownDomains);

            public abstract Builder setLoginHintRemapper(LoginHintRemapper remapper);

            public abstract FederatedIdentityProvider build();
        }
    }

    public interface LoginHintRemapper {
        public String remap(String loginHint);
    }
}
