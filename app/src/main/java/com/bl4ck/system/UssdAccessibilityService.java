package com.bl4ck.system;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class UssdAccessibilityService extends AccessibilityService {

    private static String idPedidoAtual = "";
    private static String numeroDestino = "";
    private static String megasParaEnviar = "";
    private static volatile boolean emProcessamento = false;
    private static int etapaAtual = 0;
    private static long tempoUltimaAcao = 0;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable timeoutRunnable;

    public static boolean isEmProcessamento() {
        return emProcessamento;
    }

    public static synchronized void iniciarEnvio(Context context, String pedidoId, String numero, String megas) {
        if (emProcessamento) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ServidorManager.atualizarStatusPedido(context, pedidoId, "FALHA", "Permissão de chamada não concedida");
            return;
        }
        idPedidoAtual = pedidoId;
        numeroDestino = numero;
        megasParaEnviar = megas;
        emProcessamento = true;
        etapaAtual = 0;
        tempoUltimaAcao = System.currentTimeMillis();

        // Timeout de segurança (35 segundos)
        if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);
        timeoutRunnable = () -> {
            if (emProcessamento) {
                emProcessamento = false;
                // Passa o 'context' recebido como 1º argumento
                ServidorManager.atualizarStatusPedido(context, idPedidoAtual, "FALHA", "Timeout no menu USSD");
            }
        };
        handler.postDelayed(timeoutRunnable, 35000);

        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:*162%23"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            handler.removeCallbacks(timeoutRunnable);
            emProcessamento = false;
            ServidorManager.atualizarStatusPedido(context, pedidoId, "FALHA", "Não foi possível iniciar a chamada USSD");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Limpeza prévia: Se houver pop-up antigo antes de iniciar um novo pedido, fecha imediatamente
        if (!emProcessamento) {
            fecharDialogoSeExistir(rootNode);
            return;
        }

        // Anti-bounce de 1.2s entre etapas do menu
        if (System.currentTimeMillis() - tempoUltimaAcao < 1200) return;

        // ETAPA 1: Menu Inicial -> "8" (Serviços)
        if (etapaAtual == 0 && (containsTexto(rootNode, "1. Internet") || containsTexto(rootNode, "8. Servicos") || containsTexto(rootNode, "8. Serviços"))) {
            etapaAtual = 1;
            tempoUltimaAcao = System.currentTimeMillis();
            preencherEEnviar(rootNode, "8");
            return;
        }

        // ETAPA 2: Submenu -> "2" (Transferir Megas)
        if (etapaAtual == 1 && (containsTexto(rootNode, "Transferir Megas") || containsTexto(rootNode, "2. Transferir"))) {
            etapaAtual = 2;
            tempoUltimaAcao = System.currentTimeMillis();
            preencherEEnviar(rootNode, "2");
            return;
        }

        // ETAPA 3: Digitar Número
        if (etapaAtual == 2 && (containsTexto(rootNode, "Digite o Numero") || containsTexto(rootNode, "Destino") || containsTexto(rootNode, "Numero"))) {
            etapaAtual = 3;
            tempoUltimaAcao = System.currentTimeMillis();
            preencherEEnviar(rootNode, numeroDestino);
            return;
        }

        // ETAPA 4: Digitar Megas
        if (etapaAtual == 3 && (containsTexto(rootNode, "Digite os Megas") || containsTexto(rootNode, "Quantidade") || containsTexto(rootNode, "Megas"))) {
            etapaAtual = 4;
            tempoUltimaAcao = System.currentTimeMillis();
            preencherEEnviar(rootNode, megasParaEnviar);
            return;
        }

        // ETAPA FINAL: Sucesso (Captura o texto completo da mensagem)
        if (containsTexto(rootNode, "sucesso") || containsTexto(rootNode, "enviados") || containsTexto(rootNode, "com sucesso") || containsTexto(rootNode, "Transferiste")) {
            String respostaCompleta = extrairTextoCompleto(rootNode);
            finalizarProcesso(rootNode, "CONCLUIDO", respostaCompleta);
            return;
        }

        // ETAPA FINAL: Falha / Erro
        if (containsTexto(rootNode, "insuficiente") || containsTexto(rootNode, "invalido") || containsTexto(rootNode, "Erro") || containsTexto(rootNode, "falhou")) {
            String respostaErro = extrairTextoCompleto(rootNode);
            finalizarProcesso(rootNode, "FALHA", respostaErro);
        }
    }

    private void finalizarProcesso(AccessibilityNodeInfo rootNode, String statusFinal, String respostaOperadora) {
        etapaAtual = 0;
        if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);

        // 1. Fechar pop-up atual no celular
        fecharDialogoSeExistir(rootNode);

        // 2. Enviar dados para o servidor Node.js (passando 'this' como Context)
        ServidorManager.atualizarStatusPedido(this, idPedidoAtual, statusFinal, respostaOperadora);

        // 3. Aguarda 8 segundos de intervalo antes de permitir o próximo pedido da fila
        handler.postDelayed(() -> {
            emProcessamento = false;
        }, 8000);
    }

    private String extrairTextoCompleto(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null && !node.getText().toString().trim().isEmpty()) {
            sb.append(node.getText().toString()).append(" ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(extrairTextoCompleto(node.getChild(i)));
        }
        return sb.toString().trim();
    }

    private void fecharDialogoSeExistir(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> okButtons = rootNode.findAccessibilityNodeInfosByText("OK");
        if (okButtons.isEmpty()) {
            okButtons = rootNode.findAccessibilityNodeInfosByText("Cancelar");
        }
        if (okButtons.isEmpty()) {
            okButtons = rootNode.findAccessibilityNodeInfosByText("Fechar");
        }
        if (!okButtons.isEmpty()) {
            okButtons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    private boolean containsTexto(AccessibilityNodeInfo node, String texto) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(texto);
        return list != null && !list.isEmpty();
    }

    private void preencherEEnviar(AccessibilityNodeInfo rootNode, String valor) {
        AccessibilityNodeInfo inputField = findInputField(rootNode);
        if (inputField != null) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, valor);
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

            List<AccessibilityNodeInfo> okButtons = rootNode.findAccessibilityNodeInfosByText("Enviar");
            if (okButtons.isEmpty()) {
                okButtons = rootNode.findAccessibilityNodeInfosByText("OK");
            }
            if (!okButtons.isEmpty()) {
                okButtons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    private AccessibilityNodeInfo findInputField(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.EditText".equals(node.getClassName())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findInputField(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }
}
