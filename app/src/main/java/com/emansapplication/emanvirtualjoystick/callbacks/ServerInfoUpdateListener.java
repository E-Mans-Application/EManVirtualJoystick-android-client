package com.emansapplication.emanvirtualjoystick.callbacks;

import androidx.annotation.AnyThread;

public interface ServerInfoUpdateListener extends BaseCallback {

    @AnyThread
    void onFailedToUpdateInfo(Throwable ex);

    @AnyThread
    void onInfoUpdated();

}
