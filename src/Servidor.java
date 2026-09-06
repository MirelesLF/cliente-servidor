import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Ventana que funciona como servidor de mensajería dentro de una red local.
 *
 * El servidor abre un puerto, espera la conexión de un cliente y mantiene
 * la comunicación activa para poder enviar y recibir varios mensajes.
 */
public class Servidor extends JFrame {

    // Componentes de la parte superior de la ventana.
    private final JTextField campoPuerto =
            new JTextField("5000", 6);

    private final JButton botonServidor =
            new JButton("Iniciar servidor");

    private final JLabel etiquetaEstado =
            new JLabel("Estado: detenido");

    private final JLabel etiquetaIp =
            new JLabel(
                    "IP de esta computadora: "
                            + obtenerDireccionesComoTexto()
            );

    // Área donde se muestran los mensajes enviados y recibidos.
    private final JTextArea areaConversacion =
            new JTextArea();

    // Componentes utilizados para escribir y enviar mensajes.
    private final JTextField campoMensaje =
            new JTextField();

    private final JButton botonEnviar =
            new JButton("Enviar");

    /*
     * Este objeto se utiliza para controlar el acceso
     * a los datos relacionados con la conexión.
     */
    private final Object bloqueoConexion =
            new Object();

    // Indica si el servidor se encuentra funcionando.
    private volatile boolean servidorActivo;

    // Indica si actualmente existe un cliente conectado.
    private volatile boolean clienteConectado;

    // Socket encargado de escuchar las conexiones.
    private ServerSocket servidorSocket;

    // Socket correspondiente al cliente conectado.
    private Socket clienteSocket;

    // Flujo utilizado para enviar mensajes al cliente.
    private PrintWriter salidaCliente;

    /**
     * Constructor de la ventana del servidor.
     *
     * Aquí se prepara la interfaz gráfica y se configuran
     * los eventos de los botones.
     */
    public Servidor() {
        super("Servidor - Mensajería LAN");

        construirInterfaz();
        configurarEventos();
    }

