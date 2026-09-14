# Comunicación cliente-servidor

## Descripción

Este proyecto fue realizado para la materia de **Tópicos para el Despliegue de
Aplicaciones**.

La actividad consiste en comunicar varios clientes por medio de un servidor.
Cada persona se conecta con un nombre y puede enviar mensajes a todos los
usuarios o elegir a una persona en específico.

El servidor está hecho en Java. Para comprobar que la comunicación funciona
con diferentes tecnologías se hicieron tres clientes: uno en Java, otro en
Python y otro en C#.

## Funcionamiento

Primero se abre el servidor. Este se inicia automáticamente en el puerto
`5000` y queda esperando las conexiones.

Después se pueden abrir uno o varios clientes. Cada cliente debe escribir:

- El nombre que quiere utilizar.
- La dirección IP de la computadora donde está abierto el servidor.

Al conectarse, en la parte izquierda aparecen las personas disponibles. La
lista se actualiza sola cuando alguien entra o sale.

La opción **Todos** sirve para enviar un mensaje a todas las personas
conectadas. Para mandar un mensaje a una sola persona, se selecciona su nombre
en la lista.

## Programas incluidos

- `Servidor.java`: recibe las conexiones y distribuye los mensajes.
- `Cliente.java`: cliente realizado con Java y Swing.
- `Cliente.py`: cliente realizado con Python y Tkinter.
- `Cliente.cs`: cliente realizado con C# y Windows Forms.

Los tres clientes funcionan con el mismo servidor y tienen las mismas opciones
principales.

## Requisitos

- Java JDK 17 o posterior.
- Python 3 con Tkinter.
- .NET 10.
- Visual Studio Code o una terminal.

Python y C# no necesitan paquetes adicionales para este proyecto.

## Cómo ejecutar el proyecto

### Servidor

En Visual Studio Code se abre `Servidor.java` y se presiona **Run Java**.
Cuando aparezca la ventana, el servidor ya estará funcionando.

### Cliente Java

Se abre `Cliente.java` y se presiona **Run Java**. Se pueden abrir varias
ventanas para conectar a más personas.

### Cliente Python

Desde la carpeta principal del proyecto:

```powershell
python src\Cliente.py
```

### Cliente C#

Desde la carpeta principal del proyecto:

```powershell
dotnet src\Cliente.cs
```

## Prueba en la misma computadora

Si el servidor y los clientes están abiertos en la misma computadora, se usa
esta dirección:

```text
127.0.0.1
```

Se escribe un nombre diferente en cada ventana y después se presiona
**Conectar**.

## Prueba con varias computadoras

Las computadoras deben estar conectadas a la misma red. El servidor muestra sus
direcciones IP en la ventana; una de ellas se escribe en todos los clientes.

Si Windows pregunta si Java puede comunicarse por la red, se debe permitir el
acceso para que las demás computadoras puedan conectarse.

## Uso

Después de conectarse:

1. Se selecciona **Todos** o el nombre de una persona.
2. Se escribe el mensaje.
3. Se presiona **Enviar** o la tecla **Enter**.

Si dos personas escriben el mismo nombre, el servidor agrega un número para
diferenciarlas. Por ejemplo, el segundo usuario llamado `Luis` puede aparecer
como `Luis 2`.

El servidor también muestra las personas conectadas y una sección de actividad
para observar lo que ocurre durante la ejecución.

