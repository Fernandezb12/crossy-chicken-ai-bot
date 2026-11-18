# Realme C67 · Android 15

El usuario indicó este modelo y versión. Se ha preparado `CrossyChickenAIBot-RealmeC67-arm64-debug.apk` con las bibliotecas ARM64. Esto selecciona la arquitectura; **no significa que se haya probado en ese teléfono**. La app compila con target SDK 35 (Android 15).

## Ruta recomendada para la primera prueba

1. Instala la APK ARM64. Autoriza la instalación desde tu gestor de archivos si Android la solicita.
2. Abre la app, conserva Manual y pulsa **ABRIR JUEGO · integrado**. Para esta modalidad no hacen falta accesibilidad ni permiso de capturar toda la pantalla.
3. Comprueba que el juego carga y que puedes jugar tú mismo. En caso de pantalla vacía, actualiza Android System WebView/Chrome desde la tienda del teléfono y repite.
4. Mantén la misma orientación durante la calibración. Marca el área real del juego, el pollo, las casillas vecinas, el suelo y cada gesto. Las barras del teléfono quedan fuera de la vista capturada del juego.
5. En una nueva partida comprueba **Probar ↑ ↓ ← →**. Si el toque no da la dirección esperada, vuelve a Calibrar y registra el deslizamiento que sí funcione. No basta con que el botón se ilumine.
6. Revisa Asistido antes de Automático. Puedes parar desde la barra superior del juego.

## Alternativa Chrome

- Activa el servicio de accesibilidad desde el botón de la app, después de leer su explicación.
- Si Android muestra «ajuste restringido», revisa la ficha de esta app en Ajustes y la opción del sistema para permitir ajustes restringidos. El nombre exacto y la disponibilidad pueden variar en realme UI; no se ha inspeccionado tu pantalla.
- Concede «mostrar sobre otras aplicaciones» para el panel de parada/calibración y el permiso de notificaciones.
- Inicia la captura y acepta el diálogo oficial de Android 15. No se almacena ni reutiliza esa autorización tras detener la sesión.
- Abre la URL del juego en Chrome y conserva visible su dirección al iniciar. La app se niega a enviar gestos si no consigue verificar el sitio.
- Si realme UI detiene el servicio en segundo plano, revisa la configuración de batería de esta app. No se solicita desactivar restricciones globales del teléfono.

La intervención mínima que falta es verificar **carga + captura + cuatro direcciones** en este Realme. Después hacen falta pruebas de obstáculos y partidas completas: un APK instalado y cuatro saltos correctos todavía no demuestran autonomía fiable.
