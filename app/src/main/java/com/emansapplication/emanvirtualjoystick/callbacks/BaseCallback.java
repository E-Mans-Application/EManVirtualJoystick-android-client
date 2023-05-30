package com.emansapplication.emanvirtualjoystick.callbacks;

import androidx.annotation.AnyThread;

import io.reactivex.rxjava3.disposables.Disposable;

public interface BaseCallback {

    void registerTask(Disposable task);

}
