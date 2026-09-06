# Cliente - Servidor con Java

Proyecto realizado para la materia de Tópicos.

El programa permite establecer comunicación entre varias computadoras conectadas a la misma red local utilizando Java, sockets TCP e hilos.

Una computadora funciona como servidor y las demás se conectan como clientes. Cada cliente puede escribir un nombre para identificarse dentro del chat y enviar mensajes al grupo. El servidor recibe cada mensaje y lo distribuye a todas las computadoras que se encuentren conectadas.

La aplicación cuenta con una interfaz gráfica desarrollada con Swing para facilitar la conexión y el envío de mensajes.

## Objetivo

Desarrollar una aplicación de mensajería que permita comunicar varias computadoras dentro de una red local mediante una arquitectura cliente-servidor.

El programa utiliza hilos para atender varias conexiones al mismo tiempo y para mantener la recepción de mensajes sin bloquear la interfaz gráfica.

## Estructura del proyecto

El proyecto se encuentra organizado de la siguiente manera:

```text
CLIENTE-SERVIDOR
│
├── src
│   ├── Cliente.java
│   ├── Servidor.java
│   └── RedUtil.java
│
├── .gitignore
└── README.md
```

La carpeta `src` contiene el código fuente de la aplicación.

## Servidor

La clase `Servidor` se encarga de abrir el puerto de comunicación y aceptar las conexiones de las computadoras cliente.

Cuando un cliente se conecta, el servidor crea un hilo independiente para atenderlo. Esto permite que varios clientes permanezcan conectados al mismo tiempo sin que uno bloquee la comunicación de los demás.

El servidor mantiene una lista con los clientes activos. Cuando recibe un mensaje, agrega el nombre del usuario que lo envió y distribuye el mensaje a todos los clientes conectados.

La interfaz del servidor contiene:

- Campo para indicar el puerto.
- Botón para iniciar o detener el servidor.
- Estado del servidor.
- Direcciones IP disponibles en la computadora.
- Área de conversación grupal.
- Campo para escribir mensajes.
- Botón para enviar mensajes a todos los clientes.

También muestra la cantidad de clientes conectados en ese momento.

Ejemplo:

```text
Estado: activo - 3 clientes conectados
```

## Cliente

La clase `Cliente` se encarga de establecer la conexión con el servidor.

Cada cliente debe indicar:

- Nombre con el que aparecerá en el chat.
- Dirección IP del servidor.
- Puerto utilizado por el servidor.

Después de conectarse, el cliente mantiene un hilo escuchando los mensajes enviados por el servidor.

La interfaz del cliente contiene:

- Campo para escribir el nombre del usuario.
- Campo para escribir la IP del servidor.
- Campo para indicar el puerto.
- Botón para conectar o desconectar.
- Estado de la conexión.
- Área de conversación grupal.
- Campo para escribir mensajes.
- Botón para enviar.

## RedUtil

La clase `RedUtil` contiene métodos auxiliares relacionados con la red.

Su función principal es consultar las interfaces de red de la computadora servidor y obtener las direcciones IPv4 disponibles.

Estas direcciones se muestran en la interfaz para que los clientes sepan cuál deben utilizar al conectarse.

## Funcionamiento general

La comunicación utiliza una arquitectura cliente-servidor.

```text
                         Servidor
                       Puerto 5000
                           |
             +-------------+-------------+
             |             |             |
         Cliente 1     Cliente 2     Cliente 3
         Fernando         Ana           José
             |             |             |
             +------ Conversación -------+
```

Todos los mensajes pasan primero por el servidor.

Por ejemplo, si Fernando escribe:

```text
Hola a todos
```

El cliente envía el mensaje al servidor.

El servidor lo recibe y lo distribuye al resto de las conexiones utilizando el nombre del usuario:

```text
Fernando: Hola a todos
```

Todos los clientes conectados reciben el mismo mensaje.

## Uso de sockets

