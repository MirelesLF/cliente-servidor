import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Clase que funciona como cliente para conectarse
 * con una computadora que está ejecutando el servidor.
 *
 * El usuario indica la dirección IP del servidor,
 * escribe un mensaje y espera una respuesta.
 *
 * La comunicación se realiza utilizando sockets TCP.
 */
public class Cliente {

    // Puerto donde se encuentra escuchando el servidor.
    private static final int PUERTO = 5000;

    /**
     * Método principal desde donde inicia la ejecución del cliente.
     *
     * @param args argumentos recibidos desde la línea de comandos.
     */
    public static void main(String[] args) {

        // Título mostrado al iniciar el programa.
        System.out.println(
            "=== CLIENTE DE MENSAJERIA ==="
        );

        /*
         * Scanner se utiliza para leer la dirección IP
         * y el mensaje escrito por el usuario.
         */
        try (Scanner teclado = new Scanner(System.in)) {

            // Se solicita la dirección IP de la computadora servidor.
            System.out.print(
                "Escribe la IP del servidor: "
            );

            /*
             * trim() elimina espacios adicionales al inicio
             * o al final de la dirección escrita.
             */
            String ipServidor =
                teclado.nextLine().trim();

            /*
             * Antes de intentar la conexión se verifica
             * que el usuario haya escrito una dirección.
             */
            if (ipServidor.isEmpty()) {

                System.out.println(
                    "Debes escribir una direccion IP."
                );

                return;
            }

            // Se muestran los datos que se utilizarán para conectarse.
            System.out.println(
                "Conectando con "
                + ipServidor
                + ":"
                + PUERTO
                + "..."
            );

            /*
             * Socket intenta establecer la conexión TCP
             * utilizando la IP del servidor y el puerto 5000.
             */
            try (
                Socket socket =
                    new Socket(ipServidor, PUERTO);

                /*
                 * BufferedReader permite recibir la respuesta
                 * enviada desde el servidor.
                 */
                BufferedReader entrada =
                    new BufferedReader(
                        new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    );

                /*
                 * PrintWriter permite enviar texto
                 * hacia el servidor.
                 *
                 * El valor true hace que el mensaje
                 * se envíe inmediatamente.
                 */
                PrintWriter salida =
                    new PrintWriter(
                        new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8
                        ),
                        true
                    )
            ) {

                // Si se llegó hasta aquí, la conexión fue realizada.
                System.out.println(
                    "Conexion realizada correctamente."
                );

                System.out.println();

                // Se solicita el mensaje que será enviado al servidor.
                System.out.print(
                    "Escribe tu mensaje: "
                );

                String mensaje =
                    teclado.nextLine();

                /*
                 * El mensaje se envía al servidor
                 * mediante el flujo de salida del socket.
                 */
                salida.println(mensaje);

                /*
                 * Después de enviar el mensaje,
                 * el cliente espera la respuesta del servidor.
                 */
                String respuesta =
                    entrada.readLine();

                /*
                 * Si se recibió información,
                 * se muestra la respuesta en pantalla.
                 */
                if (respuesta != null) {

                    System.out.println(
                        "Servidor: " + respuesta
                    );

                } else {

                    /*
                     * Si se recibe null, el servidor cerró
                     * la conexión sin enviar una respuesta.
                     */
                    System.out.println(
                        "El servidor cerro la conexion."
                    );
                }

            }

        } catch (IOException e) {

            /*
             * Aquí se capturan errores relacionados
             * con la conexión o el intercambio de datos.
             */
            System.out.println(
                "No fue posible conectarse con el servidor."
            );

            System.out.println(
                "Detalle: " + e.getMessage()
            );
        }
    }
}