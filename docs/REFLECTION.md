# Preguntas de discusión

Respuestas a la sección 8.2 de la guía, apoyadas en lo que se observó al correr
la aplicación (ver [EVIDENCE.md](EVIDENCE.md)).

## 1. ¿Por qué una sola página HTML provoca varias peticiones HTTP?

Porque el documento HTML no trae adentro sus recursos: trae **referencias** a
ellos. El navegador primero pide el documento (`GET /`), lo empieza a leer y, a
medida que encuentra `<link>`, `<script>` e `<img>`, va pidiendo cada archivo en
una petición aparte. Cargar la página de este laboratorio son seis peticiones:

```
GET /                 200  text/html
GET /styles.css       200  text/css
GET /img/logo.png     200  image/png
GET /img/photo.jpg    200  image/jpeg
GET /app.js           200  application/javascript
GET /img/favicon.png  200  image/png
```

Es decir que un solo usuario que "abre la página" ya son seis clientes desde el
punto de vista del servidor, antes de que llame a un solo servicio. Con 100
usuarios son 600 peticiones, y en un servidor secuencial eso se atiende en fila.

## 2. ¿Por qué las imágenes deben tratarse como bytes y no como texto?

Porque un PNG o un JPEG no son texto: son bytes arbitrarios que no corresponden a
caracteres válidos en ninguna codificación. Si se leyeran como `String` en UTF-8,
cada byte inválido se reemplazaría por el carácter de sustitución y la imagen
llegaría corrupta al navegador.

Además `Content-Length` debe ser el número de **bytes**, no de caracteres: en
UTF-8 un carácter puede ocupar de 1 a 4 bytes, así que contar caracteres da un
largo equivocado y el navegador se queda esperando datos que no llegan (o corta
la respuesta). En este proyecto el cuerpo de `HttpResponse` siempre es `byte[]`,
así que texto e imágenes recorren el mismo camino y el largo siempre sale del
arreglo. La prueba `servesImagesByteForByte` compara el tamaño del archivo en
disco contra el `Content-Length` de la respuesta.

## 3. ¿Cuál es el papel del `Content-Type`?

Es lo que le dice al navegador **cómo interpretar** los bytes que recibió. El
mismo flujo de bytes puede terminar siendo una página que se dibuja, un script
que se ejecuta o una imagen que se decodifica, según lo que diga ese encabezado.

Se ve fácil al equivocarlo: si `/index.html` se enviara como `text/plain`, el
navegador mostraría el código fuente en vez de la página; si `app.js` no se
enviara como JavaScript, el navegador se niega a ejecutarlo. En los servicios el
tipo es `application/json`, que es lo que permite que `response.json()` del
cliente funcione. También lleva el juego de caracteres (`charset=utf-8`), y por
eso "Ana María" se ve bien en la respuesta del saludo.

## 4. ¿Qué está quemado en este diseño y qué generalizaría un framework?

Quemado está el enrutamiento: cinco condiciones explícitas en `route()` que
comparan la ruta contra un literal (`/app/hello`, `/app/square`, `/app/time`,
`/app/slow`, `/health`) y, si ninguna coincide, tratan la ruta como archivo
estático. También están quemados los nombres de los parámetros (`name`, `value`,
`millis`), la validación de cada servicio y la forma del JSON de salida.

Un framework (Spring, JAX-RS, o el micro-framework que se construye más adelante
en el curso) generalizaría exactamente eso:

* un registro de rutas (`Map<String, Handler>` o anotaciones leídas por reflexión)
  en lugar de la cadena de `if`;
* patrones de ruta con variables (`/app/square/{value}`);
* conversión automática de parámetros a tipos y validación declarativa;
* serialización automática de objetos a JSON;
* manejo centralizado de errores y negociación de contenido.

Escribirlo a mano primero deja ver que un framework no hace magia: hace esto
mismo, solo que sin que el mecanismo se note.

## 5. ¿Por qué el navegador sigue respondiendo mientras el servidor atiende de a uno?