El servidor utiliza `ServerSocket` para abrir un puerto y esperar conexiones.

```java
ServerSocket servidor = new ServerSocket();
```

El puerto se asigna utilizando:

```java
servidor.bind(new InetSocketAddress(puerto));
```

Para aceptar una nueva conexión se utiliza:

```java
Socket cliente = servidor.accept();
```

Cada cliente utiliza un objeto `Socket` para conectarse con la dirección IP y el puerto del servidor.

```java
Socket socket = new Socket();
```

La conexión se realiza con:

```java
socket.connect(
    new InetSocketAddress(ip, puerto),
    5000
);
```

## Envío y recepción de mensajes

Para recibir información se utiliza `BufferedReader`.

```java
BufferedReader entrada = new BufferedReader(
    new InputStreamReader(
        socket.getInputStream(),
        StandardCharsets.UTF_8
    )
);
```

Para enviar información se utiliza `PrintWriter`.

```java
PrintWriter salida = new PrintWriter(
    new OutputStreamWriter(
        socket.getOutputStream(),
        StandardCharsets.UTF_8
    ),
    true
);
```

Los mensajes se envían con:

```java
salida.println(mensaje);
```

Y se reciben utilizando:

```java
entrada.readLine();
```

Se utiliza UTF-8 para manejar correctamente los caracteres de los mensajes.

## Identificación de los mensajes

Para distinguir la información que viaja entre cliente y servidor se utilizan pequeños prefijos de texto.

```text
HELLO   Nombre del cliente
MSG     Mensaje de la conversación
SYS     Información del sistema
```

Cuando un cliente se conecta, primero envía su nombre.

Después, los mensajes normales se envían con el identificador `MSG`.

El servidor agrega el nombre del usuario antes de distribuir el mensaje a todos los clientes.

## Uso de hilos

El uso de hilos es importante porque el servidor debe atender varias computadoras al mismo tiempo.

El servidor mantiene un hilo encargado de aceptar conexiones y crea un hilo independiente por cada cliente conectado.

```text
Servidor
   |
   +---- Hilo principal del servidor
   |        |
   |        +---- Espera nuevas conexiones
   |
   +---- Hilo cliente 1
   |        |
   |        +---- Recibe mensajes de Fernando
   |
   +---- Hilo cliente 2
   |        |
   |        +---- Recibe mensajes de Ana
   |
   +---- Hilo cliente 3
            |
            +---- Recibe mensajes de José
```

Un hilo se crea de la siguiente forma:

```java
Thread hiloCliente = new Thread(() -> {
    // Comunicación con el cliente
});
```

Y comienza su ejecución utilizando:

```java
hiloCliente.start();
```

Gracias a esto, un cliente puede estar enviando información mientras los demás continúan utilizando el chat.

El cliente también utiliza un hilo para permanecer escuchando los mensajes que llegan desde el servidor sin bloquear la ventana.

## Lista de clientes conectados

El servidor necesita conservar las conexiones activas para poder distribuir los mensajes.

Para esto se utiliza:

```java
CopyOnWriteArrayList
```

Esta lista permite trabajar de forma segura cuando diferentes hilos agregan, eliminan o recorren los clientes conectados.

Cuando un cliente se desconecta, su conexión se elimina de la lista y los demás usuarios pueden continuar utilizando el programa.

## Interfaz gráfica y Swing

La interfaz fue desarrollada utilizando Java Swing.

Las operaciones de red se realizan en hilos diferentes al hilo que controla la interfaz.

Cuando un hilo de red necesita modificar un elemento visual se utiliza:

```java
SwingUtilities.invokeLater(() -> {
    // Actualización de la interfaz
});
```

Esto permite actualizar elementos como el área de conversación y el estado de la conexión sin bloquear la ventana.

## Tecnologías utilizadas

