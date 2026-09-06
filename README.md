# Cliente - Servidor con Java

Proyecto realizado para la materia de Tópicos.

El programa permite establecer comunicación entre dos computadoras conectadas a la misma red utilizando Java y sockets TCP.

Una computadora funciona como servidor y la otra como cliente. Después de establecer la conexión, ambas pueden enviar y recibir varios mensajes sin tener que volver a conectarse después de cada envío.

Para facilitar el uso del programa se agregó una interfaz gráfica desarrollada con Swing.

## Objetivo

Desarrollar una aplicación que permita la comunicación entre dos computadoras dentro de una red local utilizando sockets TCP e hilos en Java.

El uso de hilos permite mantener activa la recepción de mensajes sin bloquear la interfaz gráfica, por lo que el usuario puede continuar escribiendo y enviando mensajes mientras el programa espera información de la otra computadora.

## Estructura del proyecto

El proyecto está organizado de la siguiente manera:

```text
CLIENTE-SERVIDOR
│
├── src
│   ├── Cliente.java
│   └── Servidor.java
│
├── .gitignore
└── README.md
```

La carpeta `src` contiene las clases principales del programa.

## Servidor

La clase `Servidor` se encarga de abrir un puerto de comunicación y esperar la conexión de un cliente.

Desde la interfaz se puede indicar el puerto que se desea utilizar y posteriormente iniciar el servidor.

El programa también muestra las direcciones IP disponibles en la computadora para facilitar la conexión desde otro equipo.

Cuando un cliente se conecta, el servidor mantiene la conexión abierta y puede enviar y recibir varios mensajes.

La interfaz del servidor contiene:

- Campo para indicar el puerto.
- Botón para iniciar o detener el servidor.
- Estado de la conexión.
- Dirección IP de la computadora.
- Área de conversación.
- Campo para escribir mensajes.
- Botón para enviar.

## Cliente

La clase `Cliente` se encarga de establecer la conexión con el servidor.

Para conectarse se debe escribir la dirección IP de la computadora servidor y el puerto que está utilizando.

Una vez realizada la conexión, el cliente puede enviar y recibir mensajes mientras la sesión permanezca activa.

La interfaz del cliente contiene:

- Campo para escribir la IP del servidor.
- Campo para indicar el puerto.
- Botón para conectar o desconectar.
- Estado de la conexión.
- Área de conversación.
- Campo para escribir mensajes.
- Botón para enviar.

## Funcionamiento de la comunicación

La aplicación utiliza una arquitectura cliente-servidor.

```text
Computadora cliente                 Computadora servidor
        |                                    |
        |---------- Conexión --------------->|
        |                                    |
        |---------- Mensaje ---------------->|
        |<--------- Mensaje -----------------|
        |---------- Mensaje ---------------->|
        |---------- Mensaje ---------------->|
        |<--------- Mensaje -----------------|
        |                                    |
```

La conexión permanece abierta mientras las dos aplicaciones continúen conectadas.

Esto permite enviar varios mensajes durante la misma sesión.

## Uso de sockets

La comunicación entre las computadoras se realiza mediante sockets TCP.

En el servidor se utiliza `ServerSocket` para abrir un puerto y esperar conexiones.

```java
ServerSocket servidor = new ServerSocket();
```

Posteriormente se utiliza `accept()` para esperar la conexión de un cliente.

```java
Socket cliente = servidor.accept();
```

En el cliente se utiliza un objeto `Socket` para establecer la conexión utilizando la dirección IP y el puerto del servidor.

```java
Socket socket = new Socket();
```

La conexión se realiza mediante:

```java
socket.connect(
    new InetSocketAddress(ip, puerto)
);
```

## Envío y recepción de mensajes

Para recibir mensajes se utiliza `BufferedReader`.

```java
BufferedReader entrada = new BufferedReader(
    new InputStreamReader(
        socket.getInputStream(),
        StandardCharsets.UTF_8
    )
);
```

Para enviar mensajes se utiliza `PrintWriter`.

```java
PrintWriter salida = new PrintWriter(
    new OutputStreamWriter(
        socket.getOutputStream(),
        StandardCharsets.UTF_8
    ),
    true
);
```

Cada mensaje se envía utilizando:

```java
salida.println(mensaje);
```

Y se recibe utilizando:

```java
entrada.readLine();
```

El salto de línea enviado por `println()` permite que `readLine()` pueda identificar cuándo termina cada mensaje.

## Uso de hilos

Las operaciones de red pueden permanecer esperando información durante varios segundos.

Si estas operaciones se realizaran directamente dentro del mismo hilo de la interfaz, la ventana podría dejar de responder mientras espera una conexión o un mensaje.

Para evitar este problema se utilizan hilos.

Por ejemplo:

```java
Thread hiloConexion = new Thread(() -> {
    // Operaciones de conexión y recepción de mensajes
});
```

Después el hilo comienza su ejecución con:

```java
hiloConexion.start();
```

De esta forma se separan principalmente dos tareas:

```text
Aplicación
   |
   +---- Interfaz gráfica
   |        |
   |        +---- Botones
   |        +---- Campos de texto
   |        +---- Envío de mensajes
   |
   +---- Hilo de comunicación
            |
            +---- Espera conexiones
            +---- Recibe mensajes
            +---- Mantiene activa la comunicación
```

