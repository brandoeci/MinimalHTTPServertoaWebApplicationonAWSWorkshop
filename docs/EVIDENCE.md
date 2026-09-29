# Evidencias y resultados

Ejecución local del artefacto empaquetado
(`java -jar minihttp-server.jar 35000`, recursos leídos desde el `.jar`),
29 de septiembre de 2026.

* Transcripción completa de `curl`: [local-evidence.txt](local-evidence.txt)
* Peticiones vistas en el navegador: [network-requests.txt](network-requests.txt)
* Capturas: [img/](img)

## 1. Pruebas automáticas

```
$ mvn test
...
Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Clase | Pruebas |
|---|---|
| `HttpServerIntegrationTest` | 10 |
| `ServicesTest` | 10 |
| `StaticFilesTest` | 8 |
| `HttpRequestTest` | 6 |
| `UrlsTest` | 6 |
| `JsonTest` | 5 |
| `HttpServerRoutingTest` | 4 |
| `MimeTypesTest` | 3 |

## 2. Matriz funcional (sección 6.1 de la guía)

| Prueba | Observación | Evidencia |
|---|---|---|
| Cargar la página de inicio | HTML, CSS, JavaScript y las dos imágenes cargan con `200` y el tipo correcto | [01](img/01-home-page-local.png), [network-requests.txt](network-requests.txt) |
| Saludo válido | El área de resultado cambia sin recargar la página | [02](img/02-greeting-service.png) |
| Número válido | `12.5` → `156.25`, respondido en 53 ms | [03](img/03-square-service.png) |
| Número inválido | `cuarenta` → `400` del servidor, mostrado como *"'cuarenta' is not a number (HTTP 400)"* | [05](img/05-invalid-number-error.png) |
| Hora del servidor | `2026-09-29T15:42:26-05:00` (zona `America/Bogota`), distinta de la del navegador en UTC | [04](img/04-server-time-service.png) |
| Archivo estático inexistente | `404 Not Found` con página de error | [06](img/06-404-missing-file.png) |
| Método no soportado | `POST /` → `405 Method Not Allowed` con `Allow: GET` | [local-evidence.txt](local-evidence.txt) |
| Intento de salir del área pública | `403 Forbidden`, no se entrega ningún archivo externo | [07](img/07-403-path-traversal.png) |
| Peticiones repetidas | 10 operaciones seguidas con `200` en una sola ejecución del servidor | [local-evidence.txt](local-evidence.txt) |
| Petición malformada | `NOT AN HTTP REQUEST` → `400` y el servidor sigue vivo (la siguiente petición responde `200`) | [local-evidence.txt](local-evidence.txt) |

### Detalle de los recursos estáticos

| Recurso | Estado | Content-Type | Content-Length |
|---|---|---|---|
| `/` | 200 | `text/html; charset=utf-8` | 3436 |
| `/styles.css` | 200 | `text/css; charset=utf-8` | 2725 |
| `/app.js` | 200 | `application/javascript; charset=utf-8` | 6361 |
| `/img/logo.png` | 200 | `image/png` | 5918 |
| `/img/photo.jpg` | 200 | `image/jpeg` | 18747 |
| `/img/favicon.png` | 200 | `image/png` | 1002 |

El largo declarado coincide con el tamaño del archivo en disco; lo verifica
también la prueba `servesImagesByteForByte`.

### Detalle de los servicios

| Petición | Estado | Cuerpo |
|---|---|---|
| `/app/hello?name=Ana%20Mar%C3%ADa` | 200 | `{"service":"greeting","name":"Ana María","greeting":"Hello, Ana María!"}` |
| `/app/hello` | 400 | `{"error":"Bad Request","status":400,"message":"Missing required query parameter 'name'"}` |
| `/app/square?value=7` | 200 | `{"service":"square","value":7,"square":49}` |
| `/app/square?value=hola` | 400 | `{"error":"Bad Request","status":400,"message":"'hola' is not a number"}` |
| `/app/square?value=1e200` | 400 | `{"error":"Bad Request","status":400,"message":"The value is too large to be squared"}` |
| `/app/time` | 200 | `{"service":"time","iso":"...","zone":"America/Bogota","epochMillis":...}` |
| `/health` | 200 | `{"status":"UP","uptimeMillis":...}` |
| `/app/slow?millis=999999` | 400 | `{"error":"Bad Request","status":400,"message":"The delay must be between 0 and 20000 milliseconds"}` |

## 3. El límite secuencial (sección 6.2 de la guía)

**Predicción antes de medir:** el segundo usuario debería esperar a que termine
la petición del primero, porque el bucle no vuelve a `accept()` hasta despachar
la conexión que tiene en la mano.

**Medición con dos clientes de terminal**

```
A  GET /app/slow?millis=5000 -> HTTP 200  arranca en +0.0s  total 5.003185s
B  GET /app/time             -> HTTP 200  arranca en +0.3s  total 4.673475s
```

**Medición con dos ventanas del navegador**

| Momento | Evento |
|---|---|
| 20:42:06.708 | Ventana **A** lanza `GET /app/slow?millis=20000` |
| 20:42:19.398 | Ventana **B** lanza `GET /app/time` (el servidor lleva 12.7 s ocupado) |
| 20:42:26.711 | El servidor termina con A y **solo entonces** responde a B |

La ventana B reportó **7315 ms** para una petición que normalmente responde en
4 ms ([captura 10](img/10-window-b-waited-for-the-server.png)).

**Interpretación.** Las dos ventanas siguieron vivas todo el tiempo: se podía
hacer scroll, escribir en los campos y el estado de carga se veía
([captura 08](img/08-loading-state-window-a.png)). Eso es lo que da `fetch()`:
un **cliente asíncrono**. Pero el servidor nunca fue **concurrente**: la conexión
de B quedó esperando en la cola de aceptación del sistema operativo hasta que el
hilo único terminó con A. Asincronía en el navegador y concurrencia en el
servidor son cosas distintas, y solo la segunda aumenta la capacidad.

La prueba automática `requestsAreServedSequentially` deja esto fijado: dos
peticiones lentas de 1.5 s lanzadas casi al tiempo tardan en total más de 3 s.

## 4. Evidencias pendientes

| Evidencia | Estado |
|---|---|
| Captura del panel *Network* de las herramientas de desarrollo | Pendiente: se puede tomar con F12 → Network al recargar la página; la lista de peticiones ya está en [network-requests.txt](network-requests.txt) |
| Aplicación corriendo en la dirección pública de EC2 | Pendiente de la cuenta de AWS del curso; el procedimiento está en el README y el artefacto ya fue verificado localmente |
| Captura del security group con las reglas de entrada | Pendiente, junto con el despliegue |
