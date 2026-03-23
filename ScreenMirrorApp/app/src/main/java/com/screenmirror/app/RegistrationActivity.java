package com.screenmirror.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etName, etAge, etPhone;
    private RadioGroup rgGender;
    private Spinner spinnerPayment;
    private Button btnSubmit;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);

        // Already registered? Go to instructions screen
        if (prefs.getBoolean("registered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_registration);

        etName = findViewById(R.id.et_name);
        etAge = findViewById(R.id.et_age);
        etPhone = findViewById(R.id.et_phone);
        rgGender = findViewById(R.id.rg_gender);
        spinnerPayment = findViewById(R.id.spinner_payment);
        btnSubmit = findViewById(R.id.btn_submit);

        // Payment spinner
        String[] paymentMethods = {"Select Payment Method", "bKash", "Nagad"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, paymentMethods);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerPayment.setAdapter(adapter);

        btnSubmit.setOnClickListener(v -> submit());
    }

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

        // Save registration data
        prefs.edit()
            .putBoolean("registered", true)
            .putString("user_name", name)
            .putString("user_age", age)
            .putString("user_gender", gender)
            .putString("user_payment", payment)
            .putString("user_phone", phone)
            .apply();

        // Go to instructions / main screen
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
