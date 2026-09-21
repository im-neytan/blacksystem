package com.bl4ck.system;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity {
    private ServidorManager servidorManager;
    private TextView txtServerStatus;
    private TextView txtStatusProcessamento;
    private Switch switchGateway;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        ((TextView) findViewById(R.id.txtDeviceId)).setText("ID do Dispositivo: " + new ServidorManager(this).getDeviceId());
        txtServerStatus = findViewById(R.id.txtServerStatus);
        txtStatusProcessamento = findViewById(R.id.txtStatusProcessamento);
        switchGateway = findViewById(R.id.switchGateway);
        servidorManager = new ServidorManager(this);
        servidorManager.setStatusCallback(status -> runOnUiThread(() -> {
            txtServerStatus.setText(status);
            txtStatusProcessamento.setText("Status: " + status.replaceAll("^[^ ]+ ", ""));
        }));

        switchGateway.setOnCheckedChangeListener((button, ativo) -> {
            if (ativo) iniciarGateway();
            else { servidorManager.pararSincronizacao(); txtServerStatus.setText("⚪ Gateway pausado"); }
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new FilaFragment()).commit();
        }
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = item.getItemId() == R.id.nav_historico ? new HistoricoFragment() : new FilaFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            return true;
        });
        if (switchGateway.isChecked()) iniciarGateway();
    }

    private void iniciarGateway() {
        if (!servicoAcessibilidadeAtivo()) {
            switchGateway.setChecked(false);
            new AlertDialog.Builder(this)
                    .setTitle("Ative a acessibilidade")
                    .setMessage("Para concluir operações USSD, ative o serviço BL4CK SYSTEM em Acessibilidade.")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Abrir configurações", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
            return;
        }
        servidorManager.iniciarSincronizacaoFila();
    }

    private boolean servicoAcessibilidadeAtivo() {
        String servicos = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(servicos) && servicos.toLowerCase().contains(getPackageName().toLowerCase() + "/" + UssdAccessibilityService.class.getName().toLowerCase());
    }

    public ServidorManager getServidorManager() { return servidorManager; }

    @Override
    protected void onResume() {
        super.onResume();
        if (switchGateway != null && switchGateway.isChecked() && servicoAcessibilidadeAtivo()) iniciarGateway();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && servidorManager != null) servidorManager.pararSincronizacao();
        super.onDestroy();
    }
}
