# Investigación de la versión de Silvergames

Fecha: 17 de noviembre de 2026. No se modificaron puntuaciones, memoria ni lógica del juego. Se leyeron recursos públicos para identificar tecnología y se enviaron entradas táctiles normales en Chromium.

## Fuentes y equivalencia

- Página solicitada: https://www.silvergames.com/es/crossy-chicken
- Su iframe público: https://www.silvergames.com/es/crossy-chicken/gameframe
- HTML servido por ese iframe: https://media.silvergames.com/m/crossy-chicken/index.html. Contiene `meta name="generator" content="Construct 3"`. Esta es evidencia directa del motor de esta versión.
- Comparación documental: https://www.desura.com/crossy-chicken identifica HTML5/Construct 3 y atribuye el juego a ivigames.com.
- https://kizi.com/games/crossy-chicken también atribuye su versión a ivigames.com. No se comprobó que ambos portales sirvan el mismo binario o configuración.
- https://codecanyon.net/item/crossy-chicken-html5-game-construct-3/32427908 ofrece un proyecto del mismo nombre con controles táctiles. La coincidencia de nombre/motor no basta para declarar equivalencia con Silvergames.

## Lo observado y lo que falta

| Pregunta | Evidencia / límite |
|---|---|
| Motor y tecnología | Construct 3 confirmado en HTML del juego; canvas/WebGL en Chromium. |
| Android WebView | Hay una implementación real con aceleración gráfica y PixelCopy; la compatibilidad del proveedor debe comprobarse en el WebView del teléfono. Chromium de escritorio no lo demuestra. |
| ¿Requiere Chrome? | No se estableció esa necesidad. Se ofrece Chrome como segunda modalidad. |
| Representación del pollo | Sprite de aspecto tridimensional/isométrico, cuerpo blanco y cresta roja; cambia su aspecto al saltar/girar. |
| Desplazamiento | Saltos por una cuadrícula isométrica, con desplazamiento de cámara. El movimiento en pantalla no equivale directamente al movimiento en el mundo. |
| Duración del salto | Respuestas visibles tras esperas de 250 ms, pero esto NO mide la duración exacta de la animación. La latencia se calibra en el teléfono y se confirma en dos fotogramas. Valor inicial conservador: 350 ms, sin certificar. |
| Coches | Se observaron vehículos de colores cruzando las carreteras. Velocidades físicas y distribuciones de aparición no medidas. |
| Estático/móvil | Rocas y árboles permanecen en el escenario; coches, troncos y cámara se mueven. |
| Ríos/trenes | Agua, troncos y vías visibles en las capturas. La ficha del proveedor describe trenes; no se validó su detección ni un cruce seguro. |
| Puntuación | El marcador superior izquierdo pasó de 0 a 4 tras cuatro toques. Monedas y TOP son indicadores distintos. No se demostró la regla exacta para retroceder/revisitar filas. |
| Derrota | Dejando de actuar, el pollo desapareció y apareció TOP y un botón azul de reproducción. No se cuantificó el tiempo límite. |
| Reinicio | Un toque en el botón azul produjo transición de carga y volvió al menú. No es equivalente a estar ya jugando. El bot exige confirmar retorno visual antes de otro intento. |
| Permanecer quieto | Una prueba de inactividad terminó en derrota. No se promete que esperar sea indefinidamente seguro. |
| Patrones | No hay una serie suficiente para afirmar periodicidad. El programa estima velocidad local, no una secuencia determinista de generación. |
| Aleatoriedad | La disposición cambió entre sesiones observadas; sin estudio suficiente para caracterizar su generador. |

## Resultado de la prueba táctil

Chromium ejecutado a 412×915, escala 1, `isMobile`, `hasTouch` y user-agent Android. Un toque inició la partida. En una secuencia corta se tocaron (380,820), (25,820), (200,890) y (200,750), separadas por 250 ms y capturas. Las cuatro hicieron avanzar el personaje y el marcador llegó a 4. Por tanto, **esta prueba no confirma cuatro zonas direccionales distintas**, ni permite trasladar esas coordenadas a un teléfono. No se distribuye ese mapa como perfil prevalidado.

El calibrador acepta toques, duración de pulsación y deslizamiento. La validación pendiente debe determinar cuál produce cada dirección en el dispositivo real. Nunca se desbloquea automático simplemente por haber dibujado cuatro puntos.

## Comprobación visual reproducible

Plantilla inicial tomada de `playing-base`; segunda apariencia de `playing-back`. Validación independiente sobre `playing-right`, `playing-left` y `playing-up`: el mismo criterio de coincidencia y unicidad localiza al pollo. Dos negativos (`mobile-idle`, `bottom`) son rechazados. Los nombres de los archivos describen las zonas tocadas, NO la dirección efectiva: todos esos toques avanzaron.

Son cinco fotogramas de evaluación, no un conjunto estadísticamente suficiente. Falta ampliar con distintos fondos, sombras, animaciones, escalas, obstáculos blancos, anuncios, varios skins y teléfonos. Se entregan los fotogramas como fixtures de pruebas, no los recursos ni el código del videojuego.

Una prueba adicional con deslizamientos horizontales produjo desplazamientos laterales visibles, con marcador 0. Los gestos verticales de esa prueba coincidieron con carga/menú, por lo que no validan adelante/atrás. El tiempo de entrada de Chromium bajo carga fue de 1,1–1,4 s por gesto y no es una medida útil de latencia del teléfono.

La validación estática del host se puede repetir con `scripts/validate_images.py` y `scripts/requirements-validation.txt` (OpenCV Python 5.0.0.93). La prueba instrumentada utiliza la biblioteca Android OpenCV 4.10.0 incluida en la app; son comprobaciones separadas.
