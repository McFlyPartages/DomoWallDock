# DomoWallDock

<p align="center">
  <a href="README.md"><img src="docs/images/flags/fr.svg" width="32" alt="Français"></a>&nbsp;
  <a href="README.en.md"><img src="docs/images/flags/gb.svg" width="32" alt="English"></a>&nbsp;
  <a href="README.de.md"><img src="docs/images/flags/de.svg" width="32" alt="Deutsch"></a>&nbsp;
  <a href="README.es.md"><img src="docs/images/flags/es.svg" width="32" alt="Español"></a>
</p>

[![Licencia GPL-3.0](https://img.shields.io/badge/licencia-GPL--3.0-64748b)](LICENSE)
[![Android 7+](https://img.shields.io/badge/Android-7%2B-3ddc84)](#descarga)
[![Última versión](https://img.shields.io/github/v/release/McFlyPartages/DomoWallDock?include_prereleases&label=versi%C3%B3n)](https://github.com/McFlyPartages/DomoWallDock/releases)

**Convierte una tableta Android en un panel de pared para [Home Assistant](https://www.home-assistant.io/) o [Gladys Assistant](https://gladysassistant.com/).**

DomoWallDock (antes GladysWallDock) es un fork de [WallDock](https://github.com/psycho1807/WallDock), de psycho1807. WallDock está pensado para Home Assistant; todo lo que sabe hacer sigue aquí, y se añade Gladys Assistant. Licencia GPL-3.0, como el original.

> 📘 **Guía ilustrada: [usar DomoWallDock con Gladys Assistant](docs/TUTO-GLADYS.es.md)** — instalación, conexión, MQTT, gestión de la carga, salvapantallas.
> La app está en francés, inglés, alemán y español: el idioma de la tableta por defecto, o el que elijas en los ajustes.

## Descarga

Los APK firmados están en [**Releases**](https://github.com/McFlyPartages/DomoWallDock/releases) (coge la más reciente; son versiones preliminares).

| Tu tableta | Archivo que descargar | Rama |
|---|---|---|
| **Android 8 o más reciente** (caso general) | `DomoWallDock-x.y.z.apk` | `main` |
| **Android 7.x** (tabletas antiguas) | `DomoWallDock-x.y.z-android7.apk` | `android7` |

- La versión `android7` es una rama **congelada**: solo recibe correcciones, trasladadas a mano. En Android 8+, usa siempre la versión estándar.
- Las dos versiones están firmadas con la **misma clave**: una nueva versión se instala encima de la anterior y se conservan los ajustes.
- Para instalarlas: permite «orígenes desconocidos» para el navegador o el gestor de archivos que uses, abre el archivo y sigue [Instalación](#instalación).

## Compatibilidad Home Assistant / Gladys Assistant

| Función | Home Assistant | Gladys Assistant |
|---|:---:|:---:|
| Panel a pantalla completa, modo quiosco, dock de apps, vuelta automática | ✅ | ✅ |
| Inicio de sesión automático (usuario y contraseña) | ✅ ¹ | ✅ |
| Acceso local, externo o ambos (cambio automático) | ✅ | ✅ |
| Salvapantallas: reloj, álbum de Immich, carpeta de fotos, página de la plataforma | ✅ | ✅ |
| Pantalla apagada cada noche, encendido por la cámara frontal | ✅ | ✅ |
| Calendario a pantalla completa (día, semana, mes, agenda) | ✅ | ✅ |
| Alarmas en la tableta | ✅ (también controlables desde HA) | ✅ (ajustadas en la app) |
| Sensores de la tableta: batería, carga, movimiento, pantalla | ✅ (ESPHome o MQTT) | ✅ (MQTT) |
| Gestión de la carga con un enchufe inteligente | ✅ (MQTT + automatización) | ✅ (MQTT + escenas) |
| Reproductor de Music Assistant sincronizado (Sendspin) | ✅ | ✅ ² |
| Control completo desde la plataforma (brillo, abrir una app o una página, alertas, cámaras a pantalla completa, sonidos, síntesis de voz…) | ✅ (ESPHome) | ➖ pantalla, recargar, volver al panel |
| Asistente de voz Jarvis («Hey Jarvis») | ✅ | ❌ |
| Proxy Bluetooth de ESPHome | ✅ | ❌ |

¹ No con la autenticación en dos pasos. ² Music Assistant es un servidor aparte: funciona con o sin Home Assistant.
Cuando eliges Gladys, la app desmarca sola Jarvis, el proxy Bluetooth y Music Assistant (puedes volver a marcarlos, por ejemplo si también usas Home Assistant o un servidor Music Assistant).

## Qué hace DomoWallDock

**Home Assistant o Gladys, a elegir**
- Ajustes → «Panel mostrado»: un botón de opción elige la plataforma que se inicia. Cada una tiene su columna (una al lado de la otra en una pantalla ancha o una tableta en horizontal, una debajo de la otra si no): dirección, cuenta, contraseña y panel predeterminado (opcional, p. ej. `/lovelace/0?kiosk` o `/dashboard/salon`). En Gladys, la casilla **Bloquear** limita la pantalla a esa página (las demás páginas de Gladys y los enlaces vuelven a ella) y el botón **Pantalla completa** añade `?fullscreen=force` (o `&fullscreen=force` si la dirección ya tiene un parámetro): Gladys se abre en pantalla completa, sin menú ni botón para salir.
- **Acceso Local / Externo / Ambos** para cada plataforma: dirección local (HTTP o HTTPS, IP y puerto) y dirección externa (HTTPS). Con «Ambos», la app usa la dirección local si responde y, si no, la externa: la tableta funciona también fuera de casa, por ejemplo detrás de un proxy inverso. Una comprobación de estado prueba cada dirección.
- **Inicio de sesión automático**: con un usuario y una contraseña, la tableta inicia sesión sola cuando aparece la página de inicio de sesión (Home Assistant: como «Mantener la sesión iniciada»; no con la autenticación en dos pasos). Consejo: una cuenta dedicada a la tableta, sin derechos de administración.
- Las funciones que pasan por Home Assistant (Jarvis, proxy Bluetooth, cámaras, sonidos) siguen disponibles cuando se muestra Gladys, mientras la dirección de HA esté rellenada.

**Proxy Bluetooth de ESPHome** (Home Assistant)
- Ajustes → «Proxy Bluetooth (ESPHome)»: la tableta escucha los dispositivos Bluetooth Low Energy de su alrededor (termómetros Xiaomi, sensores BTHome, balizas…) y transmite sus anuncios a Home Assistant mediante la integración ESPHome, exactamente como un módulo ESPHome `bluetooth_proxy`. Los dispositivos aparecen en HA como si estuvieran al alcance de su propio Bluetooth.
- Solo escucha: HA no puede *conectarse* a los dispositivos a través de la tableta (dispositivos que necesitan una conexión GATT, emparejamiento).
- Los permisos de Bluetooth y ubicación se piden al guardar (sin ubicación, Android oculta las balizas). La búsqueda solo funciona mientras HA está suscrito.
- Gladys no puede usar un proxy Bluetooth: esta opción solo sirve con Home Assistant.

**Con Gladys Assistant**
- **Conexión por MQTT** (común a las dos plataformas): ajustes → «MQTT», con la dirección del broker, el usuario y la contraseña (en Gladys: Integraciones → MQTT → Configuración). La tableta se anuncia sola (formato de descubrimiento MQTT de Home Assistant, que entienden las versiones recientes de Gladys 5): Integraciones → MQTT → dispositivos descubiertos, añadir «DomoWallDock …».
- En Gladys: batería, temperatura de la batería, cargando, movimiento, pantalla (encender / apagar), botones «Recargar» y «Volver al panel».
- **Gestión de la carga** para una tableta siempre enchufada: ajustes → «Batería». Android no permite a una app cortar la carga; por eso DomoWallDock publica un sensor «Carga solicitada» (sí por debajo del umbral bajo, 30 % por defecto; no por encima del umbral alto, 80 % por defecto) y dos escenas de Gladys controlan el enchufe inteligente del cargador:
  - «Carga solicitada» pasa a 1 → encender el enchufe;
  - «Carga solicitada» pasa a 0 → apagar el enchufe.

  Si la tableta desaparece de la red (cuelgue, Wi-Fi caído), el broker vuelve a poner sola «Carga solicitada» en sí (último deseo MQTT), para que la batería no se agote.
- **Pantalla controlada por la cámara frontal**: la pantalla se enciende cuando alguien pasa y se apaga tras un tiempo sin movimiento ni toque (ajustes → detección de movimiento → tiempo en segundos). Las imágenes se analizan en la tableta y nunca se envían ni se graban.

**Quiosco** (heredado de WallDock)
- Pantalla completa, barras del sistema ocultas, pantalla siempre encendida, ajustes protegidos por un código.
- Dock de apps permitidas (el botón de Jarvis solo aparece si Jarvis está activado), vuelta automática al panel tras la inactividad.
- Puede ser la pantalla de inicio de la tableta; se vuelve a iniciar al arrancar (corte de luz).
- Zoom del panel (de 50 a 200 %), salvapantallas (reloj, página a elegir, presentación de Immich o carpeta de fotos).
- Horario: pantalla apagada cada noche. Lista de direcciones permitidas. Pantalla de emergencia cuando el servidor no responde.
- Copia de seguridad y restauración de los ajustes en un archivo (se aceptan las copias de WallDock).

**Con Home Assistant** (heredado de WallDock, sigue funcionando)
- Integración nativa ESPHome cifrada, asistente de voz Jarvis, reproductor multimedia, reproductor de Music Assistant sincronizado (Sendspin), alarmas, calendario. Estas funciones necesitan Home Assistant; al elegir Gladys, Jarvis, el proxy Bluetooth y Music Assistant se desmarcan automáticamente (puedes volver a marcarlos).

## Temas MQTT

`<dispositivo>` vale `domowalldock-xxxxxx` (se muestra en los ajustes).

| Tema | Sentido | Valores |
|---|---|---|
| `domowalldock/<dispositivo>/battery` | estado | 0 a 100 |
| `domowalldock/<dispositivo>/battery_temperature` | estado | °C |
| `domowalldock/<dispositivo>/charging` | estado | `ON` / `OFF` |
| `domowalldock/<dispositivo>/charge_wanted` | estado | `ON` / `OFF` (si la gestión de la carga está activada) |
| `domowalldock/<dispositivo>/motion` | estado | `ON` / `OFF` |
| `domowalldock/<dispositivo>/screen` | estado | `ON` / `OFF` |
| `domowalldock/<dispositivo>/screen/set` | orden | `ON` / `OFF` |
| `domowalldock/<dispositivo>/reload/set` | orden | cualquier valor |
| `domowalldock/<dispositivo>/dashboard/set` | orden | cualquier valor |

## Instalación

1. Descarga el APK adecuado para tu tableta (ver [Descarga](#descarga)) e instálalo (permite la instalación desde este origen). La app tiene su propio identificador (`fr.mcflypartages.domowalldock`): puede convivir con WallDock.
2. En el primer inicio: plataforma (Home Assistant o Gladys), su dirección (y si hace falta cuenta, contraseña, panel), código de administrador, apps del dock y luego **Guardar**.
3. En los ajustes: pantalla de inicio, mostrar sobre otras apps, cámara (detección de movimiento) y luego la parte MQTT.
4. Desactiva la optimización de batería de Android para DomoWallDock; si no, el sistema puede cerrar la app ([dontkillmyapp.com](https://dontkillmyapp.com) detalla cada marca).

**Actualizaciones**: a mano, instalando el nuevo APK encima del anterior (misma clave de firma, se conservan los ajustes). La app no se actualiza sola.

## Compilación

`gradle assembleGithubRelease` con Gradle 8.14, JDK 17 y el SDK de Android 36. Para firmar, indica la ruta del almacén de claves (PKCS12, alias `gladyswalldock`, nombre original de la clave) en `WALLDOCK_KEYSTORE` y su contraseña en `WALLDOCK_KEY_PASSWORD`; la clave no está en el repositorio. Sin ellas, el APK no está firmado (`assembleGithubDebug` da un APK de prueba).

El código conserva el paquete `app.walldock` del original, para recuperar fácilmente sus cambios. El cliente MQTT (`app/src/main/java/app/walldock/mqtt`) y el servidor ESPHome (`…/esphome`) son Java puro y se prueban en una JVM. `./gradlew testGithubDebugUnitTest` dibuja la pantalla de ajustes en PNG (Robolectric) en `app/build/preview`.

## Código fuente y repositorios

El desarrollo se hace en un Forgejo personal; este repositorio de GitHub es una **réplica** (ramas `main` y `android7`, y las etiquetas). Los APK de las Releases se publican en los dos sitios. Los cambios pasan por *pull requests* en la forja de origen y luego llegan aquí.

## Créditos

- **DomoWallDock**: adaptado y mantenido por **McFlyPartages**.

  <a href="https://mcflypartages.fr"><img src="docs/images/social/web.svg" width="36" alt="Sitio web mcflypartages.fr" title="Sitio web mcflypartages.fr"></a> <a href="https://mastodon.social/@McFlyPartages"><img src="docs/images/social/mastodon.svg" width="36" alt="Mastodon @McFlyPartages@mastodon.social" title="Mastodon @McFlyPartages@mastodon.social"></a> <a href="https://www.facebook.com/McFlyPartages"><img src="docs/images/social/facebook.svg" width="36" alt="Facebook McFlyPartages" title="Facebook McFlyPartages"></a> <a href="https://x.com/McFlyPartages"><img src="docs/images/social/x.svg" width="36" alt="X @McFlyPartages" title="X @McFlyPartages"></a> <a href="https://fr.tipeee.com/mcflypartages"><img src="docs/images/social/tipeee.svg" width="36" alt="Apoyar en Tipeee" title="Apoyar en Tipeee"></a> <a href="https://ko-fi.com/mcflypartages"><img src="docs/images/social/kofi.svg" width="36" alt="Apoyar en Ko-fi" title="Apoyar en Ko-fi"></a>

  ¿Te gusta la app? Un pequeño apoyo en Tipeee o Ko-fi siempre se agradece.

- **[WallDock](https://github.com/psycho1807/WallDock)** de psycho1807 (GPL-3.0): toda la base de la app. DomoWallDock no está afiliado a su autor ni cuenta con su apoyo; WallDock Pro es un producto distinto.
- Palabra «Stop»: modelo [microWakeWord](https://github.com/kahrendt/microWakeWord) de Kevin Ahrendt (Apache 2.0); extracción de características portada del micro frontend de TensorFlow Lite Micro (Apache 2.0).
- Palabra de activación: modelos [openWakeWord](https://github.com/dscripka/openWakeWord) (descargados en el primer uso, CC BY-NC-SA 4.0), ejecutados con TensorFlow Lite.
- Protocolos: [API nativa de ESPHome](https://esphome.io/components/api.html), descubrimiento MQTT de Home Assistant.
- Iconos: [Simple Icons](https://simpleicons.org/) (CC0), [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache 2.0), [flag-icons](https://github.com/lipis/flag-icons) (MIT).
