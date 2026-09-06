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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Ventana que funciona como cliente de mensajería
 * dentro de una red local.
 *
 * El cliente se conecta utilizando la IP y el puerto
 * del servidor y mantiene abierta la conexión
 * para enviar y recibir varios mensajes.
 */
public class Cliente extends JFrame {

    // Campo donde se escribe la dirección IP del servidor.
    private final JTextField campoIp =
            new JTextField(
                    "127.0.0.1",
                    12
            );

    // Campo donde se indica el puerto utilizado por el servidor.
    private final JTextField campoPuerto =
            new JTextField(
                    "5000",
                    5
            );

    // Botón utilizado para conectar o desconectar al cliente.
    private final JButton botonConexion =
            new JButton(
                    "Conectar"
            );

    // Etiqueta que muestra el estado actual de la conexión.
    private final JLabel etiquetaEstado =
            new JLabel(
                    "Estado: desconectado"
            );

    // Área donde se muestran los mensajes de la conversación.
    private final JTextArea areaConversacion =
            new JTextArea();

    // Campo utilizado para escribir cada mensaje.
    private final JTextField campoMensaje =
            new JTextField();

    // Botón que envía el mensaje escrito.
    private final JButton botonEnviar =
            new JButton(
                    "Enviar"
            );

    /*
     * Este objeto ayuda a controlar el acceso
     * a los datos relacionados con la conexión.
     */
    private final Object bloqueoConexion =
            new Object();

    // Indica si existe una conexión activa con el servidor.
    private volatile boolean conexionActiva;

    // Indica si en ese momento se está intentando conectar.
    private volatile boolean conectando;

    // Socket que representa la conexión con el servidor.
    private Socket socket;

    // Flujo utilizado para enviar mensajes hacia el servidor.
    private PrintWriter salidaServidor;

    /**
     * Constructor de la ventana del cliente.
     *
     * Se encarga de crear la interfaz
     * y configurar sus eventos.
     */
    public Cliente() {

        super(
                "Cliente - Mensajería LAN"
        );

        construirInterfaz();

        configurarEventos();
    }