- Java.
- Java Swing.
- Sockets TCP.
- `ServerSocket`.
- `Socket`.
- `Thread`.
- `BufferedReader`.
- `PrintWriter`.
- `CopyOnWriteArrayList`.
- Git.

## Requisitos

Para ejecutar el proyecto se necesita:

- Java JDK 17 o superior.
- Las computadoras deben encontrarse conectadas a la misma red local.

La instalación de Java se puede comprobar con:

```powershell
java -version
```

También se puede revisar el compilador:

```powershell
javac -version
```

## Compilación

Desde la carpeta principal del proyecto se crea la carpeta donde se guardarán los archivos compilados:

```powershell
mkdir out
```

Después se compilan las tres clases:

```powershell
javac -encoding UTF-8 -d out src\Servidor.java src\Cliente.java src\RedUtil.java
```

Si la compilación se realiza correctamente se generarán los archivos `.class` dentro de `out`.

La carpeta `out` no se almacena en Git porque contiene archivos generados durante la compilación.

## Ejecución del servidor

Primero se debe ejecutar el servidor:

```powershell
java -cp out Servidor
```

Se abrirá la interfaz gráfica.

El puerto predeterminado es:

```text
5000
```

Después se presiona:

```text
Iniciar servidor
```

El servidor mostrará sus direcciones IP y quedará esperando clientes.

Ejemplo:

```text
IP de esta computadora: 172.17.57.87
```

## Ejecución de los clientes

En cada computadora cliente se ejecuta:

```powershell
java -cp out Cliente
```

Cada usuario debe escribir un nombre diferente.

Ejemplo:

```text
Nombre: Fernando
IP servidor: 172.17.57.87
Puerto: 5000
```

Después se presiona:

```text
Conectar
```

El mismo procedimiento se puede realizar en las demás computadoras.

Ejemplo:

```text
Computadora 1
Nombre: Fernando

Computadora 2
Nombre: Ana

Computadora 3
Nombre: José
```

Todas deben utilizar la misma IP del servidor y el mismo puerto.

## Ejemplo de conversación

Con varios clientes conectados se puede mantener una conversación como la siguiente:

```text
Fernando: Hola a todos
Ana: Hola Fernando
José: Ya estoy conectado
Fernando: Perfecto
Servidor: Mensaje recibido por todos
Ana: Sí, ya apareció
```

Los clientes pueden seguir enviando mensajes mientras permanezcan conectados.

## Prueba utilizando una sola computadora

También se puede comprobar el funcionamiento abriendo varias ventanas en el mismo equipo.

Primero se ejecuta el servidor:

```powershell
java -cp out Servidor
```

Después se pueden abrir varias terminales y ejecutar en cada una:

```powershell
java -cp out Cliente
```

En este caso se utiliza como dirección IP:

```text
127.0.0.1
```

Por ejemplo se pueden abrir tres clientes con nombres diferentes:

```text
Fernando
Ana
José
```

Los tres podrán comunicarse mediante el mismo servidor.

## Posibles problemas de conexión

Si un cliente no logra conectarse, se recomienda revisar:

- Que el servidor se encuentre iniciado.
- Que todas las computadoras estén en la misma red.
- Que la dirección IP corresponda al servidor.
- Que todos utilicen el mismo puerto.
- Que el Firewall de Windows permita la comunicación de Java.

Para comprobar la comunicación con el servidor se puede utilizar:

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

Si el puerto se encuentra disponible debe aparecer:

```text
TcpTestSucceeded : True
```

## Conclusión

Con este programa se implementó una comunicación grupal entre varias computadoras utilizando sockets TCP en Java.

El servidor puede mantener varias conexiones activas al mismo tiempo y distribuir los mensajes recibidos entre todos los clientes conectados.

El uso de hilos permite atender cada conexión de manera independiente y mantener la interfaz gráfica funcionando mientras se reciben mensajes de la red.

La práctica permite observar de forma directa cómo un servidor puede coordinar la comunicación entre varios clientes dentro de una red local.
