# Validación mínima en el teléfono

No se puede sustituir esta prueba por un test JVM ni por Chromium con user-agent Android.

## Primera sesión (manual y controlada)

1. Anota modelo, Android, versión de Chrome/WebView, orientación y resolución. Instala y abre la APK; verifica que no hay acciones antes de activarla.
2. Abre el juego integrado. Si no renderiza, exporta el error y prueba Chrome con consentimiento de captura, accesibilidad y panel flotante.
3. Confirma que la captura congelada de Calibrar coincide con lo que ves en el juego y no está vacía/negra. Anota el área efectiva; no incluyas barras ni publicidad.
4. Demuestra manualmente las cuatro direcciones. Registra si son toques o deslizamientos; no uses un mapa obtenido de otro dispositivo.
5. Guarda las muestras visuales y geometría. Prueba una dirección cada vez. Deben aparecer mensajes de movimiento verificado. Un toque sin desplazamiento debe bloquear otro gesto, no acumular repeticiones.
6. Repite las pruebas con cámara en movimiento. Si no reconoce un cambio de pose, añade una muestra. Anota todos los falsos positivos/negativos.
7. En modo Asistido observa al menos 20 recomendaciones cerca de carreteras. Comprueba a ojo el riesgo futuro y los casos de espera/lateral. No habilites automático si el detector confunde pollo y obstáculos.
8. Usa Automático sólo después de lo anterior. Ten visible PARAR. Verifica que dejar la app, abrir Ajustes, mostrar publicidad, rotar el teléfono o revocar permisos bloquea las acciones.
9. Prueba derrota y reinicio. Si el botón vuelve al menú, calibra el símbolo de menú y Control INICIAR y prueba Inicio antes de Reinicio. Sin ese perfil verificado el programa debe pausar. Sólo certifica partidas consecutivas cuando las hayas observado.
10. Exporta diagnóstico con las capturas del juego autorizadas. Las causas de muerte no reconocidas deben quedar como desconocidas.

## Matriz de aceptación

| Prueba | Resultado esperado | Evidencia a guardar |
|---|---|---|
| Instalación/apertura | Abre sin cierre inesperado | Modelo, Android, logcat |
| Permisos denegados | No inicia control/captura | Mensaje mostrado |
| Captura WebView | Imagen no vacía, dimensiones correctas | Captura y perfil |
| Captura Chrome | Consentimiento visible y servicio detenido al revocarlo | Notificación/log |
| Calibración | Coordenadas relativas y perfil independiente por orientación | JSON exportado |
| Cuatro direcciones | Dos observaciones concordantes por movimiento | Eventos action/result + imágenes |
| Movimiento rechazado | Un intento; no un bucle de toques | Registro |
| Seguimiento | Velocidad/dirección concordantes con secuencia real | ≥30 frames etiquetados |
| Colisión | Rechaza vehículo cruzando la trayectoria, aunque el destino quede libre después | Secuencia |
| Pérdida de pollo | No envía un nuevo movimiento | Registro |
| Menú/anuncio | Espera o pausa; nunca se afirma partida nueva sin evidencia | Registro |
| OCR | Puntos correctos, TOP/monedas excluidos | Captura anotada |
| Derrota | Tres coincidencias y una sola estadística por sesión | Sesiones |
| Reinicio | Una acción como máximo y nueva escena+puntuación | Registro |
| Parada | No hay nuevos gestos; un gesto ya aceptado por Android puede acabar | Tiempo medido |
| Rendimiento | FPS y latencia suficientes; pausa si >250 ms | Valores y temperatura |
| Persistencia | Perfil, estadísticas y adaptación sobreviven al cierre | Exportaciones |
| Sesiones prolongadas | Sin crecimiento de memoria ni pérdida de control | 10–30 minutos de mediciones |

Para validar el objetivo final faltan varias partidas completas consecutivas, sin intervención salvo permisos, con puntuaciones obtenidas jugando. Compara estrategias con el mismo dispositivo, suficientes partidas y distribución equivalente de condiciones; informa tamaño de muestra, media, mediana y causas de derrota. No hay una estrategia ganadora demostrada en esta entrega.
