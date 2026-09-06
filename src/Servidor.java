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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ventana principal del servidor de mensajería LAN.
 *
 * El servidor abre un puerto TCP y permite que varias computadoras
 * se conecten al mismo tiempo. Cada cliente conectado se atiende en
 * un hilo independiente para que todos puedan enviar mensajes sin
 * detener la comunicación de los demás.
 *
 * Los mensajes enviados por un cliente llegan primero al servidor y
 * después el servidor los distribuye a todos los clientes conectados.
 */
public class Servidor extends JFrame {

    /*
     * Prefijos sencillos que se utilizan para distinguir el tipo de
     * información que viaja por el socket.
     *
     * HELLO indica el nombre con el que se identifica un cliente.
     * MSG indica que la línea contiene un mensaje del chat.
     * SYS indica que la línea contiene información generada por el sistema.
     */
    private static final String HELLO = "HELLO\t";
    private static final String MSG = "MSG\t";
    private static final String SYS = "SYS\t";

    // Campo donde se escribe el puerto que utilizará el servidor.
    private final JTextField campoPuerto = new JTextField("5000", 6);

    // Botón utilizado para iniciar o detener el servidor.
    private final JButton botonServidor = new JButton("Iniciar servidor");

    // Etiqueta que muestra el estado actual del servidor.
    private final JLabel etiquetaEstado = new JLabel("Estado: detenido");

    // Muestra las direcciones IPv4 disponibles en la computadora.
    private final JLabel etiquetaIp = new JLabel(
            "IP de esta computadora: " + RedUtil.obtenerDireccionesComoTexto()
    );

    // Área donde se muestra la conversación y los mensajes del sistema.
    private final JTextArea areaConversacion = new JTextArea();

    // Campo para escribir un mensaje desde el servidor.
    private final JTextField campoMensaje = new JTextField();

    // Botón para enviar un mensaje a todos los clientes conectados.
    private final JButton botonEnviar = new JButton("Enviar a todos");

    /*
     * Este objeto se usa como bloqueo cuando se accede al ServerSocket.
     * Así se evita que dos partes del programa intenten modificarlo al mismo tiempo.
     */
    private final Object bloqueoServidor = new Object();

    /*
     * Lista de clientes conectados.
     * CopyOnWriteArrayList facilita recorrer la lista mientras otros hilos
     * agregan o eliminan clientes.
     */
    private final List<ClienteConectado> clientes = new CopyOnWriteArrayList<>();

    // Se utiliza para dar un nombre diferente a cada hilo de cliente.
    private final AtomicInteger contadorClientes = new AtomicInteger(1);

    // Indica si el servidor se encuentra encendido.
    private volatile boolean servidorActivo;

    // Socket principal que permanece escuchando nuevas conexiones.
    private ServerSocket servidorSocket;

    /**
     * Constructor de la ventana del servidor.
     *
     * Primero se crea la interfaz y después se preparan los eventos
     * de los botones y del cierre de la ventana.
     */
    public Servidor() {
        super("Servidor - Mensajería LAN grupal");
        construirInterfaz();
        configurarEventos();
    }

    /**
     * Crea y acomoda los elementos visuales de la aplicación.
     */
    private void construirInterfaz() {
        // El cierre se controla manualmente para poder cerrar los sockets antes de salir.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Se define un tamaño mínimo para evitar que la interfaz quede demasiado pequeña.
        setMinimumSize(new Dimension(760, 520));
        setSize(900, 600);

        // La ventana se abre al centro de la pantalla.
        setLocationRelativeTo(null);

        // Panel principal de la ventana.
        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contenido);

        // Panel superior con la configuración del servidor.
        JPanel panelSuperior = new JPanel(new BorderLayout(8, 8));
        JPanel panelConfiguracion = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        panelConfiguracion.add(new JLabel("Puerto:"));
        panelConfiguracion.add(campoPuerto);
        panelConfiguracion.add(botonServidor);
        panelConfiguracion.add(etiquetaEstado);

        // La IP se muestra en negritas para que sea fácil identificarla.
        etiquetaIp.setFont(etiquetaIp.getFont().deriveFont(Font.BOLD));
        etiquetaIp.setToolTipText(
                "Los clientes deben escribir una IP válida de esta computadora."
        );

        panelSuperior.add(panelConfiguracion, BorderLayout.NORTH);
        panelSuperior.add(etiquetaIp, BorderLayout.SOUTH);
        contenido.add(panelSuperior, BorderLayout.NORTH);