Esto permite que la interfaz continúe funcionando mientras el programa recibe información desde la red.

## Actualización de la interfaz

Swing utiliza un hilo encargado de administrar los componentes gráficos.

Cuando se recibe un mensaje desde otro hilo, la actualización de la interfaz se realiza utilizando:

```java
SwingUtilities.invokeLater(() -> {
    // Actualización de la interfaz
});
```

Esto permite modificar de forma segura elementos como el área de conversación o las etiquetas de estado.

## Tecnologías utilizadas

- Java
- Java Swing
- Sockets TCP
- ServerSocket
- Socket
- Thread
- BufferedReader
- PrintWriter
- Scanner
- Git

## Requisitos

Para ejecutar el proyecto se necesita:

- Java JDK 17 o superior.
- Dos computadoras conectadas a la misma red.

La instalación de Java se puede comprobar con:

```powershell
java -version
```

También se puede comprobar el compilador:

```powershell
javac -version
```

## Compilación

Desde la carpeta principal del proyecto se puede crear una carpeta donde se guardarán los archivos compilados:

```powershell
mkdir out
```

Posteriormente se compilan las clases:

```powershell
javac -encoding UTF-8 -d out src\Servidor.java src\Cliente.java
```

Los archivos compilados se almacenarán dentro de la carpeta `out`.

```text
CLIENTE-SERVIDOR
│
├── out
│   ├── Cliente.class
│   └── Servidor.class
│
├── src
│   ├── Cliente.java
│   └── Servidor.java
│
├── .gitignore
└── README.md
```

La carpeta `out` no se almacena en Git porque contiene archivos generados durante la compilación.

## Ejecución del servidor

Primero se debe iniciar el servidor.

```powershell
java -cp out Servidor
```

Se abrirá la interfaz gráfica.

El puerto predeterminado es:

```text
5000
```

Después se debe presionar:

```text
Iniciar servidor
```

El programa mostrará el estado:

```text
Estado: esperando cliente
```

También mostrará las direcciones IP disponibles en la computadora.

## Dirección IP del servidor

La dirección IP también se puede consultar desde Windows utilizando:

```powershell
ipconfig
```

Se debe buscar la dirección IPv4 correspondiente al adaptador de red que se está utilizando.

Ejemplo:

```text
Dirección IPv4: 172.17.57.87
```

Esa dirección se debe utilizar en la computadora cliente.

Si aparecen varias direcciones, se debe seleccionar la que corresponda a la red donde se encuentran conectadas las dos computadoras.

## Ejecución del cliente

En la segunda computadora se ejecuta:

```powershell
java -cp out Cliente
```

Se abrirá la interfaz gráfica del cliente.

En el campo de dirección IP se escribe la IP del servidor.

Por ejemplo:

```text
172.17.57.87
```

En el puerto se escribe:

```text
5000
```

Después se presiona:

```text
Conectar
```

Si la conexión se realiza correctamente aparecerá:

```text
Estado: conectado
```

## Ejemplo de funcionamiento

Después de establecer la conexión se puede mantener una conversación.

```text
Cliente: Hola
Servidor: Hola

Cliente: ¿Cómo estás?
Servidor: Bien

Cliente: Estoy probando el programa
Servidor: Recibido

Cliente: Mensaje adicional
Servidor: También llegó correctamente
```

Los mensajes continúan enviándose utilizando la misma conexión.

## Prueba en una sola computadora

El programa también puede probarse utilizando una sola computadora.

Primero se ejecuta:

```powershell
java -cp out Servidor
```

Después se abre otra terminal y se ejecuta:

```powershell
java -cp out Cliente
```

En la dirección IP del cliente se utiliza:

```text
127.0.0.1
```

Esta dirección representa la misma computadora.

El puerto debe ser el mismo que se encuentra configurado en el servidor.

## Posibles problemas de conexión

Si el cliente no logra conectarse, se recomienda revisar los siguientes puntos:

- El servidor debe estar iniciado.
- Las dos computadoras deben encontrarse en la misma red.
- La dirección IP debe ser correcta.
- El puerto del cliente debe coincidir con el puerto del servidor.
- El Firewall de Windows debe permitir las conexiones de Java.

Para comprobar si existe comunicación entre las computadoras se puede utilizar:

```powershell
ping DIRECCION_IP
```

Ejemplo:

```powershell
ping 172.17.57.87
```

También se puede comprobar el puerto:

```powershell
Test-NetConnection 172.17.57.87 -Port 5000
```

Si la conexión está disponible deberá aparecer:

```text
TcpTestSucceeded : True
```

## Conclusión

Con este programa se implementó una comunicación entre dos computadoras utilizando sockets TCP en Java.

La conexión permanece activa para permitir el intercambio de varios mensajes entre el cliente y el servidor.

También se utilizaron hilos para realizar las operaciones de red sin bloquear la interfaz gráfica, permitiendo que el usuario pueda continuar utilizando la aplicación mientras se reciben mensajes.

La interfaz desarrollada con Swing permite observar de manera más clara el estado de la conexión y la conversación entre ambas computadoras.