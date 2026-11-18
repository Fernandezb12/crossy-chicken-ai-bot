# Crossy Chicken AI Bot — Android

**Entrega experimental implementada en Kotlin. Todavía no cumple todos los criterios de autonomía del encargo.** Se entrega una APK de depuración real y el proyecto Android Studio. El controlador, captura, calibrador y motor predictivo están conectados; no se ha demostrado una serie de partidas autónomas en un teléfono físico. No se prometen puntuaciones ni una detección fiable de todos los peligros.

Consulta `docs/VALIDACION.md` para distinguir implementación de pruebas y `docs/INVESTIGACION.md` para los resultados del juego real. Ninguna puntuación se modifica artificialmente.

## Requisitos e instalación

- Android 8.0/API 26 o superior, Android System WebView actualizado y soporte WebGL.
- Internet para cargar el juego del proveedor. Visión, OCR y decisiones son locales. No hay IA remota ni claves de API.
- Para el Realme C67 con Android 15 instala `CrossyChickenAIBot-RealmeC67-arm64-debug.apk` y consulta `docs/REALME_C67_ANDROID15.md`. Para otras arquitecturas se incluye `CrossyChickenAIBot-universal-debug.apk`. Android puede solicitar permitir instalaciones de la aplicación desde la que abres el archivo. Es una firma de depuración, no una versión de Play Store.
- Alternativa con USB: `adb install -r CrossyChickenAIBot-RealmeC67-arm64-debug.apk`.
- La APK universal incluye las bibliotecas nativas y el OCR sin descargas de modelo en la primera ejecución; por eso es grande.

## Primera prueba: juego integrado

1. Abre la app y conserva **Manual**. Pulsa **ABRIR JUEGO · integrado**. Se abre el iframe oficial de la misma página investigada; no se incluye ni modifica código del juego.
2. Inicia una partida tú mismo y pulsa **Calibrar** cuando el pollo esté visible. La imagen se congela para anotar; el juego subyacente puede seguir transcurriendo.
3. En el desplegable selecciona **Área del juego** y arrastra un rectángulo. Después encierra el **Pollito completo**, con poco margen de fondo.
4. Marca el punto de aterrizaje de la casilla inmediatamente **ADELANTE** y el de la casilla a su **DERECHA**. Esto define la proyección isométrica, no las zonas táctiles.
5. Toca una muestra de **Suelo seguro** vacío. Añade asfalto, agua, vía y obstáculo sólo cuando estén visibles. Los colores desconocidos y las vías/agua quedan bloqueados.
6. Selecciona cada **Control ADELANTE/ATRÁS/IZQUIERDA/DERECHA** y demuestra sobre la captura el toque o deslizamiento que has comprobado manualmente. Se guarda también cuánto dura la pulsación. Guardar NO ejecuta esos gestos.
7. Guarda, vuelve a una partida viva y usa **Probar ↑ ↓ ← →**, una dirección cada vez. El bot envía UN gesto y exige dos observaciones visuales del desplazamiento esperado, compensando la cámara. Si falla, se bloquea: revisa coordenadas, forma del gesto, cuadrícula y muestra del pollito. El gesto entregado por Android no cuenta como salto confirmado.
8. Si cambia el aspecto del pollo y deja de reconocerse, captura esa apariencia y añade **Pollito · aspecto 2/3/4** sin sustituir la geometría. Después vuelve a verificar controles.
9. Regresa a Inicio, selecciona **Asistido**, vuelve al juego y activa. Comprueba las recomendaciones antes de elegir **Automático**. No se permiten movimientos automáticos hasta verificar las cuatro direcciones.
10. **Ⅱ** pausa; **■** detiene las acciones. Al salir del juego integrado se pausan y deja de capturarse. La captura usa PixelCopy: no requiere accesibilidad ni proyección del sistema para este modo.

No se incluye un perfil «universal»: las pruebas de navegador no reprodujeron las cuatro zonas direccionales descritas por el usuario. No uses coordenadas de las capturas del informe en otro teléfono.

## Chrome

1. En la app, abre el ajuste de accesibilidad y activa **Crossy Chicken AI Bot** después de leer su explicación. Puedes revocarlo desde el mismo ajuste. Algunos fabricantes requieren permitir ajustes restringidos para apps instaladas manualmente.
2. Permite el panel flotante y, si tu versión de Android lo requiere, las notificaciones.
3. Pulsa **INICIAR CAPTURA DE CHROME** y acepta el diálogo oficial de MediaProjection. Cada sesión requiere un consentimiento nuevo. Android 14+ solicita capturar la pantalla completa para conservar la correspondencia de coordenadas.
4. Abre el juego en Chrome. El servicio necesita observar su URL en la barra antes de permitir gestos; si no puede verificarla, se pausa. No controla otros navegadores.
5. Usa **Calibrar** y los botones de prueba del panel. Es un perfil distinto al de WebView. Si cambian resolución, orientación o área capturada, vuelve a calibrar/autorizar.
6. **PARAR** del panel o **DETENER** en la notificación finalizan acciones y captura. Revocar la proyección o accesibilidad también pausa el bot.

El flujo MediaProjection + accesibilidad necesita validación en Android 14/15 y en el fabricante de tu teléfono. El análisis visual no es un detector universal de anuncios: cualquier pérdida de reconocimiento produce espera/pausa; no se automatiza cerrar publicidad.

