import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Ventana principal del servidor de mensajería LAN.
 *
 * El servidor abre un puerto TCP y permite que varias computadoras
 * se conecten al mismo tiempo. Cada cliente conectado se atiende en
 * un hilo independiente para que todos puedan enviar mensajes sin
 * detener la comunicación de los demás.
 *
 * Los mensajes enviados por un cliente llegan primero al servidor, que
 * los distribuye al grupo completo o únicamente al usuario destinatario.
 */
public class Servidor extends JFrame {

    /**
     * Obtiene las direcciones IPv4 de la computadora para mostrarlas en la
     * ventana del servidor.
     *
     * @return direcciones encontradas separadas por comas.
     */
    private static String obtenerDireccionesComoTexto() {
        List<String> direcciones = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface interfaz = interfaces.nextElement();

                if (!interfaz.isUp()
                        || interfaz.isLoopback()
                        || interfaz.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> direccionesInterfaz =
                        interfaz.getInetAddresses();

                while (direccionesInterfaz.hasMoreElements()) {
                    InetAddress direccion = direccionesInterfaz.nextElement();

                    if (direccion instanceof Inet4Address
                            && !direccion.isLoopbackAddress()) {
                        direcciones.add(direccion.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // Si Windows no permite consultar la red, se muestra localhost.
        }

        if (direcciones.isEmpty()) {
            direcciones.add("127.0.0.1");
        }

        direcciones.sort(String::compareTo);
        return String.join(", ", direcciones);
    }

    /*
     * Prefijos sencillos que se utilizan para distinguir el tipo de
     * información que viaja por el socket.
     *
     * HELLO indica el nombre con el que se identifica un cliente.
     * MSG indica que la línea contiene un mensaje grupal.
     * PRIVATE indica que la línea contiene un mensaje privado.
     * LIST solicita la lista de usuarios conectados.
     * USERS devuelve la lista de usuarios conectados.
     * WELCOME confirma al cliente el nombre que le asignó el servidor.
     * SYS indica que la línea contiene información generada por el sistema.
     */
    private static final String HELLO = "HELLO\t";
    private static final String MSG = "MSG\t";
    private static final String PRIVATE = "PRIVATE\t";
    private static final String LIST = "LIST";
    private static final String USERS = "USERS\t";
    private static final String WELCOME = "WELCOME\t";
    private static final String SYS = "SYS\t";

    // Evita confundir la identidad de un cliente con controles o con el servidor.
    private static final String NOMBRE_SERVIDOR = "Servidor";
    private static final String DESTINO_TODOS = "Todos";
    private static final String DESTINO_GRUPAL = "Todos (grupo)";
    private static final int PUERTO = 5000;
    private static final Color FONDO = new Color(241, 245, 249);
    private static final Color TEXTO = new Color(30, 41, 59);
    private static final Color SECUNDARIO = new Color(100, 116, 139);
    private static final Color AZUL = new Color(37, 99, 235);
    private static final Color BOTON_INACTIVO = new Color(241, 245, 249);
    private static final Color TEXTO_INACTIVO = new Color(51, 65, 85);

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // Botón utilizado para iniciar o detener el servidor.
    private final JButton botonServidor = new JButton("Detener");

    // Etiqueta que muestra el estado actual del servidor.
    private final JLabel etiquetaEstado = new JLabel("Iniciando…");

    // Muestra las direcciones IPv4 detectadas por la utilidad de red.
    private final JLabel etiquetaIp = new JLabel(
            "IP del servidor: " + obtenerDireccionesComoTexto()
    );

    // Área que muestra únicamente la conversación grupal.
    private final JTextArea areaConversacion = new JTextArea();

    // Mantiene la actividad administrativa separada del chat general.
    private final JTextArea areaActividad = new JTextArea();

    // Usuarios que ya terminaron de conectarse.
    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private final JList<String> listaUsuarios = new JList<>(modeloUsuarios);
    private final JLabel etiquetaCantidadUsuarios = new JLabel("0 conectados");

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
     * Hace atómica la elección y el registro de nombres. Sin este bloqueo,
     * dos conexiones simultáneas podrían recibir el mismo identificador.
     */
    private final Object bloqueoClientes = new Object();

    /*
     * Los nombres no se reutilizan durante una misma ejecución del servidor.
     * Así un selector desactualizado no puede entregar un privado a otra persona.
     */
    private final Set<String> nombresUtilizados = new HashSet<>();

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

    /*
     * Cada cambio de usuarios recibe un número. Así una lista anterior no
     * puede aparecer después de una lista más reciente.
     */
    private long versionUsuarios;

    // La ventana aplica la misma proteccion ante tareas tardias del EDT.
    private long ultimaVersionUsuariosMostrada = -1;

    // Socket principal que permanece escuchando nuevas conexiones.
    private ServerSocket servidorSocket;

    /**
     * Constructor de la ventana del servidor.
     *
     * Primero se crea la interfaz y después se preparan los eventos
     * de los botones y del cierre de la ventana.
     */
    public Servidor() {
        super("Servidor");
        construirInterfaz();
        configurarEventos();
    }

    /**
     * Crea y acomoda los elementos visuales de la aplicación.
     */
    private void construirInterfaz() {
        // El cierre se controla manualmente para poder cerrar los sockets antes de salir.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        setMinimumSize(new Dimension(820, 540));
        setSize(1000, 650);

        // La ventana se abre al centro de la pantalla.
        setLocationRelativeTo(null);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setBackground(FONDO);
        contenido.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(contenido);

        JPanel panelSuperior = new JPanel(new BorderLayout(12, 0));
        panelSuperior.setBackground(Color.WHITE);
        panelSuperior.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 231, 239)),
                new EmptyBorder(14, 16, 14, 16)
        ));

