# Cliente - Servidor con Java

Proyecto realizado para la materia de Tópicos.

El propósito del programa es establecer una comunicación entre dos computadoras conectadas a la misma red utilizando Java y sockets TCP.

Una computadora funciona como servidor y la otra como cliente. El cliente se conecta utilizando la dirección IP del servidor y el puerto definido en el programa.

Después de establecer la conexión, el cliente puede escribir un mensaje y enviarlo al servidor. El servidor recibe el mensaje, lo muestra en pantalla y permite escribir una respuesta que será enviada de regreso al cliente.

## Objetivo

Desarrollar una aplicación sencilla que permita comunicar dos computadoras dentro de una red local mediante sockets.

Con esta práctica se busca comprender cómo se establece una conexión entre un cliente y un servidor, así como el proceso básico para enviar y recibir información utilizando Java.

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

La carpeta `src` contiene el código fuente del programa.

### Servidor.java

La clase `Servidor` se encarga de abrir el puerto de comunicación y esperar a que un cliente se conecte.

Cuando se establece la conexión, el servidor obtiene la dirección IP del cliente, recibe el mensaje enviado y lo muestra en la consola.

Después permite escribir una respuesta desde el teclado y la envía al cliente.

### Cliente.java

La clase `Cliente` se encarga de solicitar la dirección IP de la computadora donde se está ejecutando el servidor.

Con esa dirección y el puerto configurado se crea la conexión.

Después de conectarse, el usuario puede escribir un mensaje, enviarlo al servidor y esperar la respuesta.

## Comunicación entre las computadoras

La comunicación sigue un modelo cliente-servidor.

```text
Computadora cliente                 Computadora servidor
        |                                    |
        |---------- Conexion --------------->|
        |                                    |
        |---------- Mensaje ---------------->|
        |                                    |
        |<--------- Respuesta ---------------|
        |                                    |
```

El servidor utiliza el puerto:

```text
5000
```

Por esta razón, el cliente debe conectarse a la dirección IP del servidor utilizando ese mismo puerto.

Un ejemplo sería:

```text
IP del servidor: 172.17.57.87
Puerto: 5000
```

## Tecnologías utilizadas

- Java
- Sockets TCP
- `ServerSocket`
- `Socket`
- `BufferedReader`
- `PrintWriter`
- `Scanner`
- Git

## Requisitos

Para ejecutar el programa se necesita tener instalado Java JDK 17 o una versión superior.

Se puede comprobar la instalación utilizando:

```powershell
java -version
```

También se puede revisar la versión del compilador con:

```powershell
javac -version
```

Las dos computadoras deben encontrarse conectadas a la misma red.

Por ejemplo, ambas pueden estar conectadas al mismo Wi-Fi o a la misma red local por cable.

## Compilación

Desde la carpeta principal del proyecto se debe crear una carpeta para guardar los archivos compilados:

```powershell
mkdir out
```

Después se compilan las dos clases:

```powershell
javac -encoding UTF-8 -d out src\Servidor.java src\Cliente.java
```

Si la compilación termina correctamente, dentro de la carpeta `out` se generarán los archivos `.class`.

La estructura quedaría de la siguiente manera:

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

La carpeta `out` no se guarda en Git, ya que contiene archivos generados al compilar el código.

## Ejecución del servidor

Primero se debe ejecutar el servidor.

Desde la carpeta principal del proyecto:

```powershell
java -cp out Servidor
```

La consola mostrará algo parecido a:

```text
=== SERVIDOR DE MENSAJERIA ===
Puerto: 5000
Esperando conexion de un cliente...
```

En este momento el servidor queda esperando a que otra computadora se conecte.

## Consultar la dirección IP

En la computadora donde se encuentra el servidor se puede utilizar:

```powershell
ipconfig
```

Se debe buscar la dirección IPv4 del adaptador de red que se está utilizando.

Por ejemplo:

```text
Direccion IPv4. . . . . . . . . . . : 172.17.57.87
```

Esa es la dirección que se debe escribir posteriormente en el cliente.

Si aparecen varias direcciones IP, se debe utilizar la correspondiente a la red donde se encuentran conectadas las dos computadoras.

## Ejecución del cliente

