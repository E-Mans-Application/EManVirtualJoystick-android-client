package com.emansapplication.emanvirtualjoystick.activities;

import static com.emansapplication.emanvirtualjoystick.ApplicationEvents.LOG_TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.emansapplication.emanvirtualjoystick.BuildConfig;
import com.emansapplication.emanvirtualjoystick.R;
import com.emansapplication.emanvirtualjoystick.SocketDispatcher;
import com.emansapplication.emanvirtualjoystick.callbacks.ServerInfoCallback;
import com.emansapplication.emanvirtualjoystick.views.JoystickView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends BaseActivity implements ServerInfoCallback {

    private static final int STATE_CONNECTING = 0;
    private static final int STATE_READY = 1;
    private static final int STATE_DISPATCH_UNAVAILABLE = 2;

    private static final int MAX_CONNECTION_ATTEMPTS = 3;
    private int connectionAttempts;

    private boolean settingsShown;

    private final ActivityResultLauncher<Intent> launchSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::settingsChanged);

    private SocketDispatcher socket;
    private Timer timer;

    @Nullable
    private String cached_server_address;
    @Nullable
    private Integer cached_server_port;

    private JoystickView leftJoystick;
    private JoystickView rightJoystick;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
        setContentView(R.layout.activity_main);

        settingsShown = false;

        leftJoystick = findViewById(R.id.joystickLeft);
        leftJoystick.setOnStickMoveListener(v -> {
            if (socket != null && socket.isConnected())
                dispatchLeftJoystick();
        });

        rightJoystick = findViewById(R.id.joystickRight);
        rightJoystick.setOnStickMoveListener(v -> {
            if (socket != null && socket.isConnected())
                dispatchRightJoystick();
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        timer = new Timer();
        loadSettings();
    }

    @Override
    protected void onStop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (socket != null) {
            socket.close();
        }
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (socket != null && socket.isConnected())
                socket.dispatchSelectButtonPressed();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (socket != null && socket.isConnected())
                socket.dispatchStartButtonPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (socket != null && socket.isConnected())
                socket.dispatchSelectButtonReleased();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (socket != null && socket.isConnected())
                socket.dispatchStartButtonReleased();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void startRoutine() {
        if (BuildConfig.DEBUG)
            Log.d(LOG_TAG, "Starting a new routine cycle...");
        connectionAttempts = 0;
        routine();
    }

    private void routine() {
        if (cached_server_address == null || cached_server_port == null) {
            setLayoutState(STATE_DISPATCH_UNAVAILABLE);
            return;
        }
        if (socket == null || (!socket.isConnected() && !socket.isConnecting())) {
            connectionAttempts++;
            if (connectionAttempts > MAX_CONNECTION_ATTEMPTS) {
                Toast.makeText(this, R.string.cannot_connect_to_server, Toast.LENGTH_LONG).show();
                setLayoutState(STATE_DISPATCH_UNAVAILABLE);
                return;
            }
            socket = new SocketDispatcher(cached_server_address, cached_server_port);
        }
        if (socket.isConnecting()) {
            setLayoutState(STATE_CONNECTING);
        } else if (socket.isConnected()) {
            setLayoutState(STATE_READY);
            connectionAttempts = 0;
            dispatchLeftJoystick();
            dispatchRightJoystick();
        }
        if (timer == null) {
            Log.e(LOG_TAG, "Illegal state: the timer is null, but it should not. Re-creating the activity...");
            recreate();
            return;
        }
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    routine();
                });
            }
        }, 500);
    }

    private void dispatchLeftJoystick() {
        socket.dispatchLeftJoystickPosition(leftJoystick.getStickX(), leftJoystick.getStickY());
    }

    private void dispatchRightJoystick() {
        socket.dispatchRightJoystickPosition(rightJoystick.getStickX(), rightJoystick.getStickY());
    }

    private void setLayoutState(int state) {
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(state == STATE_CONNECTING ? View.VISIBLE : View.GONE);

        ImageView imageError = findViewById(R.id.imageError);
        imageError.setVisibility(state == STATE_DISPATCH_UNAVAILABLE ? View.VISIBLE : View.GONE);
    }

    private void showSettings() {
        settingsShown = true;
        launchSettings.launch(new Intent(this, SettingsActivity.class));
    }

    private void loadSettings() {
        setLayoutState(STATE_CONNECTING);
        getApplicationContext().getSettingsManager().getServerConnectionInfo(this);
    }

    private void settingsChanged(ActivityResult result) {
        loadSettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            showSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onInfoAvailable(@Nullable String serverAddress, @Nullable Integer port) {
        if (BuildConfig.DEBUG)
            Log.d(LOG_TAG, String.format("Server info loaded: address %s, port %s", serverAddress, port));
        runOnUiThread(() -> {
            this.cached_server_address = serverAddress;
            this.cached_server_port = port;
            if (serverAddress == null || port == null) {
                setLayoutState(STATE_DISPATCH_UNAVAILABLE);
                if (!settingsShown)
                    showSettings();
            } else {
                startRoutine();
            }
        });
    }

    @Override
    public void onFailedToRetrieveInfo(Throwable ex) {
        ex.printStackTrace();
        runOnUiThread(() -> {
            setLayoutState(STATE_DISPATCH_UNAVAILABLE);
            Toast.makeText(this, R.string.cannot_load_config, Toast.LENGTH_LONG).show();
        });
    }
}