    /**
     * Construye y organiza todos los componentes
     * que forman la interfaz del servidor.
     */
    private void construirInterfaz() {

        // Evita que la ventana se cierre sin liberar primero los sockets.
        setDefaultCloseOperation(
                JFrame.DO_NOTHING_ON_CLOSE
        );

        setMinimumSize(
                new Dimension(760, 520)
        );

        setSize(
                900,
                600
        );

        // La ventana aparece centrada en pantalla.
        setLocationRelativeTo(null);

        /*
         * Panel principal donde se colocarán
         * las diferentes secciones de la interfaz.
         */
        JPanel contenido =
                new JPanel(
                        new BorderLayout(10, 10)
                );

        contenido.setBorder(
                new EmptyBorder(
                        12,
                        12,
                        12,
                        12
                )
        );

        setContentPane(contenido);

        /*
         * Panel superior donde se encuentran
         * el puerto, botón y estado del servidor.
         */
        JPanel panelSuperior =
                new JPanel(
                        new BorderLayout(8, 8)
                );

        JPanel panelConfiguracion =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                8,
                                0
                        )
                );

        panelConfiguracion.add(
                new JLabel("Puerto:")
        );

        panelConfiguracion.add(
                campoPuerto
        );

        panelConfiguracion.add(
                botonServidor
        );

        panelConfiguracion.add(
                etiquetaEstado
        );

        /*
         * La dirección IP se muestra para saber
         * qué dirección debe escribir el cliente.
         */
        etiquetaIp.setFont(
                etiquetaIp
                        .getFont()
                        .deriveFont(Font.BOLD)
        );

        etiquetaIp.setToolTipText(
                "El cliente debe utilizar una IP válida de esta computadora."
        );

        panelSuperior.add(
                panelConfiguracion,
                BorderLayout.NORTH
        );

        panelSuperior.add(
                etiquetaIp,
                BorderLayout.SOUTH
        );

        contenido.add(
                panelSuperior,
                BorderLayout.NORTH
        );

        // El área de conversación solamente muestra información.
        areaConversacion.setEditable(false);

        areaConversacion.setLineWrap(true);

        areaConversacion.setWrapStyleWord(true);

        areaConversacion.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        14
                )
        );

        areaConversacion.setBorder(
                new EmptyBorder(
                        6,
                        6,
                        6,
                        6
                )
        );

        /*
         * JScrollPane permite desplazarse cuando
         * la conversación contiene muchos mensajes.
         */
        JScrollPane desplazamiento =
                new JScrollPane(
                        areaConversacion
                );

        desplazamiento.setBorder(
                BorderFactory.createTitledBorder(
                        "Conversación"
                )
        );

        contenido.add(
                desplazamiento,
                BorderLayout.CENTER
        );

        /*
         * Panel inferior donde el servidor
         * escribe los mensajes que desea enviar.
         */
        JPanel panelMensaje =
                new JPanel(
                        new BorderLayout(8, 0)
                );

        panelMensaje.setBorder(
                BorderFactory.createTitledBorder(
                        "Mensaje"
                )
        );

        panelMensaje.add(
                campoMensaje,
                BorderLayout.CENTER
        );

        panelMensaje.add(
                botonEnviar,
                BorderLayout.EAST
        );

        contenido.add(
                panelMensaje,
                BorderLayout.SOUTH
        );

        /*
         * Los controles para enviar mensajes
         * permanecen bloqueados mientras no exista un cliente.
         */
        habilitarEnvio(false);
    }

    /**
     * Configura las acciones que realizan los botones
     * y los eventos principales de la ventana.
     */
    private void configurarEventos() {

        /*
         * El mismo botón permite iniciar o detener
         * el servidor dependiendo de su estado.
         */
        botonServidor.addActionListener(evento -> {

            if (servidorActivo) {

                detenerServidor();

            } else {

                iniciarServidor();
            }
        });

        /*
         * El mensaje puede enviarse presionando
         * el botón Enviar.
         */
        botonEnviar.addActionListener(
                evento -> enviarMensaje()
        );

        /*
         * También se puede enviar el mensaje
         * directamente presionando Enter.
         */
        campoMensaje.addActionListener(
                evento -> enviarMensaje()
        );

        /*
         * Al cerrar la ventana se cierran primero
         * los sockets que puedan encontrarse abiertos.
         */
        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosing(
                            WindowEvent evento
                    ) {

                        detenerServidorSilenciosamente();

                        dispose();
                    }
                }
        );
    }

    /**
     * Valida el puerto escrito por el usuario
     * y comienza la ejecución del servidor.
     */
    private void iniciarServidor() {

        int puerto;

        try {

            puerto =
                    leerPuerto(
                            campoPuerto.getText()
                    );

        } catch (IllegalArgumentException error) {

            mostrarError(
                    error.getMessage()
            );

            campoPuerto.requestFocusInWindow();

            return;
        }

        servidorActivo = true;

        campoPuerto.setEnabled(false);

        botonServidor.setText(
                "Detener servidor"
        );

        botonServidor.setEnabled(false);

        actualizarEstado(
                "Estado: iniciando..."
        );

        /*
         * Las operaciones de red se realizan en otro hilo.
         *
         * De esta forma la interfaz no se congela mientras
         * el servidor espera una conexión o un mensaje.
         */
        Thread hiloServidor =
                new Thread(
                        () -> ejecutarServidor(puerto),
                        "hilo-servidor"
                );

        /*
         * Un hilo daemon termina automáticamente
         * cuando la aplicación principal se cierra.
         */
        hiloServidor.setDaemon(true);

        // start() comienza la ejecución del hilo.
        hiloServidor.start();
    }

    /**
     * Abre el puerto del servidor y espera
     * la conexión de un cliente.
     *
     * Después de conectarse, el hilo permanece leyendo
     * mensajes hasta que el cliente se desconecte.
     *
     * @param puerto puerto TCP utilizado para la comunicación.
     */
    private void ejecutarServidor(
            int puerto
    ) {

        try {

            /*
             * Se crea el socket que recibirá
             * las conexiones entrantes.
             */
            ServerSocket nuevoServidor =
                    new ServerSocket();

            /*
             * Permite volver a utilizar el mismo puerto
             * después de cerrar el programa.
             */
            nuevoServidor.setReuseAddress(true);

            /*
             * bind() relaciona el ServerSocket
             * con el puerto seleccionado.
             */
            nuevoServidor.bind(
                    new InetSocketAddress(puerto)
            );

            /*
             * Se guarda el socket para poder cerrarlo
             * desde el botón Detener servidor.
             */
            synchronized (bloqueoConexion) {

                servidorSocket =
                        nuevoServidor;
            }

            /*
             * Las modificaciones de Swing se realizan
             * dentro de su hilo de eventos.
             */
            ejecutarEnInterfaz(() -> {

                botonServidor.setEnabled(true);

                actualizarEstado(
                        "Estado: esperando cliente"
                );

                registrarSistema(
                        "Servidor iniciado en el puerto "
                                + puerto
                                + "."
                );

                registrarSistema(
                        "En la otra computadora escribe una de estas IP: "
                                + obtenerDireccionesComoTexto()
                );
            });

            /*
             * El servidor se mantiene activo.
             *
             * Solamente se atiende un cliente al mismo tiempo.
             * Cuando ese cliente se desconecta puede entrar otro.
             */
            while (servidorActivo) {

                /*
                 * accept() espera hasta que un cliente
                 * intente conectarse.
                 */
                Socket nuevoCliente =
                        nuevoServidor.accept();

                configurarSocket(
                        nuevoCliente
                );

                /*
                 * La comunicación con ese cliente
                 * se mantiene activa hasta que se desconecte.
                 */
                atenderCliente(
                        nuevoCliente
                );
            }

        } catch (SocketException error) {

            /*
             * Cuando se detiene el servidor se cierra
             * el ServerSocket y accept() deja de esperar.
             */
            if (servidorActivo) {

                notificarFalloServidor(
                        "Se perdió la conexión del servidor: "
                                + error.getMessage()
                );
            }

        } catch (IOException error) {

            if (servidorActivo) {

                notificarFalloServidor(
                        "No fue posible iniciar o mantener el servidor.\n\n"
                                + "Detalle: "
                                + error.getMessage()
                );
            }

        } finally {

            cerrarRecursosServidor();

            servidorActivo = false;

            ejecutarEnInterfaz(() -> {

                campoPuerto.setEnabled(true);

                botonServidor.setText(
                        "Iniciar servidor"
                );

                botonServidor.setEnabled(true);

                habilitarEnvio(false);

                actualizarEstado(
                        "Estado: detenido"
                );
            });
        }
    }

    /**
     * Mantiene la comunicación con el cliente conectado.
     *
     * Se preparan los flujos de entrada y salida
     * y se reciben todos los mensajes enviados por el cliente.
     *
     * @param socket socket correspondiente al cliente.
     */
    private void atenderCliente(
            Socket socket
    ) {

        /*
         * Se obtiene la dirección IP de la computadora
         * que acaba de conectarse.
         */
        String direccionCliente =
                socket
                        .getInetAddress()
                        .getHostAddress();

        try {

            /*
             * BufferedReader permite leer los mensajes
             * que llegan desde el cliente.
             */
            BufferedReader entrada =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

            /*
             * PrintWriter permite enviar mensajes
             * desde el servidor hacia el cliente.
             */
            PrintWriter salida =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(),
                                    StandardCharsets.UTF_8
                            ),
                            true
                    );

            /*
             * Se guardan los datos de la conexión actual.
             */
            synchronized (bloqueoConexion) {

                clienteSocket =
                        socket;

                salidaCliente =
                        salida;

                clienteConectado =
                        true;
            }

            ejecutarEnInterfaz(() -> {

                registrarSistema(
                        "Cliente conectado desde: "
                                + direccionCliente
                );

                actualizarEstado(
                        "Estado: cliente conectado"
                );

                habilitarEnvio(true);

                campoMensaje.requestFocusInWindow();
            });

            String mensaje;

            /*
             * readLine() permanece esperando mensajes.
             *
             * El ciclo permite recibir más de uno
             * sin cerrar la conexión.
             */
            while (
                    servidorActivo
                            && clienteConectado
                            && (mensaje = entrada.readLine()) != null
            ) {

                /*
                 * La variable se copia porque será utilizada
                 * dentro de la expresión lambda.
                 */
                final String mensajeRecibido =
                        mensaje;

                /*
                 * El mensaje recibido se agrega
                 * a la conversación de la interfaz.
                 */
                ejecutarEnInterfaz(
                        () -> registrarMensaje(
                                "Cliente",
                                mensajeRecibido
                        )
                );
            }

        } catch (SocketException error) {

            if (
                    servidorActivo
                            && clienteConectado
            ) {

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "La conexión con el cliente se interrumpió."
                        )
                );
            }

        } catch (IOException error) {

            if (
                    servidorActivo
                            && clienteConectado
            ) {

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "Error de comunicación: "
                                        + error.getMessage()
                        )
                );
            }

        } finally {

            /*
             * Cuando termina la conexión se limpian
             * todos los datos relacionados con ese cliente.
             */
            cerrarCliente();

            /*
             * Si el servidor continúa activo,
             * vuelve a quedar esperando otra conexión.
             */
            if (servidorActivo) {

                ejecutarEnInterfaz(() -> {

                    registrarSistema(
                            "Cliente desconectado. Esperando otra conexión..."
                    );

                    actualizarEstado(
                            "Estado: esperando cliente"
                    );

                    habilitarEnvio(false);
                });
            }
        }
    }

    /**
     * Envía el mensaje escrito al cliente conectado.
     */
    private void enviarMensaje() {

        String mensaje =
                limpiarTexto(
                        campoMensaje.getText()
                );

        // No se envían mensajes vacíos.
        if (mensaje.isBlank()) {
            return;
        }

        PrintWriter salida;

        /*
         * Se obtiene de forma segura
         * el flujo de salida actual.
         */
        synchronized (bloqueoConexion) {

            salida =
                    salidaCliente;
        }

        /*
         * Antes de enviar se comprueba
         * que exista una conexión.
         */
        if (
                !clienteConectado
                        || salida == null
        ) {

            mostrarError(
                    "No hay un cliente conectado."
            );

            return;
        }

        /*
         * println() envía el texto seguido
         * de un salto de línea.
         *
         * Ese salto permite que readLine()
         * pueda identificar cada mensaje.
         */
        salida.println(
                mensaje
        );

        /*
         * Se comprueba si PrintWriter detectó
         * algún problema al escribir.
         */
        if (salida.checkError()) {

            mostrarError(
                    "No fue posible enviar el mensaje. "
                            + "La conexión puede haberse cerrado."
            );

            return;
        }

        /*
         * El mensaje enviado también aparece
         * en la conversación del servidor.
         */
        registrarMensaje(
                "Servidor",
                mensaje
        );

        campoMensaje.setText("");

        campoMensaje.requestFocusInWindow();
    }

    /**
     * Detiene el servidor cuando el usuario
     * presiona el botón correspondiente.
     */
    private void detenerServidor() {

        servidorActivo = false;

        registrarSistema(
                "Deteniendo servidor..."
        );

        cerrarRecursosServidor();

        botonServidor.setEnabled(false);
    }

    /**
     * Detiene el servidor sin mostrar mensajes.
     *
     * Se utiliza cuando la ventana
     * se está cerrando completamente.
     */
    private void detenerServidorSilenciosamente() {

        servidorActivo = false;

        cerrarRecursosServidor();
    }

    /**
     * Cierra la conexión con el cliente actual.
     */
    private void cerrarCliente() {

        synchronized (bloqueoConexion) {

            clienteConectado = false;

            cerrarSilenciosamente(
                    clienteSocket
            );

            clienteSocket = null;

            salidaCliente = null;
        }
    }

    /**
     * Cierra tanto la conexión con el cliente
     * como el socket principal del servidor.
     */
    private void cerrarRecursosServidor() {

        cerrarCliente();

        synchronized (bloqueoConexion) {

            if (servidorSocket != null) {

                try {

                    servidorSocket.close();

                } catch (IOException ignored) {

                    /*
                     * El socket puede encontrarse
                     * cerrado previamente.
                     */
                }

                servidorSocket = null;
            }
        }
    }

    /**
     * Configura algunas opciones del socket.
     *
     * @param socket socket que será configurado.
     *
     * @throws SocketException si ocurre un problema
     * al modificar alguna propiedad.
     */
    private static void configurarSocket(
            Socket socket
    ) throws SocketException {

        /*
         * KeepAlive permite detectar conexiones
         * que dejan de estar disponibles.
         */
        socket.setKeepAlive(true);

        /*
         * TcpNoDelay ayuda a enviar mensajes pequeños
         * sin esperar a agruparlos.
         */
        socket.setTcpNoDelay(true);
    }

    /**
     * Lee y valida el puerto escrito en la interfaz.
     *
     * @param texto contenido del campo de puerto.
     * @return puerto convertido a número entero.
     */
    private static int leerPuerto(
            String texto
    ) {

        try {

            int puerto =
                    Integer.parseInt(
                            texto.trim()
                    );

            /*
             * Se utiliza este rango para evitar
             * puertos reservados del sistema.
             */
            if (
                    puerto < 1024
                            || puerto > 65535
            ) {

                throw new IllegalArgumentException(
                        "El puerto debe estar entre 1024 y 65535."
                );
            }

            return puerto;

        } catch (NumberFormatException error) {

            throw new IllegalArgumentException(
                    "Escribe un número de puerto válido."
            );
        }
    }

    /**
     * Limpia saltos de línea y espacios adicionales.
     *
     * @param texto mensaje original.
     * @return texto preparado para enviarse.
     */
    private static String limpiarTexto(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    /**
     * Activa o desactiva los controles
     * utilizados para enviar mensajes.
     *
     * @param habilitado indica si se permite enviar.
     */
    private void habilitarEnvio(
            boolean habilitado
    ) {

        campoMensaje.setEnabled(
                habilitado
        );

        botonEnviar.setEnabled(
                habilitado
        );
    }

    /**
     * Cambia el texto de la etiqueta
     * que muestra el estado del servidor.
     *
     * @param texto estado que se mostrará.
     */
    private void actualizarEstado(
            String texto
    ) {

        etiquetaEstado.setText(
                texto
        );
    }

    /**
     * Agrega un mensaje al área de conversación.
     *
     * @param origen indica quién envió el mensaje.
     * @param mensaje contenido del mensaje.
     */
    private void registrarMensaje(
            String origen,
            String mensaje
    ) {

        areaConversacion.append(
                origen
                        + ": "
                        + mensaje
                        + System.lineSeparator()
        );

        /*
         * Se mueve automáticamente la vista
         * hacia el último mensaje recibido.
         */
        areaConversacion.setCaretPosition(
                areaConversacion
                        .getDocument()
                        .getLength()
        );
    }

    /**
     * Agrega información generada por
     * el propio programa.
     *
     * @param mensaje texto informativo.
     */
    private void registrarSistema(
            String mensaje
    ) {

        areaConversacion.append(
                "[Sistema] "
                        + mensaje
                        + System.lineSeparator()
        );

        areaConversacion.setCaretPosition(
                areaConversacion
                        .getDocument()
                        .getLength()
        );
    }

    /**
     * Muestra dentro de la interfaz un error
     * ocurrido en el hilo del servidor.
     *
     * @param mensaje descripción del problema.
     */
    private void notificarFalloServidor(
            String mensaje
    ) {

        ejecutarEnInterfaz(() -> {

            registrarSistema(
                    mensaje.replace(
                            '\n',
                            ' '
                    )
            );

            mostrarError(
                    mensaje
            );
        });
    }

    /**
     * Muestra una ventana emergente
     * con un mensaje de error.
     *
     * @param mensaje información que verá el usuario.
     */
    private void mostrarError(
            String mensaje
    ) {

        JOptionPane.showMessageDialog(
                this,
                mensaje,
                "Mensajería LAN",
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Ejecuta una tarea dentro del hilo
     * encargado de la interfaz de Swing.
     *
     * Los componentes gráficos no deben actualizarse
     * directamente desde el hilo que trabaja con la red.
     *
     * @param tarea código que modificará la interfaz.
     */
    private static void ejecutarEnInterfaz(
            Runnable tarea
    ) {

        if (
                SwingUtilities.isEventDispatchThread()
        ) {

            tarea.run();

        } else {

            SwingUtilities.invokeLater(
                    tarea
            );
        }
    }

    /**
     * Cierra un socket sin detener el programa
     * si el socket ya se encontraba cerrado.
     *
     * @param socket socket que se desea cerrar.
     */
    private static void cerrarSilenciosamente(
            Socket socket
    ) {

        if (socket != null) {

            try {

                socket.close();

            } catch (IOException ignored) {

                // No es necesario realizar otra acción.
            }
        }
    }

    /**
     * Obtiene las direcciones IPv4
     * disponibles en la computadora.
     *
     * @return lista con las direcciones encontradas.
     */
    private static List<String> obtenerDireccionesIPv4() {

        List<String> direcciones =
                new ArrayList<>();

        try {

            /*
             * Se consultan todos los adaptadores
             * de red disponibles en la computadora.
             */
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            if (interfaces == null) {

                return List.of(
                        "127.0.0.1"
                );
            }

            while (
                    interfaces.hasMoreElements()
            ) {

                NetworkInterface interfaz =
                        interfaces.nextElement();

                /*
                 * No se utilizan adaptadores apagados
                 * ni la interfaz local de loopback.
                 */
                if (
                        !interfaz.isUp()
                                || interfaz.isLoopback()
                ) {

                    continue;
                }

                Enumeration<InetAddress> direccionesInterfaz =
                        interfaz.getInetAddresses();

                while (
                        direccionesInterfaz.hasMoreElements()
                ) {

                    InetAddress direccion =
                            direccionesInterfaz.nextElement();

                    /*
                     * Solamente se muestran
                     * direcciones IPv4.
                     */
                    if (
                            direccion instanceof Inet4Address
                                    && !direccion.isLoopbackAddress()
                    ) {

                        direcciones.add(
                                direccion.getHostAddress()
                        );
                    }
                }
            }

        } catch (SocketException ignored) {

            /*
             * Si no se pueden consultar los adaptadores,
             * se utilizará localhost como alternativa.
             */
        }

        if (direcciones.isEmpty()) {

            direcciones.add(
                    "127.0.0.1"
            );
        }

        Collections.sort(
                direcciones
        );

        return direcciones;
    }

    /**
     * Une las direcciones IP encontradas
     * para mostrarlas en una sola etiqueta.
     *
     * @return direcciones IPv4 separadas por comas.
     */
    private static String obtenerDireccionesComoTexto() {

        return String.join(
                ", ",
                obtenerDireccionesIPv4()
        );
    }

    /**
     * Punto de entrada de la aplicación.
     *
     * @param args argumentos recibidos
     * desde la línea de comandos.
     */
    public static void main(
            String[] args
    ) {

        aplicarAparienciaDelSistema();

        /*
         * La creación de la ventana se realiza
         * dentro del hilo de eventos de Swing.
         */
        SwingUtilities.invokeLater(
                () -> new Servidor().setVisible(true)
        );
    }

    /**
     * Intenta utilizar la apariencia visual
     * del sistema operativo.
     */
    private static void aplicarAparienciaDelSistema() {

        try {

            UIManager.setLookAndFeel(
                    UIManager
                            .getSystemLookAndFeelClassName()
            );

        } catch (Exception ignored) {

            /*
             * Si no se puede aplicar,
             * Java utiliza su apariencia predeterminada.
             */
        }
    }
}