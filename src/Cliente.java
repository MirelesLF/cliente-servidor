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
 * Ventana utilizada por cada cliente del chat de red local.
 *
 * Cada computadora indica un nombre, la dirección IP del servidor
 * y el puerto. Después de conectarse puede enviar mensajes y recibir
 * los mensajes enviados por los demás participantes.
 */
public class Cliente extends JFrame {

    /*
     * Prefijos utilizados para que cliente y servidor puedan distinguir
     * el tipo de información recibida por el socket.
     */
    private static final String HELLO = "HELLO\t";
    private static final String MSG = "MSG\t";
    private static final String SYS = "SYS\t";

    // Nombre que identificará al usuario dentro de la conversación.
    private final JTextField campoNombre = new JTextField(nombrePredeterminado(), 10);

    // Dirección IP de la computadora donde se ejecuta el servidor.
    private final JTextField campoIp = new JTextField("127.0.0.1", 12);

    // Puerto utilizado por el servidor.
    private final JTextField campoPuerto = new JTextField("5000", 5);

    // Botón que permite conectar o desconectar al usuario.
    private final JButton botonConexion = new JButton("Conectar");

    // Muestra el estado actual de la conexión.
    private final JLabel etiquetaEstado = new JLabel("Estado: desconectado");

    // Área donde se muestra la conversación completa.
    private final JTextArea areaConversacion = new JTextArea();

    // Campo donde el usuario escribe un mensaje.
    private final JTextField campoMensaje = new JTextField();

    // Botón que envía el mensaje escrito.
    private final JButton botonEnviar = new JButton("Enviar");

    /*
     * Se utiliza como bloqueo al acceder al socket y al flujo de salida
     * desde diferentes partes del programa.
     */
    private final Object bloqueoConexion = new Object();

    // Indica si existe una conexión activa con el servidor.
    private volatile boolean conexionActiva;

    // Indica si el cliente se encuentra intentando conectarse.
    private volatile boolean conectando;

    // Socket TCP utilizado para comunicarse con el servidor.
    private Socket socket;

    // Flujo que permite enviar texto hacia el servidor.
    private PrintWriter salidaServidor;

    /**
     * Constructor de la ventana cliente.
     *
     * Crea la interfaz y prepara los eventos de los botones.
     */
    public Cliente() {
        super("Cliente - Mensajería LAN grupal");
        construirInterfaz();
        configurarEventos();
    }

    /**
     * Crea y organiza todos los componentes de la interfaz gráfica.
     */
    private void construirInterfaz() {
        // El cierre se controla manualmente para cerrar primero la conexión.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        setMinimumSize(new Dimension(780, 520));
        setSize(920, 600);

        // La ventana se abre centrada.
        setLocationRelativeTo(null);

        // Panel principal de la aplicación.
        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contenido);

