package com.emansapplication.emanvirtualjoystick.views;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.emansapplication.emanvirtualjoystick.R;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

/**
 * EditText that allows to switch between IPv4-only and any-format input mode.
 */
public class ServerAddressEditText extends LinearLayout {

    private final EditText addressInput;
    private final TextInputLayout layout;
    private boolean ipv4_only = true;


    public ServerAddressEditText(Context context) {
        this(context, null, 0);
    }

    public ServerAddressEditText(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ServerAddressEditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        View.inflate(context, R.layout.edit_text_address, this);

        addressInput = findViewById(R.id.edit_text);
        addressInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && ipv4_only && length() > 0) {
                if (!Patterns.IP_ADDRESS.matcher(getText().toString()).matches()) {
                    addressInput.setError(context.getString(R.string.invalid_ip));
                }
            }
        });

        layout = findViewById(R.id.input_layout);
        layout.setEndIconOnClickListener(v -> {
            ipv4_only = !ipv4_only;
            updateInputType();
        });

        updateInputType();

    }

    private void updateInputType() {
        if (ipv4_only) {
            addressInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            addressInput.setKeyListener(DigitsKeyListener.getInstance("0123456789."));

            layout.setEndIconDrawable(R.drawable.baseline_public);
            layout.setEndIconTintList(ContextCompat.getColorStateList(getContext(), R.color.tint_toggle_on));
            layout.setEndIconContentDescription(R.string.toggle_hostname);
        } else {
            addressInput.setInputType(InputType.TYPE_CLASS_TEXT);

            layout.setEndIconDrawable(R.drawable.baseline_public_off);
            layout.setEndIconTintList(ContextCompat.getColorStateList(getContext(), R.color.tint_toggle_off));
            layout.setEndIconContentDescription(R.string.toggle_ip_only);
        }
    }

    public int length() {
        return addressInput.length();
    }

    public Editable getText() {
        return addressInput.getText();
    }

    public void setText(CharSequence text) {
        ipv4_only = ipv4_only && Pattern.compile("^[0-9.]*$").matcher(text.toString()).matches();
        updateInputType();
        addressInput.setText(text);
    }

}
