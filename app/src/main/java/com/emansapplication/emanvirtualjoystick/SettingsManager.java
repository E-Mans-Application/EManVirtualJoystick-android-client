package com.emansapplication.emanvirtualjoystick;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import com.emansapplication.emanvirtualjoystick.callbacks.ServerInfoCallback;
import com.emansapplication.emanvirtualjoystick.callbacks.ServerInfoUpdateListener;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

public class SettingsManager implements Disposable {

    private static final Preferences.Key<String> SERVER_ADDRESS = PreferencesKeys.stringKey("server_address");
    private static final Preferences.Key<Integer> SERVER_PORT = PreferencesKeys.intKey("server_port");

    private final RxDataStore<Preferences> dataStore;

    SettingsManager(@NonNull Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(context, "settings").build();
    }

    public void getServerConnectionInfo(@NonNull ServerInfoCallback callback) {
        @SuppressLint("UnsafeOptInUsageWarning")
        Disposable task = dataStore.data().map(prefs -> {
            String address = prefs.get(SERVER_ADDRESS);
            Integer port = prefs.get(SERVER_PORT);
            return new Object[]{address, port};
        }).subscribe(arr -> callback.onInfoAvailable((String) arr[0], (Integer) arr[1]), callback::onFailedToRetrieveInfo);

        callback.registerTask(task);
    }

    public void setServerConnectionInfo(@NonNull ServerInfoUpdateListener listener, @Nullable String serverAddress, @Nullable Integer port) {
        Disposable task = dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(SERVER_ADDRESS, serverAddress);
            mutablePreferences.set(SERVER_PORT, port);
            return Single.just(mutablePreferences);
        }).subscribe(_pref -> listener.onInfoUpdated(), listener::onFailedToUpdateInfo);

        listener.registerTask(task);
    }

    @Override
    public void dispose() {
        dataStore.dispose();
    }

    @Override
    public boolean isDisposed() {
        return dataStore.isDisposed();
    }
}
