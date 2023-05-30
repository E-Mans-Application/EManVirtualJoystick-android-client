package com.emansapplication.emanvirtualjoystick.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.emansapplication.emanvirtualjoystick.ApplicationEvents;
import com.emansapplication.emanvirtualjoystick.callbacks.BaseCallback;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public abstract class BaseActivity extends AppCompatActivity implements BaseCallback {

    private final List<AlertDialog> shownAlertDialogs = new ArrayList<>();

    /// Remaining tasks are cancelled when the activity stops.
    private CompositeDisposable activitySensitiveTasks;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activitySensitiveTasks = new CompositeDisposable();
    }

    @Override
    protected void onStop() {
        this.dismissAllDialogs();
        activitySensitiveTasks.clear();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activitySensitiveTasks.dispose();
        super.onDestroy();
    }

    @Override
    public ApplicationEvents getApplicationContext() {
        return (ApplicationEvents) super.getApplicationContext();
    }

    @Override
    public void registerTask(Disposable task) {
        activitySensitiveTasks.add(task);
    }

    protected synchronized final void showAlertDialog(final AlertDialog alertDialog) {
        this.shownAlertDialogs.add(alertDialog);
        alertDialog.show();
    }

    private synchronized void dismissAllDialogs() {
        for (final AlertDialog alertDialog : this.shownAlertDialogs) {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
        }
        this.shownAlertDialogs.clear();
    }

}