        // Panel superior con nombre, IP, puerto y estado.
        JPanel panelSuperior = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0)
        );

        panelSuperior.add(new JLabel("Nombre:"));
        panelSuperior.add(campoNombre);
        panelSuperior.add(new JLabel("IP servidor:"));
        panelSuperior.add(campoIp);
        panelSuperior.add(new JLabel("Puerto:"));
        panelSuperior.add(campoPuerto);
        panelSuperior.add(botonConexion);
        panelSuperior.add(etiquetaEstado);

        contenido.add(panelSuperior, BorderLayout.NORTH);

        // El área de conversación es únicamente de lectura.
        areaConversacion.setEditable(false);
        areaConversacion.setLineWrap(true);
        areaConversacion.setWrapStyleWord(true);
        areaConversacion.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        areaConversacion.setBorder(new EmptyBorder(6, 6, 6, 6));

        // Agrega desplazamiento cuando existen muchos mensajes.
        JScrollPane desplazamiento = new JScrollPane(areaConversacion);
        desplazamiento.setBorder(BorderFactory.createTitledBorder("Conversación grupal"));
        contenido.add(desplazamiento, BorderLayout.CENTER);

        // Panel inferior para escribir el mensaje.
        JPanel panelMensaje = new JPanel(new BorderLayout(8, 0));
        panelMensaje.setBorder(BorderFactory.createTitledBorder("Mensaje"));
        panelMensaje.add(campoMensaje, BorderLayout.CENTER);
        panelMensaje.add(botonEnviar, BorderLayout.EAST);
        contenido.add(panelMensaje, BorderLayout.SOUTH);

        // El envío se habilita después de conectarse correctamente.
        habilitarEnvio(false);
    }

    /**
     * Configura las acciones de los botones, del campo de mensaje
     * y del cierre de la ventana.
     */
    private void configurarEventos() {
        botonConexion.addActionListener(evento -> {
            if (conexionActiva || conectando) {
                desconectar();
            } else {
                conectar();
            }
        });

        // El mensaje se puede enviar con el botón.
        botonEnviar.addActionListener(evento -> enviarMensaje());

        // También se puede enviar presionando Enter.
        campoMensaje.addActionListener(evento -> enviarMensaje());

        // Antes de cerrar la ventana se cierra el socket activo.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                desconectarSilenciosamente();
                dispose();
            }
        });
    }

    /**
     * Valida los datos escritos por el usuario y comienza la conexión.
     */
    private void conectar() {
        String nombre = limpiarTexto(campoNombre.getText());

        if (nombre.isBlank()) {
            mostrarError("Escribe un nombre para identificar esta computadora en el chat.");
            campoNombre.requestFocusInWindow();
            return;
        }

        String ip = campoIp.getText().trim();

        if (ip.isEmpty()) {
            mostrarError("Escribe la dirección IP de la computadora servidor.");
            campoIp.requestFocusInWindow();
            return;
        }

        int puerto;

        try {
            puerto = leerPuerto(campoPuerto.getText());
        } catch (IllegalArgumentException error) {
            mostrarError(error.getMessage());
            campoPuerto.requestFocusInWindow();
            return;
        }

        conectando = true;

        // Mientras se intenta conectar no se permite modificar estos campos.
        campoNombre.setEnabled(false);
        campoIp.setEnabled(false);
        campoPuerto.setEnabled(false);
        botonConexion.setText("Cancelar");
        actualizarEstado("Estado: conectando...");

        registrarSistema(
                "Intentando conectar con " + ip + ":" + puerto + "..."
        );

        /*
         * La conexión y la recepción de mensajes se ejecutan en otro hilo.
         * Esto permite que la interfaz siga respondiendo mientras el socket
         * está esperando información del servidor.
         */
        Thread hiloConexion = new Thread(
                () -> ejecutarConexion(nombre, ip, puerto),
                "cliente-chat"
        );

        hiloConexion.setDaemon(true);
        hiloConexion.start();
    }

    /**
     * Establece la conexión TCP y mantiene al cliente escuchando mensajes.
     *
     * @param nombre nombre con el que se identificará el usuario.
     * @param ip dirección IP del servidor.
     * @param puerto puerto donde escucha el servidor.
     */
    private void ejecutarConexion(String nombre, String ip, int puerto) {
        Socket nuevoSocket = new Socket();

        try {
            /*
             * Se intenta conectar al servidor.
             * El tiempo máximo de espera se establece en cinco segundos.
             */
            nuevoSocket.connect(
                    new InetSocketAddress(ip, puerto),
                    5000
            );

            // Mantiene activa la conexión TCP.
            nuevoSocket.setKeepAlive(true);

            // Evita retrasos innecesarios al enviar mensajes pequeños.
            nuevoSocket.setTcpNoDelay(true);

            // Flujo para leer mensajes enviados desde el servidor.
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                            nuevoSocket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            // Flujo para enviar mensajes al servidor.
            PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(
                            nuevoSocket.getOutputStream(),
                            StandardCharsets.UTF_8
                    ),
                    true
            );

            synchronized (bloqueoConexion) {
                /*
                 * Si el usuario canceló mientras se realizaba la conexión,
                 * el socket recién conectado ya no se utiliza.
                 */
                if (!conectando) {
                    cerrarSilenciosamente(nuevoSocket);
                    return;
                }

                socket = nuevoSocket;
                salidaServidor = salida;
                conexionActiva = true;
                conectando = false;
            }

            /*
             * La primera línea enviada al servidor contiene el nombre.
             * El servidor usa esta información para identificar los mensajes.
             */
            salida.println(HELLO + nombre);

            ejecutarEnInterfaz(() -> {
                botonConexion.setText("Desconectar");
                actualizarEstado("Estado: conectado");
                registrarSistema("Conexión establecida con el servidor.");
                habilitarEnvio(true);
                campoMensaje.requestFocusInWindow();
            });

            String linea;

            /*
             * Este ciclo mantiene al hilo esperando información del servidor.
             * Cada mensaje recibido se procesa y después el hilo vuelve a esperar.
             */
            while (conexionActiva && (linea = entrada.readLine()) != null) {
                final String lineaRecibida = linea;

                // La actualización visual se realiza en el hilo de Swing.
                ejecutarEnInterfaz(
                        () -> procesarLineaRecibida(lineaRecibida)
                );
            }

            if (conexionActiva) {
                ejecutarEnInterfaz(
                        () -> registrarSistema("El servidor cerró la conexión.")
                );
            }

        } catch (SocketException error) {
            if (conectando || conexionActiva) {
                ejecutarEnInterfaz(
                        () -> registrarSistema("La conexión se interrumpió.")
                );
            }

        } catch (IOException error) {
            if (conectando || conexionActiva) {
                ejecutarEnInterfaz(() -> {
                    registrarSistema(
                            "No fue posible conectar: " + error.getMessage()
                    );

                    mostrarError(
                            "No fue posible conectar con el servidor.\n\n"
                                    + "Verifica que:\n"
                                    + "• El servidor esté iniciado.\n"
                                    + "• La IP y el puerto sean correctos.\n"
                                    + "• Todos los equipos estén en la misma red.\n"
                                    + "• El firewall permita la conexión.\n\n"
                                    + "Detalle: " + error.getMessage()
                    );
                });
            }

        } finally {
            // Siempre se cierra el socket y se limpia el estado al terminar el hilo.
            cerrarSilenciosamente(nuevoSocket);
            limpiarEstadoConexion();
            ejecutarEnInterfaz(this::mostrarEstadoDesconectado);
        }
    }

    /**
     * Interpreta una línea recibida desde el servidor.
     *
     * Los mensajes de chat y los mensajes del sistema tienen formatos diferentes.
     *
     * @param linea texto recibido por el socket.
     */
    private void procesarLineaRecibida(String linea) {
        if (linea.startsWith(MSG)) {
            String contenido = linea.substring(MSG.length());

            /*
             * Dentro del mensaje se separan el nombre del usuario
             * y el texto utilizando un tabulador.
             */
            int separador = contenido.indexOf('\t');

            if (separador >= 0) {
                String origen = contenido.substring(0, separador);
                String mensaje = contenido.substring(separador + 1);
                registrarMensaje(origen, mensaje);
            }

            return;
        }

        // Los mensajes SYS se muestran como información del sistema.
        if (linea.startsWith(SYS)) {
            registrarSistema(
                    linea.substring(SYS.length())
            );
            return;
        }

        // Si llega una línea desconocida también se muestra para no perder información.
        registrarSistema(linea);
    }

    /**
     * Envía al servidor el mensaje escrito en el campo de texto.
     */
    private void enviarMensaje() {
        String mensaje = limpiarTexto(campoMensaje.getText());

        if (mensaje.isBlank()) {
            return;
        }

        PrintWriter salida;

        synchronized (bloqueoConexion) {
            salida = salidaServidor;
        }

        if (!conexionActiva || salida == null) {
            mostrarError("No hay conexión con el servidor.");
            return;
        }

        /*
         * El cliente solo envía el contenido del mensaje.
         * El servidor es quien agrega el nombre antes de distribuirlo al grupo.
         */
        salida.println(MSG + mensaje);

        if (salida.checkError()) {
            mostrarError(
                    "No fue posible enviar el mensaje. La conexión puede haberse cerrado."
            );
            return;
        }

        // El mensaje llegará de regreso mediante la distribución del servidor.
        campoMensaje.setText("");
        campoMensaje.requestFocusInWindow();
    }

    /**
     * Cierra la conexión cuando el usuario presiona Desconectar.
     */
    private void desconectar() {
        boolean habiaConexion = conexionActiva || conectando;

        conectando = false;
        conexionActiva = false;
        cerrarSocketActual();

        if (habiaConexion) {
            registrarSistema("Conexión cerrada por el usuario.");
        }

        mostrarEstadoDesconectado();
    }

    /**
     * Cierra la conexión sin mostrar mensajes.
     * Se utiliza cuando se cierra completamente la ventana.
     */
    private void desconectarSilenciosamente() {
        conectando = false;
        conexionActiva = false;
        cerrarSocketActual();
    }

    /**
     * Cierra el socket actual y elimina el flujo de salida guardado.
     */
    private void cerrarSocketActual() {
        synchronized (bloqueoConexion) {
            cerrarSilenciosamente(socket);
            socket = null;
            salidaServidor = null;
        }
    }

    /**
     * Restablece las variables relacionadas con la conexión.
     */
    private void limpiarEstadoConexion() {
        synchronized (bloqueoConexion) {
            conexionActiva = false;
            conectando = false;
            cerrarSilenciosamente(socket);
            socket = null;
            salidaServidor = null;
        }
    }

    /**
     * Devuelve la interfaz a su estado inicial después de desconectarse.
     */
    private void mostrarEstadoDesconectado() {
        campoNombre.setEnabled(true);
        campoIp.setEnabled(true);
        campoPuerto.setEnabled(true);
        botonConexion.setText("Conectar");
        botonConexion.setEnabled(true);
        actualizarEstado("Estado: desconectado");
        habilitarEnvio(false);
    }

    /**
     * Cierra un socket sin detener el programa si ya se encontraba cerrado.
     *
     * @param socket socket que se desea cerrar.
     */
    private static void cerrarSilenciosamente(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // No es necesario realizar otra acción.
            }
        }
    }

    /**
     * Limpia caracteres que podrían afectar el formato usado por el protocolo.
     *
     * @param texto texto original.
     * @return texto listo para enviarse.
     */
    private static String limpiarTexto(String texto) {
        if (texto == null) {
            return "";
        }

        return texto
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    /**
     * Obtiene un nombre inicial utilizando el usuario del sistema operativo.
     *
     * @return nombre que se mostrará inicialmente en la interfaz.
     */
    private static String nombrePredeterminado() {
        String nombre = System.getProperty("user.name", "Usuario");
        String limpio = limpiarTexto(nombre);

        return limpio.isBlank() ? "Usuario" : limpio;
    }

    /**
     * Convierte y valida el puerto indicado por el usuario.
     *
     * @param texto contenido del campo de puerto.
     * @return puerto válido.
     */
    private static int leerPuerto(String texto) {
        try {
            int puerto = Integer.parseInt(texto.trim());

            if (puerto < 1024 || puerto > 65535) {
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
     * Activa o desactiva los controles para enviar mensajes.
     *
     * @param habilitado true cuando el cliente puede enviar.
     */
    private void habilitarEnvio(boolean habilitado) {
        campoMensaje.setEnabled(habilitado);
        botonEnviar.setEnabled(habilitado);
    }

    /**
     * Cambia el texto de la etiqueta que muestra el estado de la conexión.
     *
     * @param texto nuevo estado.
     */
    private void actualizarEstado(String texto) {
        etiquetaEstado.setText(texto);
    }

    /**
     * Agrega un mensaje normal al área de conversación.
     *
     * @param origen nombre de quien envió el mensaje.
     * @param mensaje contenido recibido.
     */
    private void registrarMensaje(String origen, String mensaje) {
        areaConversacion.append(
                origen + ": " + mensaje + System.lineSeparator()
        );

        // Se desplaza automáticamente hasta el último mensaje.
        areaConversacion.setCaretPosition(
                areaConversacion.getDocument().getLength()
        );
    }

    /**
     * Agrega información generada por la aplicación.
     *
     * @param mensaje información que se mostrará.
     */
    private void registrarSistema(String mensaje) {
        areaConversacion.append(
                "[Sistema] " + mensaje + System.lineSeparator()
        );

        areaConversacion.setCaretPosition(
                areaConversacion.getDocument().getLength()
        );
    }

    /**
     * Muestra un cuadro de diálogo cuando ocurre un problema.
     *
     * @param mensaje detalle del error.
     */
    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(
                this,
                mensaje,
                "Mensajería LAN",
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Ejecuta una tarea dentro del hilo de eventos de Swing.
     *
     * Los mensajes llegan desde un hilo de red, por lo que las modificaciones
     * de componentes visuales se mandan a Swing mediante este método.
     *
     * @param tarea código que actualizará la interfaz.
     */
    private static void ejecutarEnInterfaz(Runnable tarea) {
        if (SwingUtilities.isEventDispatchThread()) {
            tarea.run();
        } else {
            SwingUtilities.invokeLater(tarea);
        }
    }

    /**
     * Método principal de la aplicación cliente.
     *
     * @param args argumentos recibidos desde la línea de comandos.
     */
    public static void main(String[] args) {
        aplicarAparienciaDelSistema();

        // La ventana se crea dentro del hilo gráfico de Swing.
        SwingUtilities.invokeLater(
                () -> new Cliente().setVisible(true)
        );
    }

    /**
     * Intenta utilizar la apariencia visual del sistema operativo.
     */
    private static void aplicarAparienciaDelSistema() {
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName()
            );
        } catch (Exception ignored) {
            // Si ocurre un problema se conserva la apariencia predeterminada de Java.
        }
    }
}