        // El área del chat solo sirve para mostrar información.
        areaConversacion.setEditable(false);
        areaConversacion.setLineWrap(true);
        areaConversacion.setWrapStyleWord(true);
        areaConversacion.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        areaConversacion.setBorder(new EmptyBorder(6, 6, 6, 6));

        // El JScrollPane permite desplazarse cuando la conversación crece.
        JScrollPane desplazamiento = new JScrollPane(areaConversacion);
        desplazamiento.setBorder(BorderFactory.createTitledBorder("Conversación grupal"));
        contenido.add(desplazamiento, BorderLayout.CENTER);

        // Panel inferior para escribir y enviar mensajes desde el servidor.
        JPanel panelMensaje = new JPanel(new BorderLayout(8, 0));
        panelMensaje.setBorder(BorderFactory.createTitledBorder("Mensaje del servidor"));
        panelMensaje.add(campoMensaje, BorderLayout.CENTER);
        panelMensaje.add(botonEnviar, BorderLayout.EAST);
        contenido.add(panelMensaje, BorderLayout.SOUTH);

        // No se permite enviar hasta que exista por lo menos un cliente conectado.
        habilitarEnvio(false);
    }

    /**
     * Configura las acciones que se ejecutan al presionar botones
     * o al cerrar la ventana.
     */
    private void configurarEventos() {
        botonServidor.addActionListener(evento -> {
            if (servidorActivo) {
                detenerServidor();
            } else {
                iniciarServidor();
            }
        });

        // El mensaje se puede enviar con el botón.
        botonEnviar.addActionListener(evento -> enviarMensaje());

        // También se puede enviar presionando Enter dentro del campo de texto.
        campoMensaje.addActionListener(evento -> enviarMensaje());

        // Antes de cerrar la ventana se detiene el servidor y se cierran las conexiones.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                detenerServidorSilenciosamente();
                dispose();
            }
        });
    }

    /**
     * Valida el puerto escrito y crea el hilo que mantendrá al servidor
     * esperando conexiones.
     */
    private void iniciarServidor() {
        int puerto;

        try {
            puerto = leerPuerto(campoPuerto.getText());
        } catch (IllegalArgumentException error) {
            mostrarError(error.getMessage());
            campoPuerto.requestFocusInWindow();
            return;
        }

        servidorActivo = true;
        campoPuerto.setEnabled(false);
        botonServidor.setText("Detener servidor");
        botonServidor.setEnabled(false);
        actualizarEstado("Estado: iniciando...");

        /*
         * La espera de conexiones se realiza en otro hilo.
         * Esto evita que la interfaz gráfica se congele mientras accept()
         * permanece esperando a que alguna computadora se conecte.
         */
        Thread hiloServidor = new Thread(
                () -> ejecutarServidor(puerto),
                "servidor-chat"
        );

        // Un hilo daemon termina junto con la aplicación si la ventana se cierra.
        hiloServidor.setDaemon(true);
        hiloServidor.start();
    }

    /**
     * Abre el puerto TCP y acepta clientes mientras el servidor siga activo.
     *
     * Cada nueva conexión crea un hilo diferente que ejecuta atenderCliente().
     *
     * @param puerto puerto donde escuchará el servidor.
     */
    private void ejecutarServidor(int puerto) {
        try {
            // Se crea el ServerSocket sin asociarlo todavía a un puerto.
            ServerSocket nuevoServidor = new ServerSocket();

            // Permite volver a utilizar el puerto después de reiniciar el servidor.
            nuevoServidor.setReuseAddress(true);

            // Se asocia el socket al puerto seleccionado.
            nuevoServidor.bind(new InetSocketAddress(puerto));

            synchronized (bloqueoServidor) {
                servidorSocket = nuevoServidor;
            }

            // Las modificaciones visuales se mandan al hilo de Swing.
            ejecutarEnInterfaz(() -> {
                botonServidor.setEnabled(true);
                actualizarEstadoClientes();
                registrarSistema("Servidor iniciado en el puerto " + puerto + ".");
                registrarSistema(
                        "Las computadoras pueden conectarse usando una de estas IP: "
                                + RedUtil.obtenerDireccionesComoTexto()
                );
            });

            /*
             * Este ciclo permite aceptar varias conexiones.
             * El servidor vuelve a ejecutar accept() cada vez que un cliente entra.
             */
            while (servidorActivo) {
                Socket nuevoCliente = nuevoServidor.accept();
                configurarSocket(nuevoCliente);

                /*
                 * Cada cliente se atiende en su propio hilo.
                 * De esta forma un cliente esperando o enviando información
                 * no bloquea la comunicación de los demás.
                 */
                Thread hiloCliente = new Thread(
                        () -> atenderCliente(nuevoCliente),
                        "cliente-" + contadorClientes.getAndIncrement()
                );

                hiloCliente.setDaemon(true);
                hiloCliente.start();
            }

        } catch (SocketException error) {
            // Esta excepción también ocurre de forma normal al cerrar el ServerSocket.
            if (servidorActivo) {
                notificarFalloServidor(
                        "Se perdió la conexión del servidor: " + error.getMessage()
                );
            }

        } catch (IOException error) {
            if (servidorActivo) {
                notificarFalloServidor(
                        "No fue posible iniciar o mantener el servidor.\n\nDetalle: "
                                + error.getMessage()
                );
            }

        } finally {
            // Siempre se liberan los recursos al terminar el hilo principal del servidor.
            cerrarRecursosServidor();
            servidorActivo = false;

            ejecutarEnInterfaz(() -> {
                campoPuerto.setEnabled(true);
                botonServidor.setText("Iniciar servidor");
                botonServidor.setEnabled(true);
                habilitarEnvio(false);
                actualizarEstado("Estado: detenido");
            });
        }
    }

    /**
     * Atiende a una computadora cliente durante toda su conexión.
     *
     * Primero recibe el nombre del usuario y después permanece leyendo
     * mensajes. Cada mensaje recibido se distribuye a todos los clientes.
     *
     * @param socket conexión TCP del cliente.
     */
    private void atenderCliente(Socket socket) {
        String direccionRemota = socket.getInetAddress().getHostAddress();
        ClienteConectado cliente = null;

        try {
            // Flujo para leer texto enviado desde el cliente.
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            // Flujo para enviar texto hacia el cliente.
            PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8
                    ),
                    true
            );

            /*
             * La primera línea debe contener el nombre del cliente.
             * Esto permite identificar después quién mandó cada mensaje.
             */
            String saludo = entrada.readLine();

            if (saludo == null || !saludo.startsWith(HELLO)) {
                salida.println(
                        SYS + "Conexión rechazada: el cliente no envió un nombre válido."
                );
                return;
            }

            String nombreSolicitado = limpiarTexto(
                    saludo.substring(HELLO.length())
            );

            // Si el nombre ya está ocupado se agrega un número al final.
            String nombre = obtenerNombreDisponible(
                    nombreSolicitado.isBlank() ? "Usuario" : nombreSolicitado
            );

            // Se guardan los datos de la conexión dentro de un objeto auxiliar.
            cliente = new ClienteConectado(
                    socket,
                    salida,
                    nombre,
                    direccionRemota
            );

            clientes.add(cliente);

            final ClienteConectado clienteFinal = cliente;

            ejecutarEnInterfaz(() -> {
                registrarSistema(
                        clienteFinal.nombre + " se conectó desde "
                                + clienteFinal.direccionIp + "."
                );
                actualizarEstadoClientes();
            });

            // Todos reciben el aviso de que alguien entró al chat.
            enviarATodos(SYS + cliente.nombre + " se unió al chat.");

            String linea;

            /*
             * El hilo del cliente queda esperando mensajes mientras
             * el servidor siga activo y el socket permanezca abierto.
             */
            while (servidorActivo && (linea = entrada.readLine()) != null) {
                // Se ignoran líneas que no tengan el formato de mensaje.
                if (!linea.startsWith(MSG)) {
                    continue;
                }

                String mensaje = limpiarTexto(
                        linea.substring(MSG.length())
                );

                // Los mensajes vacíos no se distribuyen.
                if (mensaje.isBlank()) {
                    continue;
                }

                final String mensajeFinal = mensaje;

                // Se muestra también el mensaje en la ventana del servidor.
                ejecutarEnInterfaz(
                        () -> registrarMensaje(clienteFinal.nombre, mensajeFinal)
                );

                /*
                 * El servidor agrega el nombre del usuario y manda
                 * el mensaje a todas las computadoras conectadas.
                 */
                enviarATodos(
                        MSG + cliente.nombre + "\t" + mensaje
                );
            }

        } catch (SocketException error) {
            if (servidorActivo && !socket.isClosed()) {
                final String nombre = cliente == null
                        ? direccionRemota
                        : cliente.nombre;

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "La conexión con " + nombre + " se interrumpió."
                        )
                );
            }

        } catch (IOException error) {
            if (servidorActivo) {
                final String nombre = cliente == null
                        ? direccionRemota
                        : cliente.nombre;

                ejecutarEnInterfaz(
                        () -> registrarSistema(
                                "Error con " + nombre + ": " + error.getMessage()
                        )
                );
            }

        } finally {
            /*
             * Cuando el cliente sale se elimina de la lista y su socket se cierra.
             * Los demás clientes continúan funcionando normalmente.
             */
            if (cliente != null) {
                clientes.remove(cliente);
                cerrarSilenciosamente(cliente.socket);

                if (servidorActivo) {
                    String nombre = cliente.nombre;

                    ejecutarEnInterfaz(() -> {
                        registrarSistema(nombre + " se desconectó.");
                        actualizarEstadoClientes();
                    });

                    enviarATodos(SYS + nombre + " salió del chat.");
                }
            } else {
                cerrarSilenciosamente(socket);
            }
        }
    }

    /**
     * Busca un nombre disponible para evitar que dos clientes aparezcan
     * exactamente con el mismo identificador.
     *
     * @param nombreBase nombre solicitado por el cliente.
     * @return nombre disponible dentro del chat.
     */
    private String obtenerNombreDisponible(String nombreBase) {
        String base = nombreBase.trim();
        String candidato = base;
        int numero = 2;

        while (nombreEnUso(candidato)) {
            candidato = base + " " + numero;
            numero++;
        }

        return candidato;
    }

    /**
     * Revisa si un nombre ya está siendo utilizado.
     *
     * @param nombre nombre que se desea comprobar.
     * @return true si ya existe un cliente con ese nombre.
     */
    private boolean nombreEnUso(String nombre) {
        for (ClienteConectado cliente : clientes) {
            if (cliente.nombre.equalsIgnoreCase(nombre)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Envía el texto escrito en la ventana del servidor a todos los clientes.
     */
    private void enviarMensaje() {
        String mensaje = limpiarTexto(campoMensaje.getText());

        if (mensaje.isBlank()) {
            return;
        }

        if (clientes.isEmpty()) {
            mostrarError("Todavía no hay computadoras cliente conectadas.");
            return;
        }

        // Se muestra localmente el mensaje del servidor.
        registrarMensaje("Servidor", mensaje);

        // Después se envía a todos los clientes activos.
        enviarATodos(MSG + "Servidor\t" + mensaje);

        campoMensaje.setText("");
        campoMensaje.requestFocusInWindow();
    }

    /**
     * Recorre la lista de clientes y envía la misma línea a cada uno.
     *
     * @param linea información que será enviada.
     */
    private void enviarATodos(String linea) {
        for (ClienteConectado cliente : clientes) {
            cliente.salida.println(linea);
        }
    }

    /**
     * Actualiza la etiqueta de estado con el número de clientes conectados.
     */
    private void actualizarEstadoClientes() {
        int cantidad = clientes.size();

        if (!servidorActivo) {
            actualizarEstado("Estado: detenido");
            habilitarEnvio(false);
            return;
        }

        if (cantidad == 0) {
            actualizarEstado("Estado: activo - esperando clientes");
            habilitarEnvio(false);
        } else {
            actualizarEstado(
                    "Estado: activo - " + cantidad
                            + (cantidad == 1
                            ? " cliente conectado"
                            : " clientes conectados")
            );
            habilitarEnvio(true);
        }
    }

    /**
     * Detiene el servidor desde la interfaz y avisa a los clientes conectados.
     */
    private void detenerServidor() {
        servidorActivo = false;
        registrarSistema("Deteniendo servidor...");

        // Se avisa antes de cerrar las conexiones.
        enviarATodos(SYS + "El servidor cerrará la conversación.");

        cerrarRecursosServidor();
        botonServidor.setEnabled(false);
    }

    /**
     * Detiene el servidor sin agregar mensajes a la interfaz.
     * Se utiliza cuando se cierra completamente la ventana.
     */
    private void detenerServidorSilenciosamente() {
        servidorActivo = false;
        cerrarRecursosServidor();
    }

    /**
     * Cierra todos los clientes y después cierra el ServerSocket principal.
     */
    private void cerrarRecursosServidor() {
        for (ClienteConectado cliente : clientes) {
            cerrarSilenciosamente(cliente.socket);
        }

        clientes.clear();

        synchronized (bloqueoServidor) {
            if (servidorSocket != null) {
                try {
                    servidorSocket.close();
                } catch (IOException ignored) {
                    // El socket puede haberse cerrado anteriormente.
                }

                servidorSocket = null;
            }
        }
    }

    /**
     * Ajusta opciones de una conexión TCP recién aceptada.
     *
     * @param socket socket del cliente.
     * @throws SocketException si alguna propiedad no puede configurarse.
     */
    private static void configurarSocket(Socket socket) throws SocketException {
        // Mantiene activa la conexión y ayuda a detectar cortes de red.
        socket.setKeepAlive(true);

        // Evita retrasos innecesarios al enviar mensajes pequeños.
        socket.setTcpNoDelay(true);
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
                // No se necesita realizar otra acción.
            }
        }
    }

    /**
     * Limpia caracteres que podrían interferir con el formato de mensajes.
     *
     * @param texto texto original.
     * @return texto preparado para enviarse.
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
     * Convierte y valida el número de puerto escrito en la interfaz.
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
     * @param habilitado true para permitir enviar mensajes.
     */
    private void habilitarEnvio(boolean habilitado) {
        campoMensaje.setEnabled(habilitado);
        botonEnviar.setEnabled(habilitado);
    }

    /**
     * Cambia el texto mostrado en la etiqueta de estado.
     *
     * @param texto nuevo estado.
     */
    private void actualizarEstado(String texto) {
        etiquetaEstado.setText(texto);
    }

    /**
     * Agrega un mensaje de chat al área de conversación.
     *
     * @param origen usuario que envió el mensaje.
     * @param mensaje contenido del mensaje.
     */
    private void registrarMensaje(String origen, String mensaje) {
        areaConversacion.append(
                origen + ": " + mensaje + System.lineSeparator()
        );

        // La vista se mueve automáticamente al mensaje más reciente.
        areaConversacion.setCaretPosition(
                areaConversacion.getDocument().getLength()
        );
    }

    /**
     * Agrega un mensaje informativo generado por el programa.
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
     * Muestra un fallo del servidor tanto en el chat como en una ventana emergente.
     *
     * @param mensaje detalle del problema.
     */
    private void notificarFalloServidor(String mensaje) {
        ejecutarEnInterfaz(() -> {
            registrarSistema(mensaje.replace('\n', ' '));
            mostrarError(mensaje);
        });
    }

    /**
     * Muestra un cuadro de diálogo de error.
     *
     * @param mensaje información mostrada al usuario.
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
     * Ejecuta una tarea dentro del hilo gráfico de Swing.
     *
     * Los hilos de red no deben modificar directamente los componentes
     * de la interfaz, por eso las actualizaciones se mandan a este método.
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
     * Método principal de la aplicación servidor.
     *
     * @param args argumentos recibidos desde la línea de comandos.
     */
    public static void main(String[] args) {
        aplicarAparienciaDelSistema();

        // Swing crea la ventana dentro de su hilo de eventos.
        SwingUtilities.invokeLater(
                () -> new Servidor().setVisible(true)
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
            // Si no se puede aplicar, Java utiliza su apariencia predeterminada.
        }
    }

    /**
     * Clase auxiliar que reúne la información necesaria de cada cliente.
     *
     * Se guarda el socket, el flujo de salida, el nombre y la dirección IP.
     */
    private static final class ClienteConectado {

        // Socket TCP de la computadora cliente.
        private final Socket socket;

        // Flujo utilizado para enviarle mensajes.
        private final PrintWriter salida;

        // Nombre con el que aparece dentro del chat.
        private final String nombre;

        // Dirección IP desde donde se realizó la conexión.
        private final String direccionIp;

        /**
         * Guarda los datos principales de un cliente conectado.
         *
         * @param socket conexión TCP.
         * @param salida flujo para enviar texto.
         * @param nombre nombre del usuario.
         * @param direccionIp dirección IP del cliente.
         */
        private ClienteConectado(
                Socket socket,
                PrintWriter salida,
                String nombre,
                String direccionIp
        ) {
            this.socket = socket;
            this.salida = salida;
            this.nombre = nombre;
            this.direccionIp = direccionIp;
        }
    }
}