En la segunda computadora se ejecuta:

```powershell
java -cp out Cliente
```

El programa solicitará la dirección IP del servidor:

```text
=== CLIENTE DE MENSAJERIA ===
Escribe la IP del servidor:
```

Por ejemplo:

```text
172.17.57.87
```

Después el programa intentará establecer la conexión:

```text
Conectando con 172.17.57.87:5000...
Conexion realizada correctamente.
```

Una vez conectado, el cliente puede escribir el mensaje que desea enviar.

## Ejemplo de funcionamiento

En la computadora cliente:

```text
=== CLIENTE DE MENSAJERIA ===
Escribe la IP del servidor: 172.17.57.87
Conectando con 172.17.57.87:5000...
Conexion realizada correctamente.

Escribe tu mensaje: Hola servidor
```

En la computadora servidor aparecerá:

```text
=== SERVIDOR DE MENSAJERIA ===
Puerto: 5000
Esperando conexion de un cliente...

Cliente conectado desde: 172.17.57.90

Cliente: Hola servidor
Escribe una respuesta:
```

El usuario del servidor puede escribir:

```text
Hola cliente, mensaje recibido
```

El cliente recibirá:

```text
Servidor: Hola cliente, mensaje recibido
```

## Prueba utilizando una sola computadora

También es posible realizar una prueba utilizando únicamente una computadora.

Primero se abre una terminal y se ejecuta:

```powershell
java -cp out Servidor
```

Después se abre una segunda terminal y se ejecuta:

```powershell
java -cp out Cliente
```

Cuando el cliente solicite la dirección IP del servidor se puede utilizar:

```text
127.0.0.1
```

Esta dirección representa la misma computadora y permite probar el funcionamiento antes de utilizar dos equipos diferentes.

## Funcionamiento del código

Para establecer la comunicación se utiliza `ServerSocket` del lado del servidor.

```java
ServerSocket servidor = new ServerSocket(PUERTO);
```

Esto permite que la computadora escuche conexiones en el puerto 5000.

El servidor espera la conexión utilizando:

```java
Socket cliente = servidor.accept();
```

Cuando un cliente intenta conectarse, se crea un socket que representa la conexión entre las dos computadoras.

Del lado del cliente se utiliza:

```java
Socket socket = new Socket(ipServidor, PUERTO);
```

Aquí se utilizan la dirección IP del servidor y el puerto para realizar la conexión.

Para recibir texto se utiliza un `BufferedReader`:

```java
BufferedReader entrada = new BufferedReader(
    new InputStreamReader(socket.getInputStream())
);
```

Para enviar información se utiliza `PrintWriter`:

```java
PrintWriter salida = new PrintWriter(
    socket.getOutputStream(),
    true
);
```

De esta forma, cada computadora tiene un flujo de entrada para recibir información y un flujo de salida para enviar información.

## Posibles problemas de conexión

Si el cliente no puede conectarse con el servidor, se recomienda revisar que ambas computadoras se encuentren en la misma red.

También se debe verificar que la dirección IP escrita en el cliente corresponda realmente a la computadora donde está ejecutándose el servidor.

Otro punto importante es revisar el Firewall de Windows, ya que puede bloquear las conexiones realizadas por Java.

Si Windows solicita permiso para permitir la comunicación de Java, se puede habilitar el acceso para redes privadas.

También se puede comprobar si existe comunicación entre las computadoras utilizando:

```powershell
ping DIRECCION_IP
```

Por ejemplo:

```powershell
ping 172.17.57.87
```

Para revisar específicamente el puerto 5000 se puede utilizar PowerShell:

```powershell
Test-NetConnection 172.17.57.87 -Port 5000
```

Si la conexión está disponible debe aparecer:

```text
TcpTestSucceeded : True
```

## Conclusión

Con este programa se logró establecer comunicación entre dos computadoras dentro de una red local utilizando sockets TCP en Java.

La práctica permitió observar de forma directa cómo una computadora puede funcionar como servidor y permanecer esperando una conexión, mientras que otra puede funcionar como cliente y conectarse utilizando una dirección IP y un puerto.

También se utilizaron flujos de entrada y salida para intercambiar mensajes de texto entre las dos computadoras.