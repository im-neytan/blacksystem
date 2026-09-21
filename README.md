# BL4CK SYSTEM

Gateway Android para receber pedidos autorizados do servidor e conduzir a operação USSD no dispositivo.

## Requisitos

- Android 5.0 ou superior, com SIM compatível com a operação USSD.
- Permissão de **Telefone** concedida.
- Serviço **BL4CK SYSTEM** ativado nas configurações de Acessibilidade.
- Conexão com a internet e um código de emparelhamento válido.

## Compilar

Use JDK 17, Android SDK 33 e Gradle 8.5. O repositório não inclui o Gradle Wrapper porque esta plataforma de pull request não aceita arquivos binários; a CI instala a versão necessária do Gradle. O projeto aceita uma URL de API alternativa sem editar código:

```bash
gradle assembleDebug -PapiBaseUrl=https://seu-servidor.example
```

Sem a propriedade, o aplicativo usa o domínio Railway configurado na versão atual. Não distribua o app sem autorização para usar o serviço, a linha telefônica e a automação USSD.

## Segurança e produção

- O aplicativo só permite comunicação HTTPS e não inclui tokens ou chaves no repositório.
- Backups Android são desativados para evitar a extração do token de sessão.
- Antes de publicar, configure a URL de produção, gere uma chave de assinatura própria e valide o fluxo num aparelho compatível com a operadora.
