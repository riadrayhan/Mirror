package com.screenmirror.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Random;

public class MirrorGameActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // UI - Top bar
    private TextView tvAvatarLetter, tvUserName, tvUserId, tvMyCoins;
    // UI - Round info
    private TextView tvRoundNum, tvWinStreak;
    // UI - Player cards
    private TextView tvP1Name, tvP1Status, tvP1Coins;
    private TextView tvP2Name, tvP2Status, tvP2Coins;
    private TextView tvP3Name, tvP3Status, tvP3Coins;
    private TextView tvP4Name, tvP4Status, tvP4Coins;
    // UI - Game board
    private TextView tvGameStatus, tvSelectedInfo, tvPot;
    private Button btnPick1, btnPick2, btnPick3, btnPick4;
    private Button btnBet10, btnBet50, btnBet100, btnBet200;
    private Button btnPlayRound;
    // UI - Result
    private LinearLayout resultPanel;
    private TextView tvResultNumber, tvResultText, tvResultDetail;
    // UI - History
    private LinearLayout historyPanel;
    private TextView tvHistory;

    // Game state
    private int myCoins;
    private int bot2Coins = 500, bot3Coins = 500, bot4Coins = 500;
    private int selectedNumber = -1;
    private int selectedBet = 0;
    private boolean roundInProgress = false;
    private int roundCount = 0;
    private int winCount = 0, lossCount = 0;
    private final ArrayList<String> historyList = new ArrayList<>();

    // Bot names
    private final String[] botNames = {"Rahim", "Karim", "Sumon", "Faruk", "Noman", "Tanvir", "Shakil", "Rubel"};
    private String bot2Name, bot3Name, bot4Name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_game);

        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);
        myCoins = prefs.getInt("user_coins", 500);
        winCount = prefs.getInt("win_count", 0);
        lossCount = prefs.getInt("loss_count", 0);

        bindViews();
        setupUser();
        setupBots();
        setupButtons();
        updateAllCoins();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putInt("user_coins", myCoins)
                .putInt("win_count", winCount)
                .putInt("loss_count", lossCount)
                .apply();
    }

    private void bindViews() {
        tvAvatarLetter = findViewById(R.id.tv_avatar_letter);
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserId = findViewById(R.id.tv_user_id);
        tvMyCoins = findViewById(R.id.tv_my_coins);

        tvRoundNum = findViewById(R.id.tv_round_num);
        tvWinStreak = findViewById(R.id.tv_win_streak);

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
        tvPot = findViewById(R.id.tv_pot);
        tvResultNumber = findViewById(R.id.tv_result_number);
        tvResultText = findViewById(R.id.tv_result_text);
        tvResultDetail = findViewById(R.id.tv_result_detail);
        resultPanel = findViewById(R.id.result_panel);
        historyPanel = findViewById(R.id.history_panel);
        tvHistory = findViewById(R.id.tv_history);

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
        tvAvatarLetter.setText(name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "P");
        tvP1Name.setText(name);

        String deviceId = prefs.getString("user_phone", "000");
        int idNum = 100 + Math.abs(deviceId.hashCode() % 900);
        tvUserId.setText("Mirror " + idNum);

        tvWinStreak.setText("W: " + winCount + "  L: " + lossCount);
    }

    private void setupBots() {
        java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(botNames));
        java.util.Collections.shuffle(names, random);
        bot2Name = names.get(0);
        bot3Name = names.get(1);
        bot4Name = names.get(2);

        tvP2Name.setText(bot2Name);
        tvP3Name.setText(bot3Name);
        tvP4Name.setText(bot4Name);

        bot2Coins = 300 + random.nextInt(401);
        bot3Coins = 300 + random.nextInt(401);
        bot4Coins = 300 + random.nextInt(401);
    }

    private void setupButtons() {
        View.OnClickListener pickListener = v -> {
            if (roundInProgress) return;
            resetPickButtons();
            v.setBackgroundResource(R.drawable.number_btn_selected);

            if (v.getId() == R.id.btn_pick_1) selectedNumber = 1;
            else if (v.getId() == R.id.btn_pick_2) selectedNumber = 2;
            else if (v.getId() == R.id.btn_pick_3) selectedNumber = 3;
            else if (v.getId() == R.id.btn_pick_4) selectedNumber = 4;

            animatePop(v);
            updateSelectedInfo();
        };
        btnPick1.setOnClickListener(pickListener);
        btnPick2.setOnClickListener(pickListener);
        btnPick3.setOnClickListener(pickListener);
        btnPick4.setOnClickListener(pickListener);

        View.OnClickListener betListener = v -> {
            if (roundInProgress) return;
            resetBetButtons();
            v.setBackgroundResource(R.drawable.bet_chip_selected);
            ((Button) v).setTextColor(Color.parseColor("#1A1A00"));

            if (v.getId() == R.id.btn_bet_10) selectedBet = 10;
            else if (v.getId() == R.id.btn_bet_50) selectedBet = 50;
            else if (v.getId() == R.id.btn_bet_100) selectedBet = 100;
            else if (v.getId() == R.id.btn_bet_200) selectedBet = 200;

            animatePop(v);
            updateSelectedInfo();
        };
        btnBet10.setOnClickListener(betListener);
        btnBet50.setOnClickListener(betListener);
        btnBet100.setOnClickListener(betListener);
        btnBet200.setOnClickListener(betListener);

        btnPlayRound.setOnClickListener(v -> playRound());
    }

    private void animatePop(View v) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 0.85f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 0.85f, 1.05f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(250);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }

    private void resetPickButtons() {
        btnPick1.setBackgroundResource(R.drawable.number_btn_bg);
        btnPick2.setBackgroundResource(R.drawable.number_btn_bg);
        btnPick3.setBackgroundResource(R.drawable.number_btn_bg);
        btnPick4.setBackgroundResource(R.drawable.number_btn_bg);
        btnPick1.setTextColor(Color.parseColor("#EEEEFF"));
        btnPick2.setTextColor(Color.parseColor("#EEEEFF"));
        btnPick3.setTextColor(Color.parseColor("#EEEEFF"));
        btnPick4.setTextColor(Color.parseColor("#EEEEFF"));
    }

    private void resetBetButtons() {
        Button[] bets = {btnBet10, btnBet50, btnBet100, btnBet200};
        for (Button b : bets) {
            b.setBackgroundResource(R.drawable.bet_chip_bg);
            b.setTextColor(Color.parseColor("#BBBBBB"));
        }
    }

    private void updateSelectedInfo() {
        if (selectedNumber > 0 && selectedBet > 0) {
            if (selectedBet > myCoins) {
                tvSelectedInfo.setText("Not enough coins! You have " + myCoins);
                tvSelectedInfo.setTextColor(Color.parseColor("#FF6B6B"));
                setPlayButtonEnabled(false);
            } else {
                tvSelectedInfo.setText("#" + selectedNumber + "  •  " + selectedBet + " coins");
                tvSelectedInfo.setTextColor(Color.parseColor("#4ADE80"));
                setPlayButtonEnabled(true);
            }
        } else if (selectedNumber > 0) {
            tvSelectedInfo.setText("#" + selectedNumber + " selected — choose bet");
            tvSelectedInfo.setTextColor(Color.parseColor("#555577"));
            setPlayButtonEnabled(false);
        } else if (selectedBet > 0) {
            tvSelectedInfo.setText(selectedBet + " coins — pick a number");
            tvSelectedInfo.setTextColor(Color.parseColor("#555577"));
            setPlayButtonEnabled(false);
        } else {
            tvSelectedInfo.setText("Select number & bet to play");
            tvSelectedInfo.setTextColor(Color.parseColor("#555577"));
            setPlayButtonEnabled(false);
        }
    }

    private void setPlayButtonEnabled(boolean enabled) {
        btnPlayRound.setEnabled(enabled);
        btnPlayRound.setAlpha(enabled ? 1f : 0.4f);
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
        roundCount++;
        tvRoundNum.setText("#" + roundCount);
        setPlayButtonEnabled(false);
        resultPanel.setVisibility(View.GONE);

        int bot2Pick = 1 + random.nextInt(4);
        int bot3Pick = 1 + random.nextInt(4);
        int bot4Pick = 1 + random.nextInt(4);

        int[] betOptions = {10, 50, 100, 200};
        int bot2Bet = clampBet(betOptions[random.nextInt(4)], bot2Coins);
        int bot3Bet = clampBet(betOptions[random.nextInt(4)], bot3Coins);
        int bot4Bet = clampBet(betOptions[random.nextInt(4)], bot4Coins);

        int totalPot = selectedBet + bot2Bet + bot3Bet + bot4Bet;
        tvPot.setText("\uD83C\uDFAF Pot: " + totalPot + " coins");
        tvPot.setVisibility(View.VISIBLE);

        tvGameStatus.setText("\uD83C\uDFB2  Rolling...");
        tvP1Status.setText("#" + selectedNumber + " / " + selectedBet);
        tvP1Status.setTextColor(Color.parseColor("#4ADE80"));

        handler.postDelayed(() -> {
            tvP2Status.setText("#" + bot2Pick + " / " + bot2Bet);
            tvP2Status.setTextColor(Color.parseColor("#AAAACC"));
        }, 400);
        handler.postDelayed(() -> {
            tvP3Status.setText("#" + bot3Pick + " / " + bot3Bet);
            tvP3Status.setTextColor(Color.parseColor("#AAAACC"));
        }, 800);
        handler.postDelayed(() -> {
            tvP4Status.setText("#" + bot4Pick + " / " + bot4Bet);
            tvP4Status.setTextColor(Color.parseColor("#AAAACC"));
        }, 1200);

        handler.postDelayed(() -> {
            int winNum = 1 + random.nextInt(4);
            revealResult(winNum, selectedNumber, selectedBet,
                    bot2Pick, bot2Bet, bot3Pick, bot3Bet, bot4Pick, bot4Bet);
        }, 2200);
    }

    private int clampBet(int bet, int coins) {
        if (coins <= 0) return 0;
        return Math.min(bet, coins);
    }

    private void revealResult(int winNum, int myPick, int myBet,
                              int b2Pick, int b2Bet, int b3Pick, int b3Bet,
                              int b4Pick, int b4Bet) {

        // Show result panel with animation
        resultPanel.setVisibility(View.VISIBLE);
        resultPanel.setAlpha(0f);
        resultPanel.setScaleX(0.8f);
        resultPanel.setScaleY(0.8f);
        resultPanel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();

        tvResultNumber.setText(String.valueOf(winNum));
        tvGameStatus.setText("Winning: #" + winNum);

        // Highlight winning number button
        Button[] pickBtns = {btnPick1, btnPick2, btnPick3, btnPick4};
        for (int i = 0; i < 4; i++) {
            if (i + 1 == winNum) {
                pickBtns[i].setBackgroundResource(R.drawable.number_btn_winner);
                pickBtns[i].setTextColor(Color.parseColor("#1A1A00"));
            }
        }

        int totalPot = myBet + b2Bet + b3Bet + b4Bet;

        int winners = 0;
        if (myPick == winNum) winners++;
        if (b2Pick == winNum) winners++;
        if (b3Pick == winNum) winners++;
        if (b4Pick == winNum) winners++;

        boolean userWon;
        String historyEntry;

        if (winners == 0) {
            myCoins -= myBet;
            bot2Coins -= b2Bet;
            bot3Coins -= b3Bet;
            bot4Coins -= b4Bet;

            tvResultText.setText("No Winner!");
            tvResultText.setTextColor(Color.parseColor("#FF6B6B"));
            tvResultDetail.setText("All bets lost  •  -" + myBet);
            userWon = false;
            lossCount++;
            historyEntry = "R" + roundCount + ": #" + winNum + " — Lost " + myBet;
        } else {
            int winShare = totalPot / winners;

            if (myPick == winNum) {
                int profit = winShare - myBet;
                myCoins += profit;
                tvResultText.setText("\uD83C\uDF89  YOU WIN!");
                tvResultText.setTextColor(Color.parseColor("#4ADE80"));
                tvResultDetail.setText("+" + profit + " coins  (pot " + totalPot + ")");
                userWon = true;
                winCount++;
                historyEntry = "R" + roundCount + ": #" + winNum + " — Won +" + profit;
            } else {
                myCoins -= myBet;
                tvResultText.setText("You Lose");
                tvResultText.setTextColor(Color.parseColor("#FF6B6B"));
                tvResultDetail.setText("-" + myBet + " coins");
                userWon = false;
                lossCount++;
                historyEntry = "R" + roundCount + ": #" + winNum + " — Lost " + myBet;
            }

            // Bot results
            applyBotResult(b2Pick, winNum, b2Bet, winShare, tvP2Status);
            applyBotResult(b3Pick, winNum, b3Bet, winShare, tvP3Status);
            applyBotResult(b4Pick, winNum, b4Bet, winShare, tvP4Status);
        }

        if (myCoins < 0) myCoins = 0;
        if (bot2Coins < 0) bot2Coins = 0;
        if (bot3Coins < 0) bot3Coins = 0;
        if (bot4Coins < 0) bot4Coins = 0;

        if (bot2Coins < 10) bot2Coins = 300 + random.nextInt(200);
        if (bot3Coins < 10) bot3Coins = 300 + random.nextInt(200);
        if (bot4Coins < 10) bot4Coins = 300 + random.nextInt(200);

        updateAllCoins();
        tvWinStreak.setText("W: " + winCount + "  L: " + lossCount);
        prefs.edit().putInt("user_coins", myCoins)
                .putInt("win_count", winCount)
                .putInt("loss_count", lossCount).apply();

        // Update history
        historyList.add(0, historyEntry);
        if (historyList.size() > 10) historyList.remove(historyList.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String h : historyList) sb.append(h).append("\n");
        tvHistory.setText(sb.toString().trim());
        historyPanel.setVisibility(View.VISIBLE);

        // Reset for next round
        handler.postDelayed(() -> {
            roundInProgress = false;
            selectedNumber = -1;
            selectedBet = 0;
            resetPickButtons();
            resetBetButtons();
            tvGameStatus.setText("\uD83C\uDFB2  Pick your lucky number");
            tvSelectedInfo.setText("Select number & bet to play");
            tvSelectedInfo.setTextColor(Color.parseColor("#555577"));
            tvPot.setVisibility(View.GONE);
            setPlayButtonEnabled(false);
            tvP1Status.setText("\u2014");
            tvP1Status.setTextColor(Color.parseColor("#555577"));
            tvP2Status.setText("\u2014");
            tvP2Status.setTextColor(Color.parseColor("#555577"));
            tvP3Status.setText("\u2014");
            tvP3Status.setTextColor(Color.parseColor("#555577"));
            tvP4Status.setText("\u2014");
            tvP4Status.setTextColor(Color.parseColor("#555577"));

            if (myCoins <= 0) {
                tvGameStatus.setText("\uD83D\uDC80 No coins left! Contact admin.");
                tvGameStatus.setTextColor(Color.parseColor("#FF6B6B"));
            }
        }, 3500);
    }

    private void applyBotResult(int botPick, int winNum, int botBet, int winShare, TextView statusView) {
        if (botPick == winNum) {
            int idx = getBotIndex(statusView);
            addBotCoins(idx, winShare - botBet);
            statusView.setText("Won +" + (winShare - botBet));
            statusView.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            int idx = getBotIndex(statusView);
            addBotCoins(idx, -botBet);
            statusView.setText("Lost -" + botBet);
            statusView.setTextColor(Color.parseColor("#FF6B6B"));
        }
    }

    private int getBotIndex(TextView tv) {
        if (tv == tvP2Status) return 2;
        if (tv == tvP3Status) return 3;
        return 4;
    }

    private void addBotCoins(int idx, int amount) {
        if (idx == 2) bot2Coins += amount;
        else if (idx == 3) bot3Coins += amount;
        else bot4Coins += amount;
    }

    @Override
    public void onBackPressed() {
        prefs.edit().putInt("user_coins", myCoins)
                .putInt("win_count", winCount)
                .putInt("loss_count", lossCount).apply();
        super.onBackPressed();
    }
}
