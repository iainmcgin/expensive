package io.demoapp.expensive.data.source;

import android.support.annotation.WorkerThread;

import org.openyolo.protocol.AuthenticationMethod;

import java.util.SortedSet;

@WorkerThread
public interface UsersDataSource {

    User getCurrentUser();

    SortedSet<AuthenticationMethod> findExistingAccount(String email);

    boolean createPasswordAccount(String email, String password);

    boolean authWithPassword(String email, String password);

    boolean authWithIdToken(String idToken);

    void signOut();
}
