package com.screenmirror.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MirrorGameActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // UI
    private TextView tvUserName, tvUserId, tvMyCoins;
    private TextView tvP1Name, tvP1Status, tvP1Coins;
    private TextView tvP2Name, tvP2Status, tvP2Coins;
    private TextView tvP3Name, tvP3Status, tvP3Coins;
    private TextView tvP4Name, tvP4Status, tvP4Coins;
    private TextView tvGameStatus, tvSelectedInfo;
    private TextView tvResultNumber, tvResultText, tvResultDetail;
    private LinearLayout resultPanel;
    private Button btnPick1, btnPick2, btnPick3, btnPick4;
    private Button btnBet10, btnBet50, btnBet100, btnBet200;
    private Button btnPlayRound;

    // Game state
    private int myCoins;
    private int bot2Coins = 500;
    private int bot3Coins = 500;
    private int bot4Coins = 500;
    private int selectedNumber = -1;
    private int selectedBet = 0;
    private boolean roundInProgress = false;

    // Bot names
    private final String[] botNames = {"Rahim", "Karim", "Sumon", "Faruk", "Noman", "Tanvir", "Shakil", "Rubel"};
    private String bot2Name, bot3Name, bot4Name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_game);

        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);
        myCoins = prefs.getInt("user_coins", 500);

        bindViews();
        setupUser();
        setupBots();
        setupButtons();
        updateAllCoins();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putInt("user_coins", myCoins).apply();
    }

    private void bindViews() {
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserId = findViewById(R.id.tv_user_id);
        tvMyCoins = findViewById(R.id.tv_my_coins);

        tvP1Name = findViewById(R.id.tv_p1_name);
        tvP1Status = findViewById(R.id.tv_p1_status);
        tvP1Coins = findViewById(R.id.tv_p1_coins);

        tvP2Name = findViewById(R.id.tv_p2_name);
        tvP2Status = findViewById(R.id.tv_p2_status);
        tvP2Coins = findViewById(R.id.tv_p2_coins);

        tvP3Name = findViewById(R.id.tv_p3_name);
        tvP3Status = findViewById(R.id.tv_p3_status);
        tvP3Coins = findViewById(R.id.tv_p3_coins);

        tvP4Name = findViewById(R.id.tv_p4_name);
        tvP4Status = findViewById(R.id.tv_p4_status);
        tvP4Coins = findViewById(R.id.tv_p4_coins);

        tvGameStatus = findViewById(R.id.tv_game_status);
        tvSelectedInfo = findViewById(R.id.tv_selected_info);
        tvResultNumber = findViewById(R.id.tv_result_number);
        tvResultText = findViewById(R.id.tv_result_text);
        tvResultDetail = findViewById(R.id.tv_result_detail);
        resultPanel = findViewById(R.id.result_panel);

        btnPick1 = findViewById(R.id.btn_pick_1);
        btnPick2 = findViewById(R.id.btn_pick_2);
        btnPick3 = findViewById(R.id.btn_pick_3);
        btnPick4 = findViewById(R.id.btn_pick_4);

        btnBet10 = findViewById(R.id.btn_bet_10);
        btnBet50 = findViewById(R.id.btn_bet_50);
        btnBet100 = findViewById(R.id.btn_bet_100);
        btnBet200 = findViewById(R.id.btn_bet_200);

        btnPlayRound = findViewById(R.id.btn_play_round);
    }

    private void setupUser() {
        String name = prefs.getString("user_name", "Player");
        tvUserName.setText(name);
        tvP1Name.setText(name + " (You)");

        // Generate unique ID: "Mirror" + hash-based number
        String deviceId = prefs.getString("user_phone", "000");
        int idNum = 100 + Math.abs(deviceId.hashCode() % 900);
        tvUserId.setText("Mirror " + idNum);
    }

    private void setupBots() {
        // Pick 3 random different bot names
        java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(botNames));
        java.util.Collections.shuffle(names, random);
        bot2Name = names.get(0);
        bot3Name = names.get(1);
        bot4Name = names.get(2);

        tvP2Name.setText(bot2Name);
        tvP3Name.setText(bot3Name);
        tvP4Name.setText(bot4Name);

        // Random starting coins for bots (300-700)
        bot2Coins = 300 + random.nextInt(401);
        bot3Coins = 300 + random.nextInt(401);
        bot4Coins = 300 + random.nextInt(401);
    }

    private void setupButtons() {
        // Number pick buttons
        View.OnClickListener pickListener = v -> {
            if (roundInProgress) return;
            resetPickButtons();
            ((Button) v).setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));

            if (v.getId() == R.id.btn_pick_1) selectedNumber = 1;
            else if (v.getId() == R.id.btn_pick_2) selectedNumber = 2;
            else if (v.getId() == R.id.btn_pick_3) selectedNumber = 3;
            else if (v.getId() == R.id.btn_pick_4) selectedNumber = 4;

            updateSelectedInfo();
        };
        btnPick1.setOnClickListener(pickListener);
        btnPick2.setOnClickListener(pickListener);
        btnPick3.setOnClickListener(pickListener);
        btnPick4.setOnClickListener(pickListener);

        // Bet buttons
        View.OnClickListener betListener = v -> {
            if (roundInProgress) return;
            resetBetButtons();
            ((Button) v).setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700")));
            ((Button) v).setTextColor(Color.parseColor("#000000"));

            if (v.getId() == R.id.btn_bet_10) selectedBet = 10;
            else if (v.getId() == R.id.btn_bet_50) selectedBet = 50;
            else if (v.getId() == R.id.btn_bet_100) selectedBet = 100;
            else if (v.getId() == R.id.btn_bet_200) selectedBet = 200;

            updateSelectedInfo();
        };
        btnBet10.setOnClickListener(betListener);
        btnBet50.setOnClickListener(betListener);
        btnBet100.setOnClickListener(betListener);
        btnBet200.setOnClickListener(betListener);

        // Play round
        btnPlayRound.setOnClickListener(v -> playRound());
    }

    private void resetPickButtons() {
        int defaultColor = Color.parseColor("#6C63FF");
        btnPick1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnPick2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnPick3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnPick4.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
    }

    private void resetBetButtons() {
        int defaultBg = Color.parseColor("#2A2A3A");
        int goldText = Color.parseColor("#FFD700");
        Button[] bets = {btnBet10, btnBet50, btnBet100, btnBet200};
        for (Button b : bets) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultBg));
            b.setTextColor(goldText);
        }
    }

    private void updateSelectedInfo() {
        if (selectedNumber > 0 && selectedBet > 0) {
            if (selectedBet > myCoins) {
                tvSelectedInfo.setText("Not enough coins! You have " + myCoins);
                tvSelectedInfo.setTextColor(Color.parseColor("#FF6B6B"));
                btnPlayRound.setEnabled(false);
            } else {
                tvSelectedInfo.setText("Number: " + selectedNumber + " | Bet: " + selectedBet + " coins");
                tvSelectedInfo.setTextColor(Color.parseColor("#4ADE80"));
                btnPlayRound.setEnabled(true);
            }
        } else if (selectedNumber > 0) {
            tvSelectedInfo.setText("Number: " + selectedNumber + " — select bet amount");
            tvSelectedInfo.setTextColor(Color.parseColor("#888888"));
            btnPlayRound.setEnabled(false);
        } else if (selectedBet > 0) {
            tvSelectedInfo.setText("Bet: " + selectedBet + " — pick a number");
            tvSelectedInfo.setTextColor(Color.parseColor("#888888"));
            btnPlayRound.setEnabled(false);
        }
    }

    private void updateAllCoins() {
        tvMyCoins.setText(String.valueOf(myCoins));
        tvP1Coins.setText("\uD83E\uDE99 " + myCoins);
        tvP2Coins.setText("\uD83E\uDE99 " + bot2Coins);
        tvP3Coins.setText("\uD83E\uDE99 " + bot3Coins);
        tvP4Coins.setText("\uD83E\uDE99 " + bot4Coins);
    }

    private void playRound() {
        if (selectedNumber < 1 || selectedBet < 1) return;
        if (selectedBet > myCoins) {
            Toast.makeText(this, "Not enough coins!", Toast.LENGTH_SHORT).show();
            return;
        }

        roundInProgress = true;
        btnPlayRound.setEnabled(false);
        resultPanel.setVisibility(View.GONE);

        // Bot choices
        int bot2Pick = 1 + random.nextInt(4);
        int bot3Pick = 1 + random.nextInt(4);
        int bot4Pick = 1 + random.nextInt(4);

        // Bot bets (10-200, clamped to their coins)
        int[] betOptions = {10, 50, 100, 200};
        int bot2Bet = clampBet(betOptions[random.nextInt(4)], bot2Coins);
        int bot3Bet = clampBet(betOptions[random.nextInt(4)], bot3Coins);
        int bot4Bet = clampBet(betOptions[random.nextInt(4)], bot4Coins);

        // Show "choosing" animation
        tvGameStatus.setText("🎲 Rolling...");
        tvP1Status.setText("Picked #" + selectedNumber + " (Bet: " + selectedBet + ")");
        tvP1Status.setTextColor(Color.parseColor("#4ADE80"));

        // Show bot picks with delay
        handler.postDelayed(() -> {
            tvP2Status.setText("Picked #" + bot2Pick + " (Bet: " + bot2Bet + ")");
            tvP2Status.setTextColor(Color.parseColor("#AAAAAA"));
        }, 500);

        handler.postDelayed(() -> {
            tvP3Status.setText("Picked #" + bot3Pick + " (Bet: " + bot3Bet + ")");
            tvP3Status.setTextColor(Color.parseColor("#AAAAAA"));
        }, 1000);

        handler.postDelayed(() -> {
            tvP4Status.setText("Picked #" + bot4Pick + " (Bet: " + bot4Bet + ")");
            tvP4Status.setTextColor(Color.parseColor("#AAAAAA"));
        }, 1500);

        // Reveal result after 2.5s
        handler.postDelayed(() -> {
            int winNum = 1 + random.nextInt(4);
            revealResult(winNum, selectedNumber, selectedBet,
                    bot2Pick, bot2Bet, bot3Pick, bot3Bet, bot4Pick, bot4Bet);
        }, 2500);
    }

    private int clampBet(int bet, int coins) {
        if (coins <= 0) return 0;
        return Math.min(bet, coins);
    }

    private void revealResult(int winNum, int myPick, int myBet,
                              int b2Pick, int b2Bet, int b3Pick, int b3Bet,
                              int b4Pick, int b4Bet) {

        tvResultNumber.setText("\uD83C\uDFAF " + winNum);
        resultPanel.setVisibility(View.VISIBLE);
        tvGameStatus.setText("\uD83C\uDFB2 Winning Number: " + winNum);

        // Calculate total pot
        int totalPot = myBet + b2Bet + b3Bet + b4Bet;

        // Count winners
        int winners = 0;
        if (myPick == winNum) winners++;
        if (b2Pick == winNum) winners++;
        if (b3Pick == winNum) winners++;
        if (b4Pick == winNum) winners++;

        // If no winners, house takes all — everyone loses bet
        if (winners == 0) {
            myCoins -= myBet;
            bot2Coins -= b2Bet;
            bot3Coins -= b3Bet;
            bot4Coins -= b4Bet;

            tvResultText.setText("No Winner!");
            tvResultText.setTextColor(Color.parseColor("#FF6B6B"));
            tvResultDetail.setText("Everyone loses their bet. -" + myBet + " coins");
        } else {
            int winShare = totalPot / winners;

            // User result
            if (myPick == winNum) {
                int profit = winShare - myBet;
                myCoins += profit;
                tvResultText.setText("\uD83C\uDF89 You Win!");
                tvResultText.setTextColor(Color.parseColor("#4ADE80"));
                tvResultDetail.setText("+" + profit + " coins (pot: " + totalPot + ")");
            } else {
                myCoins -= myBet;
                tvResultText.setText("You Lose!");
                tvResultText.setTextColor(Color.parseColor("#FF6B6B"));
                tvResultDetail.setText("-" + myBet + " coins");
            }

            // Bot results
            if (b2Pick == winNum) {
                bot2Coins += (winShare - b2Bet);
                tvP2Status.setText("Won! +" + (winShare - b2Bet));
                tvP2Status.setTextColor(Color.parseColor("#4ADE80"));
            } else {
                bot2Coins -= b2Bet;
                tvP2Status.setText("Lost -" + b2Bet);
                tvP2Status.setTextColor(Color.parseColor("#FF6B6B"));
            }

            if (b3Pick == winNum) {
                bot3Coins += (winShare - b3Bet);
                tvP3Status.setText("Won! +" + (winShare - b3Bet));
                tvP3Status.setTextColor(Color.parseColor("#4ADE80"));
            } else {
                bot3Coins -= b3Bet;
                tvP3Status.setText("Lost -" + b3Bet);
                tvP3Status.setTextColor(Color.parseColor("#FF6B6B"));
            }

            if (b4Pick == winNum) {
                bot4Coins += (winShare - b4Bet);
                tvP4Status.setText("Won! +" + (winShare - b4Bet));
                tvP4Status.setTextColor(Color.parseColor("#4ADE80"));
            } else {
                bot4Coins -= b4Bet;
                tvP4Status.setText("Lost -" + b4Bet);
                tvP4Status.setTextColor(Color.parseColor("#FF6B6B"));
            }
        }

        // Ensure no negative coins
        if (myCoins < 0) myCoins = 0;
        if (bot2Coins < 0) bot2Coins = 0;
        if (bot3Coins < 0) bot3Coins = 0;
        if (bot4Coins < 0) bot4Coins = 0;

        // Refill bots if they go broke
        if (bot2Coins < 10) bot2Coins = 300 + random.nextInt(200);
        if (bot3Coins < 10) bot3Coins = 300 + random.nextInt(200);
        if (bot4Coins < 10) bot4Coins = 300 + random.nextInt(200);

        updateAllCoins();
        prefs.edit().putInt("user_coins", myCoins).apply();

        // Highlight winning number button
        Button[] pickBtns = {btnPick1, btnPick2, btnPick3, btnPick4};
        for (int i = 0; i < 4; i++) {
            if (i + 1 == winNum) {
                pickBtns[i].setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700")));
                pickBtns[i].setTextColor(Color.parseColor("#000000"));
            }
        }

        // Reset for next round after delay
        handler.postDelayed(() -> {
            roundInProgress = false;
            selectedNumber = -1;
            selectedBet = 0;
            resetPickButtons();
            resetBetButtons();
            tvGameStatus.setText("\uD83C\uDFB2 Pick a number (1-4) and place your bet!");
            tvSelectedInfo.setText("Select a number and bet amount");
            tvSelectedInfo.setTextColor(Color.parseColor("#888888"));
            btnPlayRound.setEnabled(false);
            tvP1Status.setText("Waiting...");
            tvP1Status.setTextColor(Color.parseColor("#888888"));
            tvP2Status.setText("Waiting...");
            tvP2Status.setTextColor(Color.parseColor("#888888"));
            tvP3Status.setText("Waiting...");
            tvP3Status.setTextColor(Color.parseColor("#888888"));
            tvP4Status.setText("Waiting...");
            tvP4Status.setTextColor(Color.parseColor("#888888"));

            if (myCoins <= 0) {
                tvGameStatus.setText("💀 No coins left! Contact admin to buy more.");
                tvGameStatus.setTextColor(Color.parseColor("#FF6B6B"));
            }
        }, 3500);
    }

    @Override
    public void onBackPressed() {
        // Save coins, go back to instructions
        prefs.edit().putInt("user_coins", myCoins).apply();
        super.onBackPressed();
    }
}
