# Estado de la entrega

**No se certifica todavía que el bot juegue partidas completas de forma autónoma en el Realme C67 con Android 15.** Se ha construido el proyecto y compilado APKs reales; hay componentes integrados y pruebas parciales. Los límites siguientes impiden afirmar que se cumplieron todos los criterios de éxito.

## Comprobado

- Proyecto Kotlin/Gradle real, dos módulos, wrapper incluido, Manifest y servicios compilados.
- Compilación APK universal y ARM64 para Realme C67; firma APK v2 verificada y metadatos Android inspeccionados. Los DEX de ambas APKs son idénticos; difieren las arquitecturas nativas empaquetadas.
- 13 pruebas JVM, 0 fallos: no moverse con estado obsoleto/desconocido; progreso sobre suelo libre; colisiones futuras y durante el trayecto; rechazo de agua/vías; incertidumbre; bloqueo de doble gesto; timeout; compensación de cámara; parada; adaptación acotada; coordenadas.
- Dos pruebas adicionales del resumen estadístico: puntuaciones desconocidas excluidas del promedio y series vacías sin resultados inventados.
- Android Lint: 0 errores y 46 advertencias. Principalmente textos españoles en código, avisos de constructores de vistas, permiso de accesibilidad condicionado por API y advertencias conservadoras de contexto estático. Los objetos persistentes reciben applicationContext, no Activity.
- Carga y juego real en Chromium con perfil móvil: progreso del marcador 0→4 mediante entradas táctiles. No es una prueba en Android ni una partida del bot.
- Plantillas OpenCV: tres capturas reales de validación con posición correcta y dos negativos rechazados, tras calibrar con otras dos imágenes. Con una sola apariencia se detectó pérdida de reconocimiento; por eso se añadieron múltiples muestras y rechazo de ambigüedad.
- APK de pruebas instrumentadas compilado. Las revisiones anteriores pasaron primero cuatro y después cinco pruebas en emulador Android 10/API 29. La segunda añadió reconocimiento de menú (154,426 s). La revisión final aplica además el límite de antigüedad del fotograma a todos los gestos: consulta `evidencia/instrumentacion.txt` para su resultado. Las ejecuciones anteriores se conservan separadas. No son pruebas en el Realme ni en Android 15.

## Implementado, pendiente de validar en el teléfono

| Componente | Implementación | Prueba que falta |
|---|---|---|
| WebView y PixelCopy | Captura de región real de la vista; gestos locales | El emulador mostró la vista y captura activa, pero no cargó el juego en su WebView 74.0.3729.185; falta WebView actualizado de realme |
| MediaProjection | Servicio foreground, notificación de parada, callback de revocación, no reutiliza tokens | Consentimiento y rotación en Android 15 real |
| Accesibilidad | Gestos calibrados; comprobación de Chrome/URL | Gestos reales y reconocimiento de la URL en la versión de Chrome del usuario |
| Calibración | Rectángulos, puntos de cuadrícula, muestras, toques/deslizamientos, duración, perfiles | Verificar los cuatro desplazamientos en Realme; no hay perfil prevalidado |
| Visión temporal | Plantillas, flujo óptico, diferencia compensada y asociación de objetos | Precisión con coches, sombras, oclusión, perspectiva y cámara durante juego prolongado |
| Decisión | Tres pasos, colisión continua, márgenes, memoria contra oscilación | Tasas de colisión y progreso en partidas reales |
| Parada | Token de cancelación, no nuevos gestos, pausa al perder enfoque | Latencia real de parada; un gesto ya despachado por Android puede completar |
| Estadísticas | OCR local, sesiones, récord, supervivencia y exportación | Exactitud de números y tiempos; no hay resultados de rendimiento físico |
| Derrota | Plantilla específica, tres coincidencias y un registro por sesión | Sensibilidad/especificidad y anuncios parecidos |
| Reinicio | Un intento calibrado; exige nuevo pollo + puntuación | En esta versión el botón vuelve al menú; inicio desde menú requiere otro gesto y plantilla calibrados; ciclo completo sin validar |
| Aprendizaje | Margen/latencia persistentes, registro de estados y resultados | Mejora estadística frente a controles fijos no demostrada |

La interfaz principal de la app se observó en pantalla y se guardó `evidencia/app-interfaz-visible.png`. Android devolvió apertura `Status: ok` en la revisión previa a la última comprobación de antigüedad. La prueba de la vista de juego mostró captura activa del área (480×658), pero contenido web blanco. Esto **no** demuestra que el juego funcione dentro de WebView: el proveedor debe comprobarse en el WebView actualizado del Realme. No se alteró la validación TLS para forzar la carga.

## Pendiente y limitaciones conocidas

1. No se ha demostrado el ciclo de varias partidas completas sin intervención. El arranque desde menú tiene una transición calibrada con verificación visual y OCR, pero falta comprobar el ciclo completo jugando.
2. El detector no clasifica semánticamente todos los peligros. Agua y ferrocarril se bloquean; falta seguir plataformas y predecir trenes. Esto puede detener el progreso y provocar derrota por inactividad.
3. Las velocidades salen de componentes de diferencia de imagen, no de cajas etiquetadas por un modelo validado. Puede haber asociaciones incorrectas.
4. La compensación de cámara sólo modela traslación. Perspectiva cambiante, zoom, escenas con pocas características o muchos objetos móviles pueden impedir confirmar movimientos.
5. La cuadrícula se calibra manualmente a partir de los puntos de aterrizaje observados; no se infiere automáticamente. Si el suelo no dibuja casillas, primero observa el tamaño/dirección de un salto manual. El perfil depende de la geometría de captura. Cambiar zoom del juego, Chrome, tamaño del área o skin exige recalibración. No hay detector universal de límites/menús/anuncios.
6. El OCR puede no leer la fuente del juego o perder puntuaciones que cambian muy rápido; desconocido nunca se sustituye por puntuación inventada.
7. No se implementaron DQN/PPO, entrenamiento remoto, sincronización ni un modelo personalizado. No hay evidencia suficiente para justificar esos sistemas todavía.
8. No se midieron batería, temperatura, FPS físicos, máxima/media/mediana de partidas del bot ni superioridad de estrategias. El 4 del navegador pertenece a una prueba de entradas, no a una evaluación autónoma.
9. El emulador del entorno carece de KVM. Su primer arranque tuvo problemas de almacenamiento y un proceso terminó con código 137 al compartir memoria con herramientas de compilación. Esto no se presenta como fallo de la APK ni como prueba física; se guardan registros. Una captura anterior mostró un ANR de System UI del emulador, no una interfaz validada de la app. La ejecución final usa menor resolución y conserva su propia captura.
10. La modalidad Chrome depende de poder verificar el paquete y URL visibles. Si Chrome oculta información necesaria, se pausa; no se relaja la comprobación para actuar fuera del juego.

## Para continuar con evidencia

Usa `docs/REALME_C67_ANDROID15.md` y `docs/PRUEBA_FISICA.md`. Primero verifica instalación, captura y las cuatro direcciones; después recopila capturas etiquetadas de peligros y partidas. Guarda resultados reales, incluyendo fallos, antes de habilitar/recomendar autonomía prolongada. El proyecto contiene herramientas para ese diagnóstico, pero no sustituye esa validación.

## Resultado final de instrumentación

La APK universal final se instaló y pasó las 5 pruebas instrumentadas en Android 10/API 29: 137,865 segundos, 0 fallos. La APK ARM64 contiene el mismo código DEX. Sigue pendiente la prueba física en Realme C67 / Android 15 y la autonomía de partidas.
