package com.emansapplication.emanvirtualjoystick.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.emansapplication.emanvirtualjoystick.R;
import com.emansapplication.emanvirtualjoystick.callbacks.ServerInfoCallback;
import com.emansapplication.emanvirtualjoystick.callbacks.ServerInfoUpdateListener;
import com.emansapplication.emanvirtualjoystick.views.ServerAddressEditText;

public class SettingsActivity extends BaseActivity implements ServerInfoCallback, ServerInfoUpdateListener {

    private ServerAddressEditText addressInput;
    private EditText portInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        addressInput = findViewById(R.id.input_address);
        portInput = findViewById(R.id.input_port);

        Button btn_save = findViewById(R.id.btn_save);
        btn_save.setOnClickListener(v -> {
            setLayoutEnabled(false);

            String address = addressInput.length() > 0 ? addressInput.getText().toString() : null;

            Integer port = null;
            if (portInput.length() > 0) {
                try {
                    port = Integer.parseInt(portInput.getText().toString());
                } catch (NumberFormatException ex) {
                    ex.printStackTrace();
                }
            }

            getApplicationContext().getSettingsManager().setServerConnectionInfo(this, address, port);
        });


        getApplicationContext().getSettingsManager().getServerConnectionInfo(this);
    }

    private void setLayoutEnabled(boolean enabled) {
        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.setEnabled(enabled);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    @AnyThread
    @Override
    public void onFailedToRetrieveInfo(Throwable ex) {
        ex.printStackTrace();
        runOnUiThread(() -> {
            Toast.makeText(this, R.string.cannot_load_config, Toast.LENGTH_LONG).show();
            setLayoutEnabled(true);
        });
    }

    @AnyThread
    @Override
    public void onInfoAvailable(@Nullable String serverAddress, @Nullable Integer port) {
        runOnUiThread(() -> {
            if (serverAddress != null) {
                addressInput.setText(serverAddress);
            }
            if (port != null) {
                portInput.setText(String.valueOf(port));
            }
            setLayoutEnabled(true);
        });

    }

    @Override
    public void onFailedToUpdateInfo(Throwable ex) {
        showAlertDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.cannot_set_config)
                .setIcon(R.drawable.baseline_error_outline)
                .create());
        setLayoutEnabled(true);
    }

    @Override
    public void onInfoUpdated() {
        setResult(RESULT_OK);
        finish();
    }
}