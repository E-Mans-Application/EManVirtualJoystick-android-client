package com.emansapplication.emanvirtualjoystick.callbacks;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

public interface ServerInfoCallback extends BaseCallback {
    @AnyThread
    void onInfoAvailable(@Nullable String serverAddress, @Nullable Integer port);

    @AnyThread
    void onFailedToRetrieveInfo(Throwable ex);

}