    /**
     * Construye y organiza todos los componentes
     * que forman la interfaz gráfica.
     */
    private void construirInterfaz() {

        setDefaultCloseOperation(
                JFrame.DO_NOTHING_ON_CLOSE
        );

        setMinimumSize(
                new Dimension(
                        780,
                        520
                )
        );

        setSize(
                900,
                600
        );

        // La ventana aparece centrada.
        setLocationRelativeTo(null);

        // Panel principal.
        JPanel contenido =
                new JPanel(
                        new BorderLayout(
                                10,
                                10
                        )
                );

        contenido.setBorder(
                new EmptyBorder(
                        12,
                        12,
                        12,
                        12
                )
        );

        setContentPane(
                contenido
        );

        /*
         * Panel superior donde se indican
         * la IP y el puerto del servidor.
         */
        JPanel panelSuperior =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                8,
                                0
                        )
                );

        panelSuperior.add(
                new JLabel(
                        "IP del servidor:"
                )
        );

        panelSuperior.add(
                campoIp
        );

        panelSuperior.add(
                new JLabel(
                        "Puerto:"
                )
        );

        panelSuperior.add(
                campoPuerto
        );

        panelSuperior.add(
                botonConexion
        );

        panelSuperior.add(
                etiquetaEstado
        );

        contenido.add(
                panelSuperior,
                BorderLayout.NORTH
        );

        // El usuario no puede modificar directamente la conversación.
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
         * El JScrollPane agrega desplazamiento
         * cuando existen muchos mensajes.
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
         * Panel inferior utilizado
         * para escribir y enviar mensajes.
         */
        JPanel panelMensaje =
                new JPanel(
                        new BorderLayout(
                                8,
                                0
                        )
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
         * El envío se habilita únicamente
         * cuando existe conexión con el servidor.
         */
        habilitarEnvio(false);
    }

    /**
     * Configura las acciones de los botones
     * y el cierre de la ventana.
     */
    private void configurarEventos() {

        /*
         * El botón permite conectarse
         * o desconectarse dependiendo del estado.
         */
        botonConexion.addActionListener(
                evento -> {

                    if (
                            conexionActiva
                                    || conectando
                    ) {

                        desconectar();

                    } else {

                        conectar();
                    }
                }
        );

        /*
         * El mensaje se puede enviar
         * utilizando el botón.
         */
        botonEnviar.addActionListener(
                evento -> enviarMensaje()
        );

        /*
         * También se puede enviar
         * presionando Enter.
         */
        campoMensaje.addActionListener(
                evento -> enviarMensaje()
        );

        /*
         * Antes de cerrar la ventana
         * se cierra la conexión con el servidor.
         */
        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosing(
                            WindowEvent evento
                    ) {

                        desconectarSilenciosamente();

                        dispose();
                    }
                }
        );
    }

    /**
     * Valida la IP y el puerto escritos
     * y comienza el intento de conexión.
     */
    private void conectar() {

        String ip =
                campoIp
                        .getText()
                        .trim();

        // Se verifica que exista una dirección escrita.
        if (ip.isEmpty()) {

            mostrarError(
                    "Escribe la dirección IP de la computadora servidor."
            );

            campoIp.requestFocusInWindow();

            return;
        }

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

        conectando = true;

        campoIp.setEnabled(false);

        campoPuerto.setEnabled(false);

        botonConexion.setText(
                "Cancelar"
        );

        actualizarEstado(
                "Estado: conectando..."
        );

        registrarSistema(
                "Intentando conectar con "
                        + ip
                        + ":"
                        + puerto
                        + "..."
        );

        /*
         * La conexión y la recepción de mensajes
         * se ejecutan en un hilo separado.
         *
         * De esta manera la interfaz sigue respondiendo
         * aunque la red esté esperando información.
         */
        Thread hiloConexion =
                new Thread(
                        () -> ejecutarConexion(
                                ip,
                                puerto
                        ),
                        "hilo-cliente"
                );

        hiloConexion.setDaemon(true);

        hiloConexion.start();
    }

    /**
     * Establece la conexión con el servidor
     * y mantiene una lectura continua de mensajes.
     *
     * @param ip dirección IP del servidor.
     * @param puerto puerto TCP utilizado para conectarse.
     */
    private void ejecutarConexion(
            String ip,
            int puerto
    ) {

        Socket nuevoSocket =
                new Socket();

        try {

            /*
             * Se intenta realizar la conexión.
             *
             * El valor 5000 representa cinco segundos
             * de tiempo máximo para esperar respuesta.
             */
            nuevoSocket.connect(
                    new InetSocketAddress(
                            ip,
                            puerto
                    ),
                    5000
            );

            /*
             * Mantiene la conexión TCP activa
             * y ayuda a detectar desconexiones.
             */
            nuevoSocket.setKeepAlive(true);

            /*
             * Permite enviar mensajes pequeños
             * sin esperar a agrupar varios datos.
             */
            nuevoSocket.setTcpNoDelay(true);

            /*
             * BufferedReader recibe
             * los mensajes enviados por el servidor.
             */
            BufferedReader entrada =
                    new BufferedReader(
                            new InputStreamReader(
                                    nuevoSocket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

            /*
             * PrintWriter se utiliza
             * para enviar mensajes al servidor.
             */
            PrintWriter salida =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    nuevoSocket.getOutputStream(),
                                    StandardCharsets.UTF_8
                            ),
                            true
                    );

            /*
             * Se guardan el socket y el flujo de salida
             * para que puedan utilizarse desde la interfaz.
             */
            synchronized (bloqueoConexion) {

                /*
                 * Si el usuario canceló mientras se intentaba
                 * conectar, el nuevo socket ya no se utiliza.
                 */
                if (!conectando) {

                    cerrarSilenciosamente(
                            nuevoSocket
                    );

                    return;
                }

                socket =
                        nuevoSocket;

                salidaServidor =
                        salida;

                conexionActiva =
                        true;

                conectando =
                        false;
            }

            /*
             * Se actualiza la ventana
             * después de conectar correctamente.
             */
            ejecutarEnInterfaz(() -> {

                botonConexion.setText(
                        "Desconectar"
                );

                actualizarEstado(
                        "Estado: conectado"
                );

                registrarSistema(
                        "Conexión establecida con el servidor."
                );

                habilitarEnvio(true);

                campoMensaje.requestFocusInWindow();
            });

            String mensaje;

            /*
             * El hilo permanece escuchando al servidor.
             *
             * Gracias al ciclo se pueden recibir
             * varios mensajes durante la misma conexión.
             */
            while (
                    conexionActiva
                            && (mensaje = entrada.readLine()) != null
            ) {

                final String mensajeRecibido =
                        mensaje;

                /*
                 * El texto recibido se agrega
                 * al área de conversación.
                 */
                ejecutarEnInterfaz(
                        () -> registrarMensaje(
                                "Servidor",
                                mensajeRecibido
                        )
                );
            }

            /*
             * Si readLine() termina mientras todavía
             * se consideraba activa la conexión,
             * significa que el servidor se desconectó.
             */
            if (conexionActiva) {

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "El servidor cerró la conexión."
                        )
                );
            }

        } catch (SocketException error) {

            /*
             * SocketException aparece normalmente
             * cuando una conexión se cierra inesperadamente.
             */
            if (
                    conectando
                            || conexionActiva
            ) {

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "La conexión se interrumpió."
                        )
                );
            }

        } catch (IOException error) {

            /*
             * Aquí se manejan problemas como IP incorrecta,
             * servidor apagado o bloqueo por firewall.
             */
            if (
                    conectando
                            || conexionActiva
            ) {

                ejecutarEnInterfaz(() -> {

                    registrarSistema(
                            "No fue posible conectar: "
                                    + error.getMessage()
                    );

                    mostrarError(
                            "No fue posible conectar con el servidor.\n\n"
                                    + "Verifica que:\n"
                                    + "• El servidor esté iniciado.\n"
                                    + "• La IP y el puerto sean correctos.\n"
                                    + "• Las dos computadoras estén en la misma red.\n"
                                    + "• El firewall permita la conexión.\n\n"
                                    + "Detalle: "
                                    + error.getMessage()
                    );
                });
            }

        } finally {

            /*
             * Independientemente del resultado,
             * se liberan los recursos utilizados.
             */
            cerrarSilenciosamente(
                    nuevoSocket
            );

            limpiarEstadoConexion();

            ejecutarEnInterfaz(
                    this::mostrarEstadoDesconectado
            );
        }
    }

    /**
     * Envía el mensaje escrito al servidor
     * y lo muestra en la conversación local.
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

        synchronized (bloqueoConexion) {

            salida =
                    salidaServidor;
        }

        /*
         * Se verifica que exista una conexión
         * antes de intentar enviar.
         */
        if (
                !conexionActiva
                        || salida == null
        ) {

            mostrarError(
                    "No hay conexión con el servidor."
            );

            return;
        }

        /*
         * println envía el texto junto
         * con un salto de línea.
         */
        salida.println(
                mensaje
        );

        /*
         * checkError permite detectar
         * si PrintWriter tuvo un problema al enviar.
         */
        if (salida.checkError()) {

            mostrarError(
                    "No fue posible enviar el mensaje. "
                            + "La conexión puede haberse cerrado."
            );

            return;
        }

        /*
         * El mensaje enviado también se muestra
         * dentro de la conversación del cliente.
         */
        registrarMensaje(
                "Cliente",
                mensaje
        );

        campoMensaje.setText("");

        campoMensaje.requestFocusInWindow();
    }

    /**
     * Cierra la conexión cuando
     * el usuario presiona Desconectar.
     */
    private void desconectar() {

        boolean habiaConexion =
                conexionActiva
                        || conectando;

        conectando = false;

        conexionActiva = false;

        cerrarSocketActual();

        if (habiaConexion) {

            registrarSistema(
                    "Conexión cerrada por el usuario."
            );
        }

        mostrarEstadoDesconectado();
    }

    /**
     * Cierra la conexión sin mostrar mensajes.
     *
     * Se utiliza cuando el usuario
     * cierra completamente la ventana.
     */
    private void desconectarSilenciosamente() {

        conectando = false;

        conexionActiva = false;

        cerrarSocketActual();
    }

    /**
     * Cierra el socket actual
     * y elimina el flujo de salida.
     */
    private void cerrarSocketActual() {

        synchronized (bloqueoConexion) {

            cerrarSilenciosamente(
                    socket
            );

            socket = null;

            salidaServidor = null;
        }
    }

    /**
     * Limpia las variables relacionadas
     * con una conexión que acaba de terminar.
     */
    private void limpiarEstadoConexion() {

        synchronized (bloqueoConexion) {

            conexionActiva = false;

            conectando = false;

            cerrarSilenciosamente(
                    socket
            );

            socket = null;

            salidaServidor = null;
        }
    }

    /**
     * Devuelve la interfaz a su estado
     * inicial después de desconectarse.
     */
    private void mostrarEstadoDesconectado() {

        campoIp.setEnabled(true);

        campoPuerto.setEnabled(true);

        botonConexion.setText(
                "Conectar"
        );

        botonConexion.setEnabled(true);

        actualizarEstado(
                "Estado: desconectado"
        );

        habilitarEnvio(false);
    }

    /**
     * Habilita o deshabilita
     * los controles para enviar mensajes.
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
     * Cambia el texto que muestra
     * el estado de la conexión.
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
     * @param mensaje contenido enviado o recibido.
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
         * La conversación se desplaza automáticamente
         * hasta el mensaje más reciente.
         */
        areaConversacion.setCaretPosition(
                areaConversacion
                        .getDocument()
                        .getLength()
        );
    }

    /**
     * Agrega información relacionada
     * con el funcionamiento del programa.
     *
     * @param mensaje información que se mostrará.
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
     * Valida el puerto indicado en la interfaz.
     *
     * @param texto contenido del campo del puerto.
     * @return número de puerto válido.
     */
    private static int leerPuerto(
            String texto
    ) {

        try {

            int puerto =
                    Integer.parseInt(
                            texto.trim()
                    );

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
     * Elimina saltos de línea
     * y espacios adicionales de un mensaje.
     *
     * @param texto texto original.
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
     * Cierra un socket sin detener el programa
     * si ya se encontraba cerrado.
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
     * Muestra un cuadro de diálogo
     * con información sobre un error.
     *
     * @param mensaje detalle del problema.
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
     * @param tarea código que actualizará la ventana.
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
     * Punto de entrada de la aplicación cliente.
     *
     * @param args argumentos recibidos
     * desde la línea de comandos.
     */
    public static void main(
            String[] args
    ) {

        aplicarAparienciaDelSistema();

        /*
         * Swing crea la ventana
         * dentro de su hilo de eventos.
         */
        SwingUtilities.invokeLater(
                () -> new Cliente().setVisible(true)
        );
    }

    /**
     * Intenta utilizar la apariencia
     * visual del sistema operativo.
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
             * se utiliza la apariencia predeterminada.
             */
        }
    }
}