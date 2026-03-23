package com.screenmirror.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GameInstructionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_instruction);

        TextView tvUserInfo = findViewById(R.id.tv_user_info);
        Button btnConfirm = findViewById(R.id.btn_confirm);
        Button btnCancel = findViewById(R.id.btn_cancel);

        String name = getIntent().getStringExtra("name");
        String age = getIntent().getStringExtra("age");
        String gender = getIntent().getStringExtra("gender");
        String payment = getIntent().getStringExtra("payment");

        tvUserInfo.setText("Welcome, " + name + " (" + gender + ", Age " + age + ") — " + payment);

        btnConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("name", name);
            intent.putExtra("age", age);
            intent.putExtra("gender", gender);
            intent.putExtra("payment", payment);
            startActivity(intent);
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            finish(); // go back to registration
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Goes back to registration
    }
}