Porque son dos cosas distintas. `fetch()` no bloquea el hilo de la interfaz: la
petición se va, el navegador sigue dibujando, respondiendo al scroll y a los
clics, y cuando la respuesta llega se ejecuta la continuación de la función
`async`. La página se queda viva y muestra su estado de carga.

Eso es **asincronía en el cliente**, y no cambia en nada lo que pasa del otro
lado: el servidor sigue teniendo una sola ventanilla. En la captura del estado de
carga la página está perfectamente viva mientras el servidor está ocupado 20
segundos con otra petición.

## 6. ¿Qué cambió al mover el servidor a EC2 y qué no cambió?

**Cambia** dónde corre el proceso y quién puede alcanzarlo: la dirección deja de
ser `localhost` y pasa a ser una IP pública; aparece un firewall (el security
group) que hay que abrir explícitamente en el puerto de la aplicación; aparece
latencia de red real; el proceso debe sobrevivir al cierre de la sesión SSH (por
eso la unidad de systemd) y los logs dejan de verse en la terminal propia.
También cambian los recursos de la máquina y aparece un costo por hora.

**No cambia** la arquitectura: el mismo `.jar`, el mismo código, el mismo bucle
secuencial, las mismas rutas quemadas, el mismo cliente. Y sobre todo no cambia
el límite: sigue siendo **una instancia y una conexión a la vez**. La nube movió
el servidor de lugar; no lo volvió escalable.

## 7. ¿Qué pasa cuando dos usuarios mandan peticiones lentas casi al mismo tiempo?

Se encolan: el segundo espera a que el primero termine. Está medido de dos
maneras.

Con dos clientes de terminal:

```
A  GET /app/slow?millis=5000 -> HTTP 200  arranca en +0.0s  total 5.003s
B  GET /app/time             -> HTTP 200  arranca en +0.3s  total 4.673s
```

`B` pide la hora, que normalmente responde en 4 ms, y tarda 4.6 segundos.

Con dos ventanas del navegador: la ventana A lanzó `/app/slow?millis=20000` a las
20:42:06.7; la ventana B pidió `/app/time` a las 20:42:19.4 y solo fue atendida a
las 20:42:26.7, justo cuando A terminó: **7315 ms de espera**. La conexión de B
ni siquiera fue aceptada antes; quedó esperando en la cola del sistema operativo.

Si ese trabajo lento fuera real (una consulta pesada, un archivo grande), cada
usuario nuevo sumaría su espera a la de todos los anteriores. El tiempo de
respuesta no se degrada poco a poco: crece de forma acumulativa.

## 8. ¿Cuál es la siguiente limitación que se debe atacar, y por qué la concurrencia va antes que el balanceo de carga?

La siguiente limitación es la concurrencia: que el servidor pueda atender varias
conexiones a la vez (un hilo por conexión, un *thread pool*, o E/S no bloqueante).
Hoy la máquina está casi siempre ociosa mientras espera: durante los 20 segundos
del servicio lento la CPU no hace nada y aun así nadie más es atendido. Es
capacidad desperdiciada dentro de una sola máquina.

La concurrencia va primero por dos razones:

1. **Costo y desperdicio.** Balancear carga entre varias instancias secuenciales
   es pagar más máquinas para un problema que se resuelve usando bien la que ya
   se tiene. Si una instancia atiende 1 petición a la vez, dos instancias atienden
   2: el balanceador multiplica por el número de servidores, mientras que hacer
   concurrente una sola instancia multiplica por un factor mucho mayor.
2. **El balanceador no arregla el cuello de botella.** Repartir el tráfico entre
   réplicas que siguen atendiendo de a una solo mueve la fila de lugar; una
   petición lenta sigue bloqueando a todas las que caigan en esa réplica.

Además, volver concurrente el servidor obliga a resolver antes lo que el balanceo
da por hecho: que los manejadores no compartan estado mutable. Este servidor ya
es *stateless* (nada se guarda entre peticiones), así que está listo para ese
paso. Solo después de eso tiene sentido replicar, poner un balanceador delante y
hablar de escalamiento horizontal.