## Puntuación, derrota y reinicio

El OCR sólo procesa el rectángulo **Marcador de puntos** que marques. Exige dígitos puros y dos lecturas coincidentes, descarta saltos excesivos y nunca usa el número de toques como puntuación. «—» significa que no hay lectura verificada.

En una captura de derrota marca el símbolo o botón que sólo aparezca al perder, y el gesto **REINICIAR**. Guarda y usa **Prueba reinicio**. El detector exige tres coincidencias de derrota. El reinicio exige volver a reconocer al pollo y una nueva puntuación OCR; si sólo vuelve al menú sin un inicio calibrado, se pausa. Para continuar desde el menú, calibra **Pantalla de MENÚ** con un botón exclusivo de ese estado (no el logotipo de carga), y **Control INICIAR**. Usa **Inicio** para probarlo: requiere reconocer al pollo y una puntuación nueva. Después prueba Reinicio desde una derrota. El controlador puede encadenar ambos gestos verificados, con un máximo de un intento por transición y timeout de 8 s. Este ciclo está implementado, pero todavía no se han validado partidas encadenadas en un teléfono. El interruptor de reinicio sólo actúa con un gesto previamente verificado y no reintenta indefinidamente.

## Motor y aprendizaje

- OpenCV: coincidencia de hasta cuatro plantillas, rechazo de candidatos ambiguos, flujo óptico para estimar cámara, diferencia temporal compensada y asociación de objetos móviles.
- Cuadrícula calibrada y clasificación de suelo por muestras de color. El seguimiento produce obstáculos genéricos; no afirma reconocer semánticamente cada coche o tren.
- Planificación con búsqueda de hasta tres saltos, colisión continua a lo largo del movimiento, márgenes de incertidumbre y memoria de posiciones para penalizar oscilaciones.
- Conservadora/equilibrada/agresiva cambian márgenes, progreso y penalización de espera. Adaptativa ajusta un margen persistente con resultados de movimientos/derrotas; registra una media suavizada de latencia. No hay DQN/PPO, entrenamiento de redes ni evidencia comparativa de superioridad.
- Un movimiento pendiente como máximo. Fotogramas de más de 250 ms no autorizan decisiones. El procesamiento no acumula cola: descarta capturas cuando el analizador está ocupado.

## Privacidad y diagnóstico

Registros detallados y capturas están desactivados inicialmente. Se guardan localmente si los activas; máximo 40 PNG y rotación del registro a 5 MB. Las estadísticas numéricas de partidas se conservan localmente. La app no sube capturas; el WebView sí realiza las peticiones habituales del sitio del proveedor.

Exporta el ZIP desde Inicio. Incluye eventos, estadísticas y las capturas que autorizaste. Borra los registros desde la app. Para calcular máxima/media/mediana sin convertir puntuaciones desconocidas en cero:

```sh
python3 scripts/evaluate_sessions.py ruta/sessions.jsonl
```

## Abrir y compilar en Android Studio

JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26. OpenCV 4.10.0 y ML Kit OCR latino 16.0.1, versiones fijadas.

Abre esta carpeta como proyecto y selecciona el JDK 17 de Gradle. Instala SDK 35 y Build Tools 34.0.0 (versión predeterminada de AGP). Android Studio genera `local.properties` con tu ruta local. No se incluye la ruta de la máquina que compiló esta entrega.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
python3 scripts/test_evaluate_sessions.py
./gradlew :app:assembleDebug -PbotAbi=arm64-v8a  # APK para Realme C67
./gradlew :app:assembleDebugAndroidTest
# Con teléfono/emulador conectado y autorizado:
./gradlew :app:connectedDebugAndroidTest
```

La APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Los informes unitarios están en `core/build/reports/tests/test/`, y Lint en `app/build/reports/`.

## Organización

`core/`: geometría, planificación, bloqueo de acciones, adaptación y pruebas JVM.
`app/`: `ui`, `capture`, `automation`, `vision`, `storage`, coordinados por `Hub` con un único analizador sin cola. La separación permite probar decisiones sin Android y mantener las API de permisos y captura en el módulo Android. `docs/` contiene la investigación, límites y protocolo de pruebas. `scripts/` contiene diagnóstico USB y resumen estadístico.

El código del bot se entrega para su modificación. Los derechos del juego y de las capturas de prueba pertenecen a sus titulares. OpenCV, ML Kit, Kotlin y AndroidX conservan sus licencias respectivas; consulta las dependencias publicadas. No se redistribuye el juego para uso sin conexión.

## Descargas

- [APK ARM64 para Realme C67](https://raw.githubusercontent.com/Fernandezb12/crossy-chicken-ai-bot/main/downloads/CrossyChickenAIBot-RealmeC67-arm64-debug.apk)
- [Proyecto Android Studio (ZIP)](https://raw.githubusercontent.com/Fernandezb12/crossy-chicken-ai-bot/main/downloads/CrossyChickenAIBot-proyecto.zip)
- [Checksums SHA-256](https://raw.githubusercontent.com/Fernandezb12/crossy-chicken-ai-bot/main/downloads/SHA256SUMS.txt)
