package com.jameds.seniorcalculator;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private TextView expressionDisplay;
    private TextView resultDisplay;
    private Button voiceButton;
    private TextToSpeech tts;

    private String currentInput = "0";
    private BigDecimal storedValue = null;
    private String pendingOperator = null;
    private String expressionText = "";

    private boolean startNewNumber = false;
    private boolean justCalculated = false;
    private boolean voiceEnabled = false;

    private static final int MAX_INPUT_LENGTH = 18;

    private final int[] allButtonIds = {
            R.id.btnAc, R.id.btnBack, R.id.btnVoice, R.id.btnDivide,
            R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnMultiply,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btnSubtract,
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btnAdd,
            R.id.btn0, R.id.btnDot, R.id.btnEquals
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        tts = new TextToSpeech(this, this);
    }

    private void buildUi() {
        setContentView(R.layout.activity_main);

        expressionDisplay = findViewById(R.id.expressionDisplay);
        resultDisplay = findViewById(R.id.resultDisplay);
        voiceButton = findViewById(R.id.btnVoice);

        bindButtons();
        updateDisplays();
        updateVoiceButton();
        applySafeSystemInsets();
        scheduleAdaptiveSizing();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
    }

    private void bindButtons() {
        bindNumberButton(R.id.btn0, "0");
        bindNumberButton(R.id.btn1, "1");
        bindNumberButton(R.id.btn2, "2");
        bindNumberButton(R.id.btn3, "3");
        bindNumberButton(R.id.btn4, "4");
        bindNumberButton(R.id.btn5, "5");
        bindNumberButton(R.id.btn6, "6");
        bindNumberButton(R.id.btn7, "7");
        bindNumberButton(R.id.btn8, "8");
        bindNumberButton(R.id.btn9, "9");

        findViewById(R.id.btnDot).setOnClickListener(v -> onDecimalPoint());
        findViewById(R.id.btnAdd).setOnClickListener(v -> onOperator("+"));
        findViewById(R.id.btnSubtract).setOnClickListener(v -> onOperator("-"));
        findViewById(R.id.btnMultiply).setOnClickListener(v -> onOperator("*"));
        findViewById(R.id.btnDivide).setOnClickListener(v -> onOperator("/"));
        findViewById(R.id.btnEquals).setOnClickListener(v -> onEquals());
        findViewById(R.id.btnAc).setOnClickListener(v -> clearAll());
        findViewById(R.id.btnBack).setOnClickListener(v -> backspace());
        voiceButton.setOnClickListener(v -> toggleVoice());
    }

    private void bindNumberButton(int id, String number) {
        Button button = findViewById(id);
        button.setOnClickListener(v -> {
            onDigit(number);
            speak(number);
        });
    }

    private void onDigit(String digit) {
        if (justCalculated) clearCalculationStateForNewInput();

        if (startNewNumber) {
            currentInput = digit;
            startNewNumber = false;
        } else if ("0".equals(currentInput)) {
            currentInput = digit;
        } else if (currentInput.length() < MAX_INPUT_LENGTH) {
            currentInput += digit;
        }

        updateLiveExpression();
        updateDisplays();
    }

    private void onDecimalPoint() {
        if (justCalculated) clearCalculationStateForNewInput();

        if (startNewNumber) {
            currentInput = "0.";
            startNewNumber = false;
        } else if (!currentInput.contains(".") && currentInput.length() < MAX_INPUT_LENGTH) {
            currentInput += ".";
        }

        updateLiveExpression();
        updateDisplays();
        speak("小數點");
    }

    private void onOperator(String operator) {
        try {
            BigDecimal currentValue = parseCurrent();

            if (justCalculated) {
                storedValue = currentValue;
                justCalculated = false;
            } else if (storedValue == null) {
                storedValue = currentValue;
            } else if (pendingOperator != null && !startNewNumber) {
                BigDecimal result = calculate(storedValue, currentValue, pendingOperator);
                if (result == null) return;
                storedValue = result;
                currentInput = format(result);
            }

            pendingOperator = operator;
            expressionText = format(storedValue) + " " + operatorToVisible(operator);
            startNewNumber = true;

            updateDisplays();
            speak(operatorToSpeech(operator));
        } catch (Exception e) {
            showError("輸入錯誤");
        }
    }

    private void onEquals() {
        if (storedValue == null || pendingOperator == null || startNewNumber) {
            speakCurrentValue();
            return;
        }

        try {
            BigDecimal right = parseCurrent();

            String fullExpression =
                    format(storedValue) + " " +
                    operatorToVisible(pendingOperator) + " " +
                    format(right) + " ＝";

            BigDecimal result = calculate(storedValue, right, pendingOperator);
            if (result == null) return;

            currentInput = format(result);
            expressionText = fullExpression;

            storedValue = null;
            pendingOperator = null;
            startNewNumber = false;
            justCalculated = true;

            updateDisplays();
            speak("答案是 " + numberForSpeech(currentInput));
        } catch (Exception e) {
            showError("計算錯誤");
        }
    }

    private void updateLiveExpression() {
        if (pendingOperator != null && storedValue != null && !startNewNumber) {
            expressionText =
                    format(storedValue) + " " +
                    operatorToVisible(pendingOperator) + " " +
                    currentInput;
        } else if (pendingOperator == null && !justCalculated) {
            expressionText = currentInput;
        }
    }

    private void clearCalculationStateForNewInput() {
        currentInput = "0";
        expressionText = "";
        storedValue = null;
        pendingOperator = null;
        startNewNumber = false;
        justCalculated = false;
    }

    private BigDecimal calculate(BigDecimal left, BigDecimal right, String operator) {
        try {
            switch (operator) {
                case "+": return left.add(right);
                case "-": return left.subtract(right);
                case "*": return left.multiply(right);
                case "/":
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        showError("不能除以零");
                        speak("不能除以零");
                        return null;
                    }
                    return left.divide(right, MathContext.DECIMAL64);
                default: return right;
            }
        } catch (ArithmeticException e) {
            showError("數字太大");
            return null;
        }
    }

    private void clearAll() {
        currentInput = "0";
        expressionText = "";
        storedValue = null;
        pendingOperator = null;
        startNewNumber = false;
        justCalculated = false;
        updateDisplays();
        speak("清除");
    }

    private void backspace() {
        if (startNewNumber || justCalculated) return;

        if (currentInput.length() > 1) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            if ("-".equals(currentInput)) currentInput = "0";
        } else {
            currentInput = "0";
        }

        updateLiveExpression();
        updateDisplays();
        speak("退格");
    }

    private void toggleVoice() {
        voiceEnabled = !voiceEnabled;
        if (!voiceEnabled && tts != null) tts.stop();

        updateVoiceButton();

        if (voiceEnabled) speak("語音開啟");
    }

    private void updateVoiceButton() {
        voiceButton.setText(voiceEnabled ? "語音 開" : "語音 關");
        voiceButton.setAlpha(voiceEnabled ? 1.0f : 0.55f);
        voiceButton.setContentDescription(voiceEnabled ? "關閉語音" : "開啟語音");
    }

    private BigDecimal parseCurrent() {
        if (currentInput.endsWith(".")) {
            return new BigDecimal(currentInput.substring(0, currentInput.length() - 1));
        }
        return new BigDecimal(currentInput);
    }

    private String format(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        String text = normalized.toPlainString();

        if (text.length() > 22) {
            text = normalized.round(MathContext.DECIMAL64).toEngineeringString();
        }
        return text;
    }

    private void updateDisplays() {
        expressionDisplay.setText(expressionText);
        resultDisplay.setText(currentInput);
        scheduleAdaptiveSizing();
    }

    private void applySafeSystemInsets() {
        View root = findViewById(R.id.rootLayout);
        if (root == null) return;

        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();

            view.setPadding(
                    baseLeft + left,
                    baseTop + top,
                    baseRight + right,
                    baseBottom + bottom
            );

            view.post(this::applyAdaptiveSizing);
            return insets;
        });

        root.requestApplyInsets();
    }

    private void scheduleAdaptiveSizing() {
        View root = findViewById(R.id.rootLayout);
        if (root == null) return;
        root.post(this::applyAdaptiveSizing);
    }

    private void applyLandscapeKeypadWidth() {
        boolean landscape =
                getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE;

        View keypad = findViewById(R.id.keypadPanel);
        View root = findViewById(R.id.rootLayout);

        if (keypad == null || root == null) return;

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) keypad.getLayoutParams();

        if (!landscape) {
            params.width = LinearLayout.LayoutParams.MATCH_PARENT;
            keypad.setLayoutParams(params);
            return;
        }

        int keypadHeight = keypad.getHeight();
        int availableWidth = root.getWidth()
                - root.getPaddingLeft()
                - root.getPaddingRight();

        if (keypadHeight <= 0 || availableWidth <= 0) return;

        int desiredWidth = Math.round(keypadHeight * 2.05f);
        int minWidth = Math.round(availableWidth * 0.88f);
        int maxWidth = Math.round(availableWidth * 0.96f);

        int finalWidth = Math.max(minWidth, Math.min(desiredWidth, maxWidth));

        params.width = finalWidth;
        params.gravity = Gravity.CENTER_HORIZONTAL;
        keypad.setLayoutParams(params);
    }

    private void applyAdaptiveSizing() {
        if (expressionDisplay == null || resultDisplay == null) return;

        applyLandscapeKeypadWidth();

        boolean landscape =
                getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE;

        int expressionHeight = expressionDisplay.getHeight();
        int resultHeight = resultDisplay.getHeight();

        float expressionRatio = landscape ? 0.34f : 0.27f;
        float resultRatio = landscape ? 0.72f : 0.43f;

        if (justCalculated) {
            expressionRatio = landscape ? 0.30f : 0.23f;
            resultRatio = landscape ? 0.86f : 0.52f;
        }

        float expressionPx = Math.max(18f, expressionHeight * expressionRatio);
        float resultPx = Math.max(30f, resultHeight * resultRatio);

        int resultLength = Math.max(1, currentInput.length());
        if (resultLength > 10) {
            resultPx *= 10f / resultLength;
            resultPx = Math.max(resultPx, landscape ? 42f : 30f);
        }

        expressionDisplay.setTextSize(TypedValue.COMPLEX_UNIT_PX, expressionPx);
        resultDisplay.setTextSize(TypedValue.COMPLEX_UNIT_PX, resultPx);

        for (int id : allButtonIds) {
            View v = findViewById(id);
            if (!(v instanceof Button)) continue;

            Button b = (Button) v;
            int h = b.getHeight();
            if (h <= 0) continue;

            float ratio;

            if (id == R.id.btnVoice) {
                ratio = landscape ? 0.36f : 0.29f;
            } else if (id == R.id.btnAdd ||
                    id == R.id.btnSubtract ||
                    id == R.id.btnMultiply ||
                    id == R.id.btnDivide ||
                    id == R.id.btnEquals) {
                ratio = landscape ? 0.58f : 0.45f;
            } else {
                ratio = landscape ? 0.50f : 0.39f;
            }

            float px = Math.max(20f, h * ratio);

            // 長文字不可超過按鍵寬度。
            int availableButtonWidth = Math.max(
                    1,
                    b.getWidth() - b.getPaddingLeft() - b.getPaddingRight()
            );
            float estimatedCharWidth = Math.max(
                    1f,
                    b.getText().length() * 0.62f
            );
            float widthLimitedPx =
                    availableButtonWidth / estimatedCharWidth;

            px = Math.min(px, widthLimitedPx);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        }
    }

    private String operatorToVisible(String op) {
        switch (op) {
            case "+": return "＋";
            case "-": return "－";
            case "*": return "×";
            case "/": return "÷";
            default: return op;
        }
    }

    private String operatorToSpeech(String op) {
        switch (op) {
            case "+": return "加";
            case "-": return "減";
            case "*": return "乘以";
            case "/": return "除以";
            default: return "";
        }
    }

    private void speakCurrentValue() {
        speak(numberForSpeech(currentInput));
    }

    private String numberForSpeech(String value) {
        return value.replace("-", "負").replace(".", "點");
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        expressionDisplay.setText(message);
    }

    private void speak(String text) {
        if (!voiceEnabled || tts == null) return;

        tts.stop();
        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "seniorCalculatorSpeech"
        );
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.TRADITIONAL_CHINESE);
            tts.setSpeechRate(0.85f);
            tts.setPitch(1.0f);

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                        this,
                        "未安裝中文語音，計算功能仍可正常使用",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
