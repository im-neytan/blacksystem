package com.bl4ck.system;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private ServidorManager servidorManager;
    private TextView txtDeviceId, txtServerStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        txtDeviceId = findViewById(R.id.txtDeviceId);
        txtServerStatus = findViewById(R.id.txtServerStatus);

        servidorManager = new ServidorManager(this);

        // Exibe o ID do aparelho no painel
        String id = servidorManager.getDeviceId();
        txtDeviceId.setText("ID do Dispositivo: " + id);

        // Inicia a comunicação/polling com o bot
        servidorManager.iniciarSincronizacaoFila();
        txtServerStatus.setText("🟢 Conectado (Sincronizado)");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (servidorManager != null) {
            servidorManager.pararSincronizacao();
        }
    }
}
