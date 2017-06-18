package io.demoapp.expensive.data.source;

import org.openyolo.protocol.AuthenticationMethod;
import org.openyolo.protocol.Credential;

public interface User {
    String getEmail();
    AuthenticationMethod getPrimaryAuthMethod();
}