        etiquetaIp.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        etiquetaIp.setForeground(TEXTO);
        panelSuperior.add(etiquetaIp, BorderLayout.CENTER);

        JPanel estado = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        estado.setOpaque(false);
        etiquetaEstado.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        actualizarEstado("Iniciando…");
        configurarBoton(botonServidor, new Color(71, 85, 105));
        estado.add(etiquetaEstado);
        estado.add(botonServidor);
        panelSuperior.add(estado, BorderLayout.EAST);
        contenido.add(panelSuperior, BorderLayout.NORTH);

        configurarAreaLectura(areaConversacion, Font.SANS_SERIF);
        configurarAreaLectura(areaActividad, Font.MONOSPACED);

        JScrollPane desplazamientoChat = new JScrollPane(areaConversacion);
        JScrollPane desplazamientoActividad = new JScrollPane(areaActividad);
        desplazamientoChat.setBorder(BorderFactory.createEmptyBorder());
        desplazamientoActividad.setBorder(BorderFactory.createEmptyBorder());

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        pestanas.setBackground(Color.WHITE);
        pestanas.addTab("Chat general", desplazamientoChat);
        pestanas.addTab("Actividad", desplazamientoActividad);

        listaUsuarios.setFocusable(false);
        listaUsuarios.setCellRenderer(new UsuarioServidorRenderer());
        listaUsuarios.setFixedCellHeight(62);
        listaUsuarios.setBackground(Color.WHITE);

