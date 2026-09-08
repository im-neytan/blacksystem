package com.bl4ck.system;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    // URL fixa do seu servidor central na Railway onde a API gerencia as licenças
    private static final String API_CENTRAL_URL = "https://seu-bot.up.railway.app/api/emparelhar";

    private TextInputEditText edtCodigoEmparelhamento;
    private MaterialButton btnEmparelhar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("BL4CK_CONFIG", MODE_PRIVATE);
        String tokenSessao = prefs.getString("SESSION_TOKEN", "");

        // Se já foi emparelhado anteriormente, entra direto no Dashboard
        if (!tokenSessao.isEmpty()) {
            abrirDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        edtCodigoEmparelhamento = findViewById(R.id.edtCodigoEmparelhamento);
        btnEmparelhar = findViewById(R.id.btnEmparelhar);

        btnEmparelhar.setOnClickListener(v -> {
            String codigo = edtCodigoEmparelhamento.getText().toString().trim().toUpperCase();

            if (codigo.isEmpty()) {
                Toast.makeText(this, "Por favor, digite o código de emparelhamento!", Toast.LENGTH_SHORT).show();
                return;
            }

            btnEmparelhar.setEnabled(false);
            btnEmparelhar.setText("Validando...");

            enviarEmparelhamento(codigo);
        });
    }

    private void enviarEmparelhamento(String codigo) {
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                URL url = new URL(API_CENTRAL_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setConnectTimeout(8000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("codigo", codigo);
                body.put("device_id", deviceId);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);

                    JSONObject json = new JSONObject(response.toString());
                    String token = json.getString("token");

                    // Salva a chave de sessão permanente no dispositivo
                    getSharedPreferences("BL4CK_CONFIG", MODE_PRIVATE)
                            .edit()
                            .putString("SESSION_TOKEN", token)
                            .apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Dispositivo emparelhado com sucesso!", Toast.LENGTH_SHORT).show();
                        abrirDashboard();
                    });
                } else {
                    runOnUiThread(() -> {
                        btnEmparelhar.setEnabled(true);
                        btnEmparelhar.setText("Conectar Dispositivo");
                        Toast.makeText(this, "Código inválido ou expirado!", Toast.LENGTH_SHORT).show();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    btnEmparelhar.setEnabled(true);
                    btnEmparelhar.setText("Conectar Dispositivo");
                    Toast.makeText(this, "Erro ao conectar. Verifique a internet.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void abrirDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
