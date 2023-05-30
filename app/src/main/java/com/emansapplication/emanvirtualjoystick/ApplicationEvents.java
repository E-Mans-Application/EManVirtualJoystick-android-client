package com.emansapplication.emanvirtualjoystick;

import android.app.Application;

public class ApplicationEvents extends Application {

    public static final String LOG_TAG = "EManVirtualJoystick";

    private SettingsManager settingsManager;

    @Override
    public void onCreate() {
        super.onCreate();

        this.settingsManager = new SettingsManager(this);
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        settingsManager.dispose();
    }
}
