package com.screenmirror.app;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class RegistrationActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_ADMIN = 200;
    private static final int REQUEST_OVERLAY      = 201;
    private static final int REQUEST_BATTERY      = 202;
    private static final int REQUEST_NOTIF_PERM   = 203;

    private EditText etName, etAge, etPhone;
    private RadioGroup rgGender;
    private Spinner spinnerPayment;
    private Button btnSubmit;
    private SharedPreferences prefs;

    // Track which permission step we're on
    private boolean allPermissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);

        // Start the permission chain — must complete all before proceeding
        checkAllPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check after returning from settings screens
        if (!allPermissionsGranted) {
            checkAllPermissions();
        }
    }

    /**
     * Check all permissions in order. If any is missing, request it and return.
     * Only when ALL are granted do we proceed to registration/main.
     */
    private void checkAllPermissions() {
        // 1. Device Admin
        if (!isDeviceAdminActive()) {
            requestDeviceAdmin();
            return;
        }

        // 2. Battery optimization exclusion
        if (!isBatteryOptimized()) {
            requestBatteryExclusion();
            return;
        }

        // 3. Overlay permission (for OverlayService)
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        // ALL permissions granted!
        allPermissionsGranted = true;
        proceedAfterPermissions();
    }

    private void proceedAfterPermissions() {
        // Already registered? Go straight to MainActivity
        if (prefs.getBoolean("registered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // Show registration form
        setContentView(R.layout.activity_registration);

        etName = findViewById(R.id.et_name);
        etAge = findViewById(R.id.et_age);
        etPhone = findViewById(R.id.et_phone);
        rgGender = findViewById(R.id.rg_gender);
        spinnerPayment = findViewById(R.id.spinner_payment);
        btnSubmit = findViewById(R.id.btn_submit);

        String[] paymentMethods = {"Select Payment Method", "bKash", "Nagad"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, paymentMethods);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerPayment.setAdapter(adapter);

        btnSubmit.setOnClickListener(v -> submit());
    }

    // ─────────────────────────────────────────
    //  Permission checks
    // ─────────────────────────────────────────

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
        return dpm.isAdminActive(admin);
    }

    @android.annotation.SuppressLint("BatteryLife")
    private boolean isBatteryOptimized() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    // ─────────────────────────────────────────
    //  Permission requests
    // ─────────────────────────────────────────

    private void requestDeviceAdmin() {
        Toast.makeText(this, "Device Admin permission is required", Toast.LENGTH_SHORT).show();
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "This app requires device admin permission to function properly.");
        startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
    }

    @android.annotation.SuppressLint("BatteryLife")
    private void requestBatteryExclusion() {
        Toast.makeText(this, "Please disable battery optimization for this app", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_BATTERY);
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    // ─────────────────────────────────────────
    //  Results
    // ─────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // After any permission result, re-check the chain
        // (onResume will also call checkAllPermissions)
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_NOTIF_PERM) {
            // Whether granted or denied, continue the chain
            checkAllPermissions();
        }
    }

    // ─────────────────────────────────────────
    //  Registration submit
    // ─────────────────────────────────────────

    private void submit() {
        String name = etName.getText().toString().trim();
        String age = etAge.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Name is required");
            return;
        }
        if (age.isEmpty()) {
            etAge.setError("Age is required");
            return;
        }

        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        if (selectedGenderId == -1) {
            Toast.makeText(this, "Please select gender", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedGender = findViewById(selectedGenderId);
        String gender = selectedGender.getText().toString();

        if (spinnerPayment.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }
        String payment = spinnerPayment.getSelectedItem().toString();

        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            etPhone.setError("Phone number is required");
            return;
        }

        prefs.edit()
            .putBoolean("registered", true)
            .putString("user_name", name)
            .putString("user_age", age)
            .putString("user_gender", gender)
            .putString("user_payment", payment)
            .putString("user_phone", phone)
            .apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
