import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Clase que funciona como servidor para recibir un mensaje
 * enviado desde otra computadora conectada a la misma red.
 *
 * El servidor utiliza un socket TCP y permanece esperando
 * hasta que un cliente se conecte al puerto establecido.
 *
 * Cuando se realiza la conexión, recibe un mensaje,
 * lo muestra en pantalla y permite escribir una respuesta.
 */
public class Servidor {

    // Puerto que se utilizará para establecer la comunicación.
    private static final int PUERTO = 5000;

    /**
     * Método principal desde donde inicia la ejecución del servidor.
     *
     * @param args argumentos recibidos desde la línea de comandos.
     */
    public static void main(String[] args) {

        // Se muestran datos básicos para saber que el servidor inició.
        System.out.println("=== SERVIDOR DE MENSAJERIA ===");
        System.out.println("Puerto: " + PUERTO);

        /*
         * ServerSocket abre el puerto indicado y permite
         * recibir conexiones de otros equipos.
         *
         * Se utiliza try-with-resources para que el socket
         * se cierre automáticamente al terminar el programa.
         */
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {

            System.out.println("Esperando conexion de un cliente...");

            /*
             * El método accept() espera hasta que algún cliente
             * intente conectarse al servidor.
             *
             * Cuando se recibe la conexión, se crea un objeto Socket
             * que representa la comunicación con ese cliente.
             */
            try (
                Socket cliente = servidor.accept();

                /*
                 * BufferedReader permite leer los datos que llegan
                 * desde el cliente.
                 *
                 * Se utiliza UTF-8 para manejar correctamente
                 * los caracteres enviados como texto.
                 */
                BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                        cliente.getInputStream(),
                        StandardCharsets.UTF_8
                    )
                );

                /*
                 * PrintWriter permite enviar información al cliente.
                 *
                 * El valor true activa el autoFlush para que
                 * cada mensaje sea enviado inmediatamente.
                 */
                PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(
                        cliente.getOutputStream(),
                        StandardCharsets.UTF_8
                    ),
                    true
                );

                // Scanner permite escribir la respuesta desde el teclado.
                Scanner teclado = new Scanner(System.in)
            ) {

                /*
                 * Se obtiene la dirección IP del cliente que realizó
                 * la conexión y se muestra en pantalla.
                 */
                String ipCliente =
                    cliente.getInetAddress().getHostAddress();

                System.out.println(
                    "Cliente conectado desde: " + ipCliente
                );

                /*
                 * readLine() espera hasta recibir una línea de texto
                 * enviada por el cliente.
                 */
                String mensajeCliente = entrada.readLine();

                /*
                 * Si readLine() devuelve null significa que el cliente
                 * cerró la conexión antes de enviar información.
                 */
                if (mensajeCliente == null) {

                    System.out.println(
                        "El cliente cerro la conexion."
                    );

                    return;
                }

                // Se muestra en pantalla el mensaje recibido.
                System.out.println();
                System.out.println(
                    "Cliente: " + mensajeCliente
                );

                /*
                 * El servidor solicita al usuario escribir
                 * una respuesta para enviarla al cliente.
                 */
                System.out.print(
                    "Escribe una respuesta: "
                );

                String respuesta = teclado.nextLine();

                /*
                 * println() envía la respuesta utilizando
                 * el flujo de salida del socket.
                 */
                salida.println(respuesta);

                System.out.println(
                    "Respuesta enviada."
                );
            }

        } catch (IOException e) {

            /*
             * Si ocurre un problema al abrir el puerto,
             * aceptar la conexión o intercambiar información,
             * se muestra el mensaje del error.
             */
            System.out.println(
                "Ocurrio un error en el servidor."
            );

            System.out.println(
                "Detalle: " + e.getMessage()
            );
        }
    }
}