        JPanel panelUsuarios = new JPanel(new BorderLayout(6, 6));
        panelUsuarios.setBackground(Color.WHITE);
        panelUsuarios.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 231, 239)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        panelUsuarios.setPreferredSize(new Dimension(270, 0));
        JLabel tituloUsuarios = new JLabel("Usuarios conectados");
        tituloUsuarios.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        tituloUsuarios.setForeground(TEXTO);
        JPanel cabeceraUsuarios = new JPanel(new BorderLayout());
        cabeceraUsuarios.setOpaque(false);
        cabeceraUsuarios.setBorder(new EmptyBorder(0, 2, 8, 2));
        cabeceraUsuarios.add(tituloUsuarios, BorderLayout.NORTH);
        etiquetaCantidadUsuarios.setForeground(SECUNDARIO);
        cabeceraUsuarios.add(etiquetaCantidadUsuarios, BorderLayout.SOUTH);
        panelUsuarios.add(cabeceraUsuarios, BorderLayout.NORTH);
        JScrollPane desplazamientoUsuarios = new JScrollPane(listaUsuarios);
        desplazamientoUsuarios.setBorder(BorderFactory.createEmptyBorder());
        panelUsuarios.add(desplazamientoUsuarios, BorderLayout.CENTER);

        JSplitPane divisionPrincipal = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                panelUsuarios,
                pestanas
        );
        divisionPrincipal.setResizeWeight(0.27);
        divisionPrincipal.setDividerLocation(270);
        divisionPrincipal.setDividerSize(5);
        divisionPrincipal.setContinuousLayout(true);
        divisionPrincipal.setBorder(BorderFactory.createLineBorder(new Color(225, 231, 239)));
        contenido.add(divisionPrincipal, BorderLayout.CENTER);

        JPanel panelMensaje = new JPanel(new BorderLayout(10, 8));
        panelMensaje.setBackground(Color.WHITE);
        panelMensaje.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 231, 239)),
                new EmptyBorder(10, 14, 12, 14)
        ));
        JLabel tituloMensaje = new JLabel("Mensaje para todos");
        tituloMensaje.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        tituloMensaje.setForeground(TEXTO);
        panelMensaje.add(tituloMensaje, BorderLayout.NORTH);
        campoMensaje.setPreferredSize(new Dimension(100, 40));
        campoMensaje.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        campoMensaje.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        configurarBoton(botonEnviar, AZUL);
        panelMensaje.add(campoMensaje, BorderLayout.CENTER);
        panelMensaje.add(botonEnviar, BorderLayout.EAST);
        contenido.add(panelMensaje, BorderLayout.SOUTH);

        // No se permite enviar hasta que exista por lo menos un cliente conectado.
        habilitarEnvio(false);
    }

    /**
     * Aplica el mismo formato de lectura a los dos registros del servidor.
     */
    private static void configurarAreaLectura(JTextArea area, String familiaFuente) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(familiaFuente, Font.PLAIN, 14));
        area.setForeground(TEXTO);
        area.setBackground(Color.WHITE);
        area.setBorder(new EmptyBorder(14, 14, 14, 14));
    }

    private static void configurarBoton(JButton boton, Color color) {
        boton.setBackground(color);
        boton.setForeground(Color.WHITE);
        boton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        boton.setFocusPainted(false);
        boton.setOpaque(true);
        boton.setContentAreaFilled(true);
        boton.setBorderPainted(false);
        boton.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
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

    private void iniciarServidor() {
        if (servidorActivo) {
            return;
        }

        servidorActivo = true;
        botonServidor.setText("Detener");
        botonServidor.setEnabled(false);
        actualizarEstado("Iniciando…");

        /*
         * La espera de conexiones se realiza en otro hilo.
         * Esto evita que la interfaz gráfica se congele mientras accept()
         * permanece esperando a que alguna computadora se conecte.
         */
        Thread hiloServidor = new Thread(
                this::ejecutarServidor,
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
     */
    private void ejecutarServidor() {
        try {
            // Se crea el ServerSocket sin asociarlo todavía a un puerto.
            ServerSocket nuevoServidor = new ServerSocket();

            // Permite volver a utilizar el puerto después de reiniciar el servidor.
            nuevoServidor.setReuseAddress(true);

            // Se asocia el socket al puerto seleccionado.
            nuevoServidor.bind(new InetSocketAddress(PUERTO));

            synchronized (bloqueoServidor) {
                servidorSocket = nuevoServidor;
            }

            // Las modificaciones visuales se mandan al hilo de Swing.
            ejecutarEnInterfaz(() -> {
                botonServidor.setEnabled(true);
                actualizarEstadoClientes();
                registrarSistema("Servidor iniciado en el puerto " + PUERTO + ".");
                registrarSistema(
                        "Las computadoras pueden conectarse usando una de estas IP: "
                                + obtenerDireccionesComoTexto()
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
                        "El servidor dejó de funcionar. Intenta iniciarlo de nuevo."
                );
            }

        } catch (IOException error) {
            if (servidorActivo) {
                notificarFalloServidor(
                        "No se pudo iniciar el servidor. "
                                + "Cierra cualquier otro servidor abierto e inténtalo de nuevo."
                );
            }

        } finally {
            // Siempre se liberan los recursos al terminar el hilo principal del servidor.
            servidorActivo = false;
            cerrarRecursosServidor();

            ejecutarEnInterfaz(() -> {
                botonServidor.setText("Iniciar");
                botonServidor.setEnabled(true);
                habilitarEnvio(false);
                actualizarEstado("Detenido");
            });
        }
    }

    /**
     * Atiende a una computadora cliente durante toda su conexión.
     *
     * Primero recibe el nombre del usuario y después permanece leyendo
     * solicitudes. Los mensajes se enrutan al grupo o a un destinatario.
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

            /*
             * La asignación del nombre y el alta se hacen como una sola operación
             * para conservar identificadores únicos aun si llegan varios clientes.
             */
            synchronized (bloqueoClientes) {
                if (!servidorActivo) {
                    return;
                }

                String nombre = obtenerNombreDisponible(
                        nombreSolicitado.isBlank() ? "Usuario" : nombreSolicitado
                );

                cliente = new ClienteConectado(
                        socket,
                        salida,
                        nombre,
                        direccionRemota
                );

                clientes.add(cliente);
                nombresUtilizados.add(normalizarNombre(nombre));
            }

            final ClienteConectado clienteFinal = cliente;

            /*
             * Mientras activo sea false, el cliente reserva su nombre pero no
             * participa en envíos. Así WELCOME siempre es la primera respuesta.
             */
            if (!enviarACliente(cliente, WELCOME + cliente.nombre)) {
                return;
            }

            InstantaneaUsuarios instantaneaAlta;

            synchronized (bloqueoClientes) {
                if (!servidorActivo) {
                    return;
                }

                cliente.activo = true;
                versionUsuarios++;
                instantaneaAlta = crearInstantaneaUsuariosBloqueada();
            }

            /*
             * WELCOME ya fue enviado, por lo que ahora se puede compartir la
             * primera lista de usuarios.
             */
            publicarInstantaneaUsuarios(instantaneaAlta);

            ejecutarEnInterfaz(() -> {
                actualizarVistaUsuarios(instantaneaAlta);
                registrarSistema(
                        clienteFinal.nombre + " se conectó desde "
                                + clienteFinal.direccionIp + "."
                );
            });

            // Todos reciben el aviso de que alguien entró al chat.
            enviarATodos(SYS + cliente.nombre + " se unió al chat.");

            String linea;

            /*
             * El hilo del cliente queda esperando mensajes mientras
             * el servidor siga activo y el socket permanezca abierto.
             */
            while (servidorActivo && (linea = entrada.readLine()) != null) {
                if (linea.equals(LIST)) {
                    enviarListaUsuarios(clienteFinal);
                    ejecutarEnInterfaz(
                            () -> registrarSistema(
                                    "Lista de usuarios solicitada por "
                                            + clienteFinal.nombre + "."
                            )
                    );
                    continue;
                }

                if (linea.startsWith(PRIVATE)) {
                    procesarSolicitudPrivada(
                            clienteFinal,
                            linea.substring(PRIVATE.length())
                    );
                    continue;
                }

                if (linea.startsWith(MSG)) {
                    procesarMensajeGrupal(
                            clienteFinal,
                            linea.substring(MSG.length())
                    );
                    continue;
                }

                enviarACliente(
                        clienteFinal,
                        SYS + "No se pudo procesar la solicitud."
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
                                "Se perdió la conexión con " + nombre + "."
                        )
                );
            }

        } finally {
            /*
             * Cuando el cliente sale se elimina de la lista y su socket se cierra.
             * Los demás clientes continúan funcionando normalmente.
             */
            if (cliente != null) {
                boolean estabaActivo;
                boolean perteneciaALaSesion;
                InstantaneaUsuarios instantaneaBaja = null;

                synchronized (bloqueoClientes) {
                    estabaActivo = cliente.activo;
                    cliente.activo = false;
                    perteneciaALaSesion = clientes.remove(cliente);

                    if (!estabaActivo && perteneciaALaSesion) {
                        nombresUtilizados.remove(normalizarNombre(cliente.nombre));
                    } else if (estabaActivo && perteneciaALaSesion) {
                        versionUsuarios++;
                        instantaneaBaja = crearInstantaneaUsuariosBloqueada();
                    }
                }

                cerrarSilenciosamente(cliente.socket);

                if (servidorActivo && estabaActivo && perteneciaALaSesion) {
                    String nombre = cliente.nombre;
                    InstantaneaUsuarios listaActual = instantaneaBaja;

                    publicarInstantaneaUsuarios(listaActual);

                    ejecutarEnInterfaz(() -> {
                        actualizarVistaUsuarios(listaActual);
                        registrarSistema(nombre + " se desconectó.");
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
     * Revisa si un nombre está reservado o ya se utilizó en esta sesión.
     *
     * @param nombre nombre que se desea comprobar.
     * @return true si el identificador no debe volver a asignarse.
     */
    private boolean nombreEnUso(String nombre) {
        return nombre.equalsIgnoreCase(NOMBRE_SERVIDOR)
                || nombre.equalsIgnoreCase(DESTINO_TODOS)
                || nombre.equalsIgnoreCase(DESTINO_GRUPAL)
                || nombresUtilizados.contains(normalizarNombre(nombre));
    }

    /**
     * Genera la clave estable usada para comparar nombres sin mayúsculas.
     */
    private static String normalizarNombre(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /**
     * Procesa y distribuye un mensaje para toda la conversación.
     * El contenido siempre se trata como texto. Las demás solicitudes llegan
     * por separado.
     *
     * @param remitente cliente que envió la línea.
     * @param contenido texto del mensaje.
     */
    private void procesarMensajeGrupal(
            ClienteConectado remitente,
            String contenido
    ) {
        String mensaje = limpiarTexto(contenido);

        if (mensaje.isBlank()) {
            return;
        }

        ejecutarEnInterfaz(
                () -> registrarMensaje(remitente.nombre, mensaje)
        );

        enviarATodos(MSG + remitente.nombre + "\t" + mensaje);
    }

    /**
     * Separa el destinatario y el texto recibidos con el prefijo PRIVATE.
     *
     * @param remitente cliente que solicita el envío.
     * @param contenido destinatario y mensaje separados por tabulador.
     */
    private void procesarSolicitudPrivada(
            ClienteConectado remitente,
            String contenido
    ) {
        int separador = contenido.indexOf('\t');

        if (separador < 0) {
            enviarACliente(
                    remitente,
                    SYS + "No se pudo enviar el mensaje directo: "
                            + "faltan destinatario o mensaje."
            );
            return;
        }

        String destinatario = limpiarTexto(contenido.substring(0, separador));
        String mensaje = limpiarTexto(contenido.substring(separador + 1));

        if (destinatario.isBlank() || mensaje.isBlank()) {
            enviarACliente(
                    remitente,
                    SYS + "No se pudo enviar el mensaje directo: "
                            + "faltan destinatario o mensaje."
            );
            return;
        }

        enviarMensajePrivado(remitente, destinatario, mensaje);
    }

    /**
     * Envía un mensaje exclusivamente al remitente y al destinatario indicado.
     *
     * @param remitente usuario que escribe el mensaje.
     * @param nombreDestinatario nombre solicitado sin distinguir mayúsculas.
     * @param mensaje contenido privado.
     */
    private void enviarMensajePrivado(
            ClienteConectado remitente,
            String nombreDestinatario,
            String mensaje
    ) {
        ClienteConectado destinatario = buscarCliente(nombreDestinatario);

        if (destinatario == null) {
            enviarACliente(
                    remitente,
                    SYS + "El usuario \"" + nombreDestinatario
                            + "\" no está conectado. Consulta la lista de usuarios."
            );
            return;
        }

        String linea = PRIVATE + remitente.nombre + "\t"
                + destinatario.nombre + "\t" + mensaje;

        if (destinatario == remitente) {
            if (!enviarACliente(remitente, linea)) {
                return;
            }
        } else {
            if (!enviarACliente(destinatario, linea)) {
                enviarACliente(
                        remitente,
                        SYS + "No se pudo entregar el mensaje a \""
                                + destinatario.nombre + "\"."
                );
                return;
            }

            enviarACliente(remitente, linea);
        }

        ejecutarEnInterfaz(
                () -> registrarMensajePrivado(
                        remitente.nombre,
                        destinatario.nombre
                )
        );
    }

    /**
     * Busca un cliente por su nombre visible.
     *
     * @param nombre identificador solicitado.
     * @return cliente encontrado o null si ya no está conectado.
     */
    private ClienteConectado buscarCliente(String nombre) {
        for (ClienteConectado cliente : clientes) {
            if (cliente.activo && cliente.nombre.equalsIgnoreCase(nombre)) {
                return cliente;
            }
        }

        return null;
    }

    /**
     * Envía al cliente que la solicitó la lista actual de usuarios.
     *
     * @param cliente receptor de la lista.
     */
    private void enviarListaUsuarios(ClienteConectado cliente) {
        enviarInstantaneaUsuarios(cliente, obtenerInstantaneaUsuarios());
    }

    /**
     * Captura de forma atómica la versión y los usuarios activos actuales.
     */
    private InstantaneaUsuarios obtenerInstantaneaUsuarios() {
        synchronized (bloqueoClientes) {
            return crearInstantaneaUsuariosBloqueada();
        }
    }

    /**
     * Prepara la lista mientras no hay otro cambio de usuarios.
     */
    private InstantaneaUsuarios crearInstantaneaUsuariosBloqueada() {
        List<ClienteConectado> destinatarios = clientes.stream()
                .filter(cliente -> cliente.activo)
                .collect(Collectors.toList());

        List<String> nombres = destinatarios.stream()
                .map(cliente -> cliente.nombre)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        List<String> detalles = destinatarios.stream()
                .sorted((primero, segundo) -> String.CASE_INSENSITIVE_ORDER.compare(
                        primero.nombre,
                        segundo.nombre
                ))
                .map(cliente -> cliente.nombre + "  —  " + cliente.direccionIp)
                .collect(Collectors.toList());

        String linea = USERS + String.join("\t", nombres);

        return new InstantaneaUsuarios(
                versionUsuarios,
                linea,
                List.copyOf(nombres),
                List.copyOf(detalles),
                List.copyOf(destinatarios)
        );
    }

    /**
     * Envía la misma lista a quienes estaban conectados cuando se creó.
     */
    private void publicarInstantaneaUsuarios(InstantaneaUsuarios instantanea) {
        for (ClienteConectado cliente : instantanea.destinatarios) {
            enviarInstantaneaUsuarios(cliente, instantanea);
        }
    }

    /**
     * Evita enviar una lista anterior si el cliente ya recibió una más nueva.
     */
    private boolean enviarInstantaneaUsuarios(
            ClienteConectado cliente,
            InstantaneaUsuarios instantanea
    ) {
        synchronized (cliente.salida) {
            if (!cliente.activo
                    || instantanea.version < cliente.ultimaVersionUsuariosEnviada) {
                return true;
            }

            cliente.salida.println(instantanea.linea);

            if (cliente.salida.checkError()) {
                cerrarSilenciosamente(cliente.socket);
                return false;
            }

            cliente.ultimaVersionUsuariosEnviada = instantanea.version;
            return true;
        }
    }

    /**
     * Envía el texto escrito en la ventana del servidor a todos los clientes.
     */
    private void enviarMensaje() {
        String mensaje = limpiarTexto(campoMensaje.getText());

        if (mensaje.isBlank()) {
            return;
        }

        if (clientes.stream().noneMatch(cliente -> cliente.activo)) {
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
            if (cliente.activo) {
                enviarACliente(cliente, linea);
            }
        }
    }

    /**
     * Envía una sola línea y cierra la conexión si el flujo reporta un error.
     * La escritura y su comprobación se serializan por cliente para evitar que
     * dos hilos intercalen respuestas dirigidas al mismo socket.
     *
     * @return true cuando el flujo aceptó la línea.
     */
    private boolean enviarACliente(ClienteConectado cliente, String linea) {
        synchronized (cliente.salida) {
            cliente.salida.println(linea);

            if (cliente.salida.checkError()) {
                cerrarSilenciosamente(cliente.socket);
                return false;
            }
        }

        return true;
    }

    /**
     * Actualiza la etiqueta de estado con el número de clientes conectados.
     */
    private void actualizarEstadoClientes() {
        actualizarVistaUsuarios(obtenerInstantaneaUsuarios());
    }

    /**
     * Actualiza la lista, el contador, el estado y los controles.
     */
    private void actualizarVistaUsuarios(InstantaneaUsuarios instantanea) {
        if (instantanea.version < ultimaVersionUsuariosMostrada) {
            return;
        }

        ultimaVersionUsuariosMostrada = instantanea.version;
        modeloUsuarios.clear();

        for (String detalle : instantanea.detalles) {
            modeloUsuarios.addElement(detalle);
        }

        int cantidad = instantanea.nombres.size();
        etiquetaCantidadUsuarios.setText(
                cantidad + (cantidad == 1 ? " conectado" : " conectados")
        );

        if (!servidorActivo) {
            actualizarEstado("Detenido");
            habilitarEnvio(false);
            return;
        }

        actualizarEstado("En línea");
        habilitarEnvio(cantidad > 0);
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
        List<ClienteConectado> clientesACerrar;
        InstantaneaUsuarios instantaneaVacia;

        synchronized (bloqueoClientes) {
            clientesACerrar = List.copyOf(clientes);

            for (ClienteConectado cliente : clientesACerrar) {
                cliente.activo = false;
            }

            clientes.clear();
            nombresUtilizados.clear();
            versionUsuarios++;
            instantaneaVacia = crearInstantaneaUsuariosBloqueada();
        }

        ejecutarEnInterfaz(() -> actualizarVistaUsuarios(instantaneaVacia));

        for (ClienteConectado cliente : clientesACerrar) {
            cerrarSilenciosamente(cliente.socket);
        }

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
     * Activa o desactiva los controles para enviar mensajes.
     *
     * @param habilitado true para permitir enviar mensajes.
     */
    private void habilitarEnvio(boolean habilitado) {
        campoMensaje.setEnabled(habilitado);
        botonEnviar.setEnabled(habilitado);
        botonEnviar.setBackground(habilitado ? AZUL : BOTON_INACTIVO);
        botonEnviar.setForeground(habilitado ? Color.WHITE : TEXTO_INACTIVO);
    }

    /**
     * Cambia el texto mostrado en la etiqueta de estado.
     *
     * @param texto nuevo estado.
     */
    private void actualizarEstado(String texto) {
        etiquetaEstado.setText("● " + texto);

        if (texto.equals("En línea")) {
            etiquetaEstado.setForeground(new Color(22, 128, 83));
        } else if (texto.startsWith("Iniciando")) {
            etiquetaEstado.setForeground(new Color(217, 119, 6));
        } else {
            etiquetaEstado.setForeground(SECUNDARIO);
        }
    }

    /**
     * Agrega un mensaje de chat al área de conversación.
     *
     * @param origen usuario que envió el mensaje.
     * @param mensaje contenido del mensaje.
     */
    private void registrarMensaje(String origen, String mensaje) {
        areaConversacion.append(
                "[" + LocalTime.now().format(FORMATO_HORA) + "] "
                        + origen + ": " + mensaje + System.lineSeparator()
        );

        // La vista se mueve automáticamente al mensaje más reciente.
        areaConversacion.setCaretPosition(
                areaConversacion.getDocument().getLength()
        );
    }

    /**
     * Registra en el servidor un privado sin compartirlo con otros clientes.
     */
    private void registrarMensajePrivado(
            String origen,
            String destino
    ) {
        areaActividad.append(
                "[" + LocalTime.now().format(FORMATO_HORA) + "] "
                        + "Mensaje directo entregado: " + origen
                        + " \u2192 " + destino + "." + System.lineSeparator()
        );

        areaActividad.setCaretPosition(
                areaActividad.getDocument().getLength()
        );
    }

    /**
     * Agrega un mensaje informativo generado por el programa.
     *
     * @param mensaje información que se mostrará.
     */
    private void registrarSistema(String mensaje) {
        areaActividad.append(
                "[" + LocalTime.now().format(FORMATO_HORA) + "] "
                        + mensaje + System.lineSeparator()
        );

        areaActividad.setCaretPosition(
                areaActividad.getDocument().getLength()
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
                "Servidor",
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
        SwingUtilities.invokeLater(() -> {
            Servidor ventana = new Servidor();
            ventana.setVisible(true);
            ventana.iniciarServidor();
        });
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

    private static final class UsuarioServidorRenderer extends JPanel
            implements ListCellRenderer<String> {

        private final JLabel avatar = new JLabel();
        private final JLabel nombre = new JLabel();
        private final JLabel direccion = new JLabel();

        private UsuarioServidorRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));
            avatar.setHorizontalAlignment(JLabel.CENTER);
            avatar.setPreferredSize(new Dimension(40, 40));
            avatar.setOpaque(true);
            avatar.setForeground(AZUL);
            avatar.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            nombre.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            nombre.setForeground(TEXTO);
            direccion.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            direccion.setForeground(SECUNDARIO);
            JPanel datos = new JPanel(new BorderLayout(0, 3));
            datos.setOpaque(false);
            datos.add(nombre, BorderLayout.NORTH);
            datos.add(direccion, BorderLayout.SOUTH);
            add(avatar, BorderLayout.WEST);
            add(datos, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends String> lista,
                String valor,
                int indice,
                boolean seleccionado,
                boolean foco
        ) {
            String[] partes = valor.split(" — ", 2);
            String usuario = partes[0];
            nombre.setText(usuario);
            direccion.setText(partes.length > 1 ? partes[1] : "");
            int letras = Math.min(2, usuario.codePointCount(0, usuario.length()));
            int fin = usuario.offsetByCodePoints(0, letras);
            avatar.setText(usuario.substring(0, fin).toUpperCase(Locale.ROOT));
            avatar.setBackground(seleccionado
                    ? new Color(210, 226, 255)
                    : new Color(235, 241, 251));
            setBackground(seleccionado
                    ? new Color(232, 240, 255)
                    : Color.WHITE);
            return this;
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

        // Solo se activa después de confirmar su nombre mediante WELCOME.
        private volatile boolean activo;

        // Se consulta y modifica unicamente bajo el bloqueo del flujo de salida.
        private long ultimaVersionUsuariosEnviada = -1;

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

    /**
     * Guarda una lista de usuarios y el número de cambio que le corresponde.
     */
    private static final class InstantaneaUsuarios {

        private final long version;
        private final String linea;
        private final List<String> nombres;
        private final List<String> detalles;
        private final List<ClienteConectado> destinatarios;

        private InstantaneaUsuarios(
                long version,
                String linea,
                List<String> nombres,
                List<String> detalles,
                List<ClienteConectado> destinatarios
        ) {
            this.version = version;
            this.linea = linea;
            this.nombres = nombres;
            this.detalles = detalles;
            this.destinatarios = destinatarios;
        }
    }
}
