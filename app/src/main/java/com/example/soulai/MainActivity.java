package com.example.soulai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Settings UI activity to configure API, System Prompt & Auto-reply behavior
 */
public class MainActivity extends AppCompatActivity {

    private EditText etApiUrl, etApiKey, etModel, etSystemPrompt, etDelay, etMaxHistory;
    private Switch switchAutoReply, switchAutoSend;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("ai_config", Context.MODE_WORLD_READABLE);

        etApiUrl = findViewById(R.id.etApiUrl);
        etApiKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        etSystemPrompt = findViewById(R.id.etSystemPrompt);
        etDelay = findViewById(R.id.etDelay);
        etMaxHistory = findViewById(R.id.etMaxHistory);
        switchAutoReply = findViewById(R.id.switchAutoReply);
        switchAutoSend = findViewById(R.id.switchAutoSend);
        Button btnSave = findViewById(R.id.btnSave);

        // Load saved values
        etApiUrl.setText(sp.getString("api_url", "https://api.openai.com/v1/chat/completions"));
        etApiKey.setText(sp.getString("api_key", ""));
        etModel.setText(sp.getString("model", "gpt-3.5-turbo"));
        etSystemPrompt.setText(sp.getString("system_prompt", "你是一个幽默、贴心且有趣的Soul小助手，语言简洁自然。"));
        etDelay.setText(sp.getString("response_delay", "2000"));
        etMaxHistory.setText(sp.getString("max_history", "10"));
        switchAutoReply.setChecked(sp.getBoolean("enable_auto_reply", true));
        switchAutoSend.setChecked(sp.getBoolean("auto_click_send", true));

        btnSave.setOnClickListener(v -> {
            sp.edit()
                .putString("api_url", etApiUrl.getText().toString().trim())
                .putString("api_key", etApiKey.getText().toString().trim())
                .putString("model", etModel.getText().toString().trim())
                .putString("system_prompt", etSystemPrompt.getText().toString().trim())
                .putString("response_delay", etDelay.getText().toString().trim())
                .putString("max_history", etMaxHistory.getText().toString().trim())
                .putBoolean("enable_auto_reply", switchAutoReply.isChecked())
                .putBoolean("auto_click_send", switchAutoSend.isChecked())
                .apply();

            Toast.makeText(this, "设置已保存！请重启 Soul App 生效", Toast.LENGTH_SHORT).show();
        });
    }
}