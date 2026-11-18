# Arquitectura y límites técnicos

La ruta integrada utiliza WebView del sistema, PixelCopy de su región visible y MotionEvent sobre esa vista. No accede al estado interno de Construct, no inyecta JavaScript y no altera el juego. La ruta Chrome utiliza MediaProjection en servicio foreground y AccessibilityService con comprobación de paquete y URL visibles; sólo envía gestos calibrados.

Los permisos pueden revocarse y no se persisten tokens MediaProjection. Cambiar la geometría invalida los controles; cambiar la fuente, tamaño, orientación o densidad selecciona otro perfil. Las coordenadas del gesto están normalizadas al área de juego. Los puntos de la cuadrícula están en coordenadas de captura para incluir la perspectiva isométrica.

`Hub` recibe un fotograma por vez; si el analizador está ocupado descarta el siguiente. Un solo hilo ejecuta OpenCV y decisiones. Los toques se ejecutan en el hilo principal y comprueban un token de generación: parar invalida acciones encoladas. `MotionGate` conserva el movimiento pendiente hasta dos observaciones del desplazamiento esperado o un timeout que bloquea más acciones. La finalización del gesto del sistema no confirma el movimiento del personaje.

El seguimiento estima traslación de cámara mediante Lucas–Kanade y consenso de características. Segmenta cambios con diferencia temporal compensada y asocia componentes por proximidad. Convierte posiciones y velocidades a la cuadrícula calibrada. Es sensible a oclusiones, sombras, colores similares, movimientos rápidos y poca textura. Todavía no es una segmentación semántica validada de coches, trenes y plataformas.

La planificación evalúa hasta tres acciones futuras, utiliza el acercamiento mínimo continuo entre el pollo y cada obstáculo y rechaza terrenos no conocidos. Los márgenes crecen con incertidumbre. Agua/vías están bloqueadas deliberadamente porque no hay un detector validado de plataformas y trenes. Esto limita seriamente cuánto puede progresar en esta versión del juego.

La adaptación conserva latencia media, número de muestras y margen de riesgo. No aprende un mapa periódico de aparición de obstáculos. No se implementó DQN/PPO: sin estado visual fiable y pruebas físicas del controlador, las recompensas no serían una señal suficiente y una red no corregiría el problema de controles.

La puntuación viene de OCR sobre un rectángulo seleccionado, con lecturas repetidas; puede perder cambios rápidos o dígitos pequeños. Una puntuación no leída permanece desconocida. La plantilla de derrota es específica del perfil. El reinicio está condicionado a un gesto verificado y a retornar a escena con puntuación; la transición automática desde menú requiere una plantilla y gesto de inicio independientes, previamente verificados. Cada transición tiene timeout de 8 s; el OCR de una generación anterior se descarta.

Los modelos internos del OCR pertenecen a ML Kit; no hay un modelo neuronal personalizado del juego. Las imágenes de pruebas no se empaquetan en la APK de uso: sólo en el APK de instrumentación. No hay datos privados ni claves incrustadas.
