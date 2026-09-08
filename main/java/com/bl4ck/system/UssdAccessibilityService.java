package com.bl4ck.system;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class UssdAccessibilityService extends AccessibilityService {

    private static String idPedidoAtual = "";
    private static String numeroDestino = "";
    private static String megasParaEnviar = "";
    private static boolean emProcessamento = false;

    public static boolean isEmProcessamento() {
        return emProcessamento;
    }

    public static void iniciarEnvio(String pedidoId, String numero, String megas) {
        idPedidoAtual = pedidoId;
        numeroDestino = numero;
        megasParaEnviar = megas;
        emProcessamento = true;

        // Formata o código USSD com o caractere encoded (%23)
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:*162%23"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Nota: Certifique-se de chamar o intent via Context
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!emProcessamento) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // MÁQUINA DE ESTADOS DINÂMICA
        if (containsTexto(rootNode, "1. Internet") || containsTexto(rootNode, "8. Serviços")) {
            preencherEEnviar(rootNode, "8");
        } else if (containsTexto(rootNode, "Transferir Megas")) {
            preencherEEnviar(rootNode, "2");
        } else if (containsTexto(rootNode, "Digite os Megas") || containsTexto(rootNode, "Quantidade")) {
            preencherEEnviar(rootNode, megasParaEnviar);
        } else if (containsTexto(rootNode, "Digite o Numero") || containsTexto(rootNode, "Destino")) {
            preencherEEnviar(rootNode, numeroDestino);
        } else if (containsTexto(rootNode, "sucesso") || containsTexto(rootNode, "enviados")) {
            emProcessamento = false;
            // Notifica o servidor via API que a transferência foi feita com sucesso
            ServidorManager.atualizarStatusPedido(idPedidoAtual, "CONCLUIDO");
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
        info.packageNames = new String[]{"com.android.phone", "com.google.android.dialer"}; 
        setServiceInfo(info);
    }
}
