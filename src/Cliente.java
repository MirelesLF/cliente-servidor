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
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ventana principal del cliente de mensajería hecho en Java.
 *
 * El cliente se conecta por medio de un socket TCP al puerto 5000 del servidor.
 * Desde esta ventana se puede ver quién está conectado, mandar mensajes al grupo
 * completo o abrir una conversación privada con una sola persona.
 *
 * La lista de usuarios no necesita un botón para actualizarse. El servidor manda
 * los cambios y el cliente los muestra cuando alguien entra o sale del chat.
 */
public class Cliente extends JFrame {
    /*
     * Prefijos que permiten saber qué tipo de información se está enviando.
     * Deben ser iguales a los que utiliza Servidor.java para que ambos programas
     * puedan entenderse.
     */
    private static final String HELLO = "HELLO\t";
    private static final String MSG = "MSG\t";
    private static final String PRIVATE = "PRIVATE\t";
    private static final String USERS = "USERS\t";
    private static final String WELCOME = "WELCOME\t";
    private static final String SYS = "SYS\t";
    private static final String DESTINO_TODOS = "Todos";
    private static final int PUERTO = 5000;
    private static final Color FONDO = new Color(245, 247, 251);
    private static final Color TEXTO = new Color(30, 41, 59);
    private static final Color SECUNDARIO = new Color(100, 116, 139);
    private static final Color AZUL = new Color(37, 99, 235);
    private static final Color BOTON_INACTIVO = new Color(241, 245, 249);
    private static final Color TEXTO_INACTIVO = new Color(51, 65, 85);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    // Controles que forman la ventana del cliente.
    private final JTextField campoNombre = new JTextField(nombrePredeterminado(), 10);
    private final JTextField campoIp = new JTextField("127.0.0.1", 12);
    private final JButton botonConexion = new JButton("Conectar");
    private final JLabel etiquetaUsuarios = texto("Usuarios", 12, SECUNDARIO);
    private final JLabel etiquetaConversacion = texto(DESTINO_TODOS, 20, TEXTO);
    private final JLabel etiquetaDetalle = texto("Desconectado", 12, SECUNDARIO);
    private final JLabel etiquetaAviso = texto(" ", 12, SECUNDARIO);
    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private final JList<String> listaUsuarios = new JList<>(modeloUsuarios);
    private final JTextPane areaConversacion = new JTextPane();
    private final JTextField campoMensaje = new JTextField();
    private final JButton botonEnviar = new JButton("Enviar");

    // El estado visual pertenece al hilo de Swing. La clave vacía identifica al grupo.
    private final Map<String, Conversacion> conversaciones = new LinkedHashMap<>();
    private final Set<String> usuariosConectados = new HashSet<>();
    private String conversacionActual = "";
    private String nombreActual = "";
    private boolean actualizandoLista;
    private boolean identidadConfirmada;

    /*
     * Protege los datos del socket porque el hilo de red y el hilo de Swing
     * pueden intentar consultarlos al mismo tiempo.
     */
    private final Object bloqueoConexion = new Object();
    private volatile boolean conexionActiva;
    private volatile boolean conectando;
    private long generacionConexion;
    private Socket socket;
    private PrintWriter salidaServidor;

    /**
     * Prepara la conversación general, construye la ventana y registra sus eventos.
     */
    public Cliente() {
        super("Cliente");
        conversaciones.put("", new Conversacion(DESTINO_TODOS));
        construirInterfaz();
        configurarEventos();
    }

    /**
     * Crea y acomoda los controles que se muestran en la ventana.
     */
    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(820, 540));
        setSize(1020, 660);
        setLocationRelativeTo(null);
        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setBackground(FONDO);
        contenido.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(contenido);

        JPanel conexion = new JPanel(new GridBagLayout());
        conexion.setBackground(Color.WHITE);
        conexion.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 231, 239)),
                new EmptyBorder(12, 14, 12, 14)
        ));
        agregarCampo(conexion, "Nombre", campoNombre, 0, 0.42);
        agregarCampo(conexion, "IP del servidor", campoIp, 1, 0.58);
        GridBagConstraints boton = new GridBagConstraints();
        boton.gridx = 2;
        boton.gridy = 1;
        boton.fill = GridBagConstraints.HORIZONTAL;
        boton.insets = new Insets(4, 0, 0, 0);
        botonConexion.setPreferredSize(new Dimension(130, 38));
        configurarBoton(botonConexion, AZUL);
        conexion.add(botonConexion, boton);
        contenido.add(conexion, BorderLayout.NORTH);

        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBackground(Color.WHITE);
        lateral.setMinimumSize(new Dimension(200, 0));
        lateral.setBorder(new EmptyBorder(4, 4, 4, 4));
        etiquetaUsuarios.setBorder(new EmptyBorder(18, 14, 14, 12));
        etiquetaUsuarios.setFont(etiquetaUsuarios.getFont().deriveFont(Font.BOLD));
        lateral.add(etiquetaUsuarios, BorderLayout.NORTH);
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setCellRenderer(new UsuarioRenderer());
        listaUsuarios.setFixedCellHeight(72);
        listaUsuarios.setBackground(Color.WHITE);
        listaUsuarios.getAccessibleContext().setAccessibleName("Conversaciones y usuarios conectados");
        modeloUsuarios.addElement(DESTINO_TODOS);
        listaUsuarios.setSelectedIndex(0);
        JScrollPane listaScroll = new JScrollPane(listaUsuarios);
        listaScroll.setBorder(BorderFactory.createEmptyBorder());
        listaScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        lateral.add(listaScroll, BorderLayout.CENTER);

        JPanel chat = new JPanel(new BorderLayout());
        chat.setBackground(Color.WHITE);
        chat.setMinimumSize(new Dimension(390, 0));
        chat.setBorder(new EmptyBorder(0, 6, 0, 0));
        JPanel cabecera = new JPanel(new BorderLayout(0, 5));
        cabecera.setBackground(Color.WHITE);
        cabecera.setBorder(new EmptyBorder(17, 20, 16, 20));
        etiquetaConversacion.setFont(etiquetaConversacion.getFont().deriveFont(Font.BOLD));
        cabecera.add(etiquetaConversacion, BorderLayout.NORTH);
        cabecera.add(etiquetaDetalle, BorderLayout.SOUTH);
        chat.add(cabecera, BorderLayout.NORTH);
        areaConversacion.setEditorKit(new EditorChat());
        areaConversacion.setEditable(false);
        areaConversacion.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, true);
        areaConversacion.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        areaConversacion.setBackground(Color.WHITE);
        areaConversacion.setBorder(new EmptyBorder(8, 16, 8, 16));
        areaConversacion.getAccessibleContext().setAccessibleName("Mensajes de la conversación seleccionada");
        JScrollPane desplazamiento = new JScrollPane(areaConversacion);
        desplazamiento.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(235, 239, 245)));
        desplazamiento.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chat.add(desplazamiento, BorderLayout.CENTER);

        JPanel redaccion = new JPanel(new BorderLayout(10, 8));
        redaccion.setBackground(Color.WHITE);
        redaccion.setBorder(new EmptyBorder(10, 16, 14, 16));
        redaccion.add(etiquetaAviso, BorderLayout.NORTH);
        campoMensaje.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        campoMensaje.setPreferredSize(new Dimension(100, 40));
        campoMensaje.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 235)), new EmptyBorder(8, 10, 8, 10)));
        campoMensaje.getAccessibleContext().setAccessibleName("Escribe un mensaje");
        botonEnviar.setPreferredSize(new Dimension(96, 40));
        configurarBoton(botonEnviar, AZUL);
        redaccion.add(campoMensaje, BorderLayout.CENTER);
        redaccion.add(botonEnviar, BorderLayout.EAST);
        chat.add(redaccion, BorderLayout.SOUTH);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lateral, chat);
        division.setDividerLocation(240);
        division.setDividerSize(4);
        division.setResizeWeight(0);
        division.setBorder(BorderFactory.createLineBorder(new Color(225, 231, 239)));
        contenido.add(division, BorderLayout.CENTER);
        actualizarConversacion();
        renderizarConversacion();
    }

    /**
     * Crea una etiqueta con el formato usado en la aplicación.
     *
     * @param contenido texto inicial de la etiqueta.
     * @param tamano tamaño de la letra.
     * @param color color del texto.
     * @return etiqueta lista para agregarse a la ventana.
     */
    private static JLabel texto(String contenido, int tamano, Color color) {
        JLabel etiqueta = new JLabel(contenido);
        // Los nombres recibidos son texto, nunca etiquetas HTML de Swing.
        etiqueta.putClientProperty("html.disable", true);
        etiqueta.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, tamano));
        etiqueta.setForeground(color);
        return etiqueta;
    }

    /**
     * Aplica el mismo estilo a los botones principales.
     *
     * @param boton botón que se va a modificar.
     * @param color color de fondo que tendrá el botón.
     */
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
     * Cambia la apariencia del botón de envío según el estado de la conexión.
     *
     * @param habilitado true cuando ya se permite mandar mensajes.
     */
    private void actualizarBotonEnviar(boolean habilitado) {
        botonEnviar.setEnabled(habilitado);
        botonEnviar.setBackground(habilitado ? AZUL : BOTON_INACTIVO);
        botonEnviar.setForeground(habilitado ? Color.WHITE : TEXTO_INACTIVO);
    }

    /**
     * Agrega una etiqueta y su campo de texto al panel de conexión.
     *
     * @param panel panel donde se colocará el campo.
     * @param nombre texto que explica para qué sirve.
     * @param campo control donde escribe el usuario.
     * @param columna columna que ocupará dentro del panel.
     * @param peso espacio horizontal que puede utilizar.
     */
    private static void agregarCampo(JPanel panel, String nombre, JTextField campo, int columna, double peso) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = columna;
        c.gridy = 0;
        c.weightx = peso;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 12);
        panel.add(texto(nombre, 12, SECUNDARIO), c);
        c.gridy = 1;
        c.insets = new Insets(4, 0, 0, 12);
        campo.setPreferredSize(new Dimension(90, 36));
        campo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                new EmptyBorder(7, 9, 7, 9)
        ));
        campo.getAccessibleContext().setAccessibleName(nombre);
        panel.add(campo, c);
    }

    /**
     * Relaciona los botones, la lista y el cierre de la ventana con sus acciones.
     */
    private void configurarEventos() {
        botonConexion.addActionListener(evento -> {
            if (conexionActiva || conectando) desconectar();
            else conectar();
        });
        botonEnviar.addActionListener(evento -> enviarMensaje());
        campoMensaje.addActionListener(evento -> enviarMensaje());
        listaUsuarios.addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting() && !actualizandoLista) cambiarConversacion();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                invalidarConexionActual();
                dispose();
            }
        });
    }

    /**
     * Valida el nombre y la dirección antes de comenzar un intento de conexión.
     *
     * La comunicación se inicia en otro hilo para que la ventana no se congele
     * mientras se busca al servidor.
     */
    private void conectar() {
        String nombre = limpiarTexto(campoNombre.getText());
        String ip = campoIp.getText().trim();
        if (nombre.isBlank() || ip.isBlank()) {
            mostrarError("Escribe tu nombre y la IP del servidor.");
            return;
        }
        long generacion;
        synchronized (bloqueoConexion) {
            conectando = true;
            generacion = ++generacionConexion;
        }
        identidadConfirmada = false;
        campoNombre.setEnabled(false);
        campoIp.setEnabled(false);
        botonConexion.setText("Cancelar");
        botonConexion.setBackground(new Color(71, 85, 105));
        actualizarConversacion();
        Thread hilo = new Thread(() -> ejecutarConexion(nombre, ip, generacion), "cliente-chat");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Abre el socket, envía el nombre y permanece escuchando al servidor.
     *
     * Cada intento usa un número de generación. Esto evita que un hilo anterior
     * cambie la ventana después de que el usuario ya inició otra conexión.
     *
     * @param nombre nombre solicitado por el usuario.
     * @param ip dirección IP donde se encuentra el servidor.
     * @param generacion número que identifica este intento de conexión.
     */
    private void ejecutarConexion(String nombre, String ip, long generacion) {
        Socket nuevoSocket = new Socket();
        try {
            synchronized (bloqueoConexion) {
                if (!conectando || generacion != generacionConexion) return;
                socket = nuevoSocket;
            }
            nuevoSocket.connect(new InetSocketAddress(ip, PUERTO), 5000);
            nuevoSocket.setKeepAlive(true);
            nuevoSocket.setTcpNoDelay(true);
            nuevoSocket.setSoTimeout(10000);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(
                    nuevoSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                    nuevoSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            synchronized (bloqueoConexion) {
                if (!conectando || generacion != generacionConexion) return;
                salidaServidor = salida;
                conexionActiva = true;
            }
            salida.println(HELLO + nombre);
            if (salida.checkError()) throw new IOException("No se pudo enviar el nombre al servidor.");
            String bienvenida = entrada.readLine();
            if (bienvenida == null || !bienvenida.startsWith(WELCOME)
                    || bienvenida.substring(WELCOME.length()).isBlank()) {
                throw new IOException("El servidor no confirmó tu nombre. Inicia la versión actual del servidor.");
            }
            nuevoSocket.setSoTimeout(0);
            synchronized (bloqueoConexion) {
                if (generacion != generacionConexion) return;
                conectando = false;
            }
            ejecutarEnInterfazSiActual(generacion, () -> procesarLineaRecibida(bienvenida));
            String linea;
            while (esConexionActiva(generacion) && (linea = entrada.readLine()) != null) {
                String recibida = linea;
                ejecutarEnInterfazSiActual(generacion, () -> procesarLineaRecibida(recibida));
            }
            if (esConexionActiva(generacion)) {
                ejecutarEnInterfazSiActual(generacion, () -> registrarSistema("El servidor cerró la conexión."));
            }
        } catch (SocketTimeoutException error) {
            ejecutarEnInterfazSiActual(generacion,
                    () -> registrarSistema("El servidor no respondió. Revisa la IP e inténtalo otra vez."));
        } catch (UnknownHostException error) {
            ejecutarEnInterfazSiActual(generacion,
                    () -> registrarSistema("No se encontró esa dirección. Revisa la IP del servidor."));
        } catch (SocketException error) {
            if (esSesionEnCurso(generacion)) {
                String aviso = conectando
                        ? "No se pudo conectar. Revisa la IP y que el servidor esté abierto."
                        : "Se perdió la conexión con el servidor.";
                ejecutarEnInterfazSiActual(generacion,
                        () -> registrarSistema(aviso));
            }
        } catch (IOException error) {
            if (esSesionEnCurso(generacion)) {
                ejecutarEnInterfazSiActual(generacion,
                        () -> registrarSistema("No se pudo completar la conexión. Intenta conectarte de nuevo."));
            }
        } finally {
            cerrarSilenciosamente(nuevoSocket);
            if (limpiarEstadoConexion(generacion, nuevoSocket)) {
                ejecutarEnInterfazSiActual(generacion, this::mostrarEstadoDesconectado);
            }
        }
    }

    /**
     * Interpreta una línea recibida según el prefijo del protocolo.
     *
     * @param linea información enviada por el servidor.
     */
    private void procesarLineaRecibida(String linea) {
        if (linea.startsWith(WELCOME)) {
            String solicitado = limpiarTexto(campoNombre.getText());
            nombreActual = linea.substring(WELCOME.length());
            campoNombre.setText(nombreActual);
            // El historial pertenece a esta conexión, nunca a otra identidad o servidor.
            conversaciones.clear();
            conversaciones.put("", new Conversacion(DESTINO_TODOS));
            usuariosConectados.clear();
            conversacionActual = "";
            campoMensaje.setText("");
            identidadConfirmada = true;
            botonConexion.setText("Desconectar");
            botonConexion.setBackground(new Color(71, 85, 105));
            etiquetaAviso.setText(" ");
            reconstruirListaUsuarios();
            renderizarConversacion();
            if (!solicitado.equals(nombreActual)) {
                registrarSistema("Ese nombre no estaba disponible. Entraste como " + nombreActual + ".");
            }
            campoMensaje.requestFocusInWindow();
        } else if (linea.startsWith(MSG)) {
            String contenido = linea.substring(MSG.length());
            int separador = contenido.indexOf('\t');
            if (separador >= 0) {
                registrarMensaje(contenido.substring(0, separador), contenido.substring(separador + 1));
            }
        } else if (linea.startsWith(PRIVATE)) {
            procesarMensajePrivado(linea.substring(PRIVATE.length()));
        } else if (linea.startsWith(USERS)) {
            mostrarListaUsuarios(linea.substring(USERS.length()));
        } else if (linea.startsWith(SYS)) {
            registrarSistema(linea.substring(SYS.length()));
        }
    }

    /**
     * Prepara el texto escrito como mensaje grupal o privado y lo envía.
     */
    private void enviarMensaje() {
        String mensaje = limpiarTexto(campoMensaje.getText());
        if (mensaje.isBlank()) return;
        if (!puedeEnviar()) {
            etiquetaAviso.setText(conversacionActual.isEmpty() ? "Conéctate para enviar mensajes."
                    : "Este usuario se desconectó.");
            return;
        }
        Conversacion conversacion = conversaciones.get(conversacionActual);
        String linea = conversacionActual.isEmpty() ? MSG + mensaje
                : PRIVATE + conversacion.nombre + "\t" + mensaje;
        if (enviarLinea(linea)) {
            // La copia confirmada llega del servidor; no duplicamos mensajes locales.
            campoMensaje.setText("");
            conversacion.borrador = "";
            etiquetaAviso.setText(" ");
            campoMensaje.requestFocusInWindow();
        }
    }

    /**
     * Escribe una línea completa en la conexión actual.
     *
     * @param linea contenido que entiende el servidor.
     * @return true si el flujo aceptó el envío.
     */
    private boolean enviarLinea(String linea) {
        PrintWriter salida;
        synchronized (bloqueoConexion) {
            if (!conexionActiva || salidaServidor == null) return false;
            salida = salidaServidor;
        }
        salida.println(linea);
        if (salida.checkError()) {
            invalidarConexionActual();
            registrarSistema("No se pudo enviar el mensaje. Vuelve a conectarte.");
            mostrarEstadoDesconectado();
            return false;
        }
        return true;
    }

    /**
     * Separa el origen, el destino y el texto de un mensaje privado.
     *
     * @param contenido datos recibidos después del prefijo PRIVATE.
     */
    private void procesarMensajePrivado(String contenido) {
        String[] partes = contenido.split("\t", 3);
        if (partes.length != 3) {
            registrarSistema("No se pudo mostrar un mensaje.");
            return;
        }
        String origen = partes[0];
        String destino = partes[1];
        boolean propio = origen.equalsIgnoreCase(nombreActual);
        if (!propio && !destino.equalsIgnoreCase(nombreActual)) return;
        String otro = propio ? destino : origen;
        String clave = normalizarNombre(otro);
        conversaciones.computeIfAbsent(clave, ignorada -> new Conversacion(otro));
        agregarMensaje(clave, new MensajeChat(origen, partes[2], propio, false));
        reconstruirListaUsuarios();
    }

    /**
     * Guarda la lista de usuarios que mandó el servidor.
     *
     * USERS llega automáticamente cada vez que alguien entra o sale, por eso el
     * cliente no necesita consultar la lista con un botón.
     *
     * @param contenido nombres separados por tabuladores.
     */
    private void mostrarListaUsuarios(String contenido) {
        usuariosConectados.clear();
        if (!contenido.isBlank()) {
            for (String nombre : contenido.split("\t")) {
                String clave = normalizarNombre(nombre);
                if (clave.isBlank() || nombre.equalsIgnoreCase(DESTINO_TODOS)) continue;
                usuariosConectados.add(clave);
                if (!nombre.equalsIgnoreCase(nombreActual)) {
                    conversaciones.computeIfAbsent(clave, ignorada -> new Conversacion(nombre));
                }
            }
        }
        reconstruirListaUsuarios();
    }

    /**
     * Vuelve a dibujar la opción Todos y las conversaciones individuales.
     * También conserva conversaciones anteriores aunque su usuario ya haya salido.
     */
    private void reconstruirListaUsuarios() {
        actualizandoLista = true;
        try {
            modeloUsuarios.clear();
            modeloUsuarios.addElement(DESTINO_TODOS);
            conversaciones.entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty())
                    .filter(e -> usuariosConectados.contains(e.getKey())
                            || !e.getValue().mensajes.isEmpty() || !e.getValue().borrador.isEmpty()
                            || e.getKey().equals(conversacionActual))
                    .sorted(Comparator.<Map.Entry<String, Conversacion>, Boolean>comparing(
                            e -> !usuariosConectados.contains(e.getKey()))
                            .thenComparing(e -> e.getValue().nombre, String.CASE_INSENSITIVE_ORDER))
                    .forEach(e -> modeloUsuarios.addElement(e.getValue().nombre));
            listaUsuarios.setSelectedValue(conversaciones.get(conversacionActual).nombre, true);
        } finally {
            actualizandoLista = false;
        }
        etiquetaUsuarios.setText("Usuarios · " + usuariosConectados.size());
        actualizarConversacion();
        listaUsuarios.repaint();
    }

    /**
     * Cambia entre el chat general y un usuario seleccionado de la lista.
     * El texto sin enviar se guarda como borrador de esa conversación.
     */
    private void cambiarConversacion() {
        String seleccionado = listaUsuarios.getSelectedValue();
        if (seleccionado == null) return;
        String nueva = seleccionado.equals(DESTINO_TODOS) ? "" : normalizarNombre(seleccionado);
        if (nueva.equals(conversacionActual)) return;
        conversaciones.get(conversacionActual).borrador = campoMensaje.getText();
        conversacionActual = nueva;
        Conversacion conversacion = conversaciones.get(nueva);
        conversacion.noLeidos = 0;
        campoMensaje.setText(conversacion.borrador);
        etiquetaAviso.setText(" ");
        actualizarConversacion();
        renderizarConversacion();
        listaUsuarios.repaint();
        campoMensaje.requestFocusInWindow();
    }

    /**
     * Revisa que exista conexión y que el destinatario siga disponible.
     *
     * @return true cuando el campo y el botón de envío deben estar activos.
     */
    private boolean puedeEnviar() {
        return conexionActiva && identidadConfirmada
                && (conversacionActual.isEmpty() || usuariosConectados.contains(conversacionActual));
    }

    /**
     * Actualiza el título, el estado del destinatario y los controles de envío.
     */
    private void actualizarConversacion() {
        Conversacion conversacion = conversaciones.get(conversacionActual);
        etiquetaConversacion.setText(conversacion.nombre);
        boolean grupo = conversacionActual.isEmpty();
        if (!conexionActiva || !identidadConfirmada) {
            etiquetaDetalle.setText(conectando ? "Conectando…" : "Desconectado");
        } else if (grupo) {
            int cantidad = usuariosConectados.size();
            etiquetaDetalle.setText(cantidad + (cantidad == 1 ? " participante" : " participantes"));
        } else if (usuariosConectados.contains(conversacionActual)) {
            etiquetaDetalle.setText("En línea");
        } else {
            etiquetaDetalle.setText("Desconectado");
        }
        boolean habilitado = puedeEnviar();
        campoMensaje.setEnabled(habilitado);
        actualizarBotonEnviar(habilitado);
        campoMensaje.setToolTipText(grupo ? "Mensaje para todos" : "Mensaje para " + conversacion.nombre);
    }

    /**
     * Agrega un mensaje recibido a la conversación general.
     *
     * @param origen persona que mandó el mensaje.
     * @param mensaje texto que se mostrará.
     */
    private void registrarMensaje(String origen, String mensaje) {
        agregarMensaje("", new MensajeChat(origen, mensaje, origen.equalsIgnoreCase(nombreActual), false));
    }

    /**
     * Muestra un aviso del programa dentro de la conversación general.
     *
     * @param mensaje aviso que verá el usuario.
     */
    private void registrarSistema(String mensaje) {
        etiquetaAviso.setText(mensaje);
        etiquetaAviso.setToolTipText("Aviso: " + mensaje);
        agregarMensaje("", new MensajeChat("", mensaje, false, true));
    }

    /**
     * Guarda un mensaje y aumenta el contador si pertenece a otro chat.
     *
     * @param clave identificador de la conversación.
     * @param mensaje información que se agregará al historial.
     */
    private void agregarMensaje(String clave, MensajeChat mensaje) {
        Conversacion conversacion = conversaciones.get(clave);
        conversacion.mensajes.add(mensaje);
        if (clave.equals(conversacionActual)) renderizarConversacion();
        else if (!mensaje.propio && !mensaje.sistema) conversacion.noLeidos++;
        listaUsuarios.repaint();
    }

    /**
     * Construye el contenido visual de la conversación seleccionada.
     * Los nombres y mensajes se escapan para que siempre se traten como texto y no
     * puedan agregar etiquetas HTML dentro de Swing.
     */
    private void renderizarConversacion() {
        Conversacion conversacion = conversaciones.get(conversacionActual);
        StringBuilder html = new StringBuilder(
                "<html><body style='font-family:sans-serif;font-size:14pt;color:#1e293b;'>");
        if (conversacion.mensajes.isEmpty()) {
            html.append("<div style='text-align:center;color:#64748b;padding:30px;'>")
                    .append("Todavía no hay mensajes.")
                    .append("<br><br><span style='font-size:12pt;'>")
                    .append(puedeEnviar() ? "Escribe algo para empezar." : "")
                    .append("</span></div>");
        }
        html.append("<table width='100%' cellpadding='0' cellspacing='0'>");
        for (MensajeChat mensaje : conversacion.mensajes) {
            if (mensaje.sistema) {
                html.append("<tr><td align='center' style='padding:10px;color:#64748b;font-size:12pt;'>")
                        .append(escaparHtml(mensaje.texto)).append("</td></tr>");
            } else {
                html.append("<tr><td align='").append(mensaje.propio ? "right" : "left")
                        .append("' style='padding:5px 0;'><table width='78%' cellpadding='10' cellspacing='0' bgcolor='")
                        .append(mensaje.propio ? "#e5edff" : "#f1f5f9")
                        .append("'><tr><td style='font-size:14pt;'>")
                        .append("<span style='font-size:11pt;color:#475569;'><b>")
                        .append(mensaje.propio ? "Tú" : escaparHtml(mensaje.origen))
                        .append("</b> &nbsp; ").append(mensaje.hora).append("</span><br>")
                        .append(escaparHtml(mensaje.texto)).append("</td></tr></table></td></tr>");
            }
        }
        html.append("</table></body></html>");
        areaConversacion.setText(html.toString());
        areaConversacion.setCaretPosition(areaConversacion.getDocument().getLength());
    }

    /**
     * Sustituye caracteres que tienen un significado especial en HTML.
     *
     * @param texto contenido original.
     * @return texto seguro para mostrar dentro del JTextPane.
     */
    private static String escaparHtml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Termina la conexión cuando el usuario presiona el botón Desconectar.
     */
    private void desconectar() {
        if (invalidarConexionActual()) registrarSistema("Te desconectaste del servidor.");
        mostrarEstadoDesconectado();
    }

    /**
     * Marca como terminado el intento actual y cierra su socket.
     *
     * @return true si había una conexión o un intento en proceso.
     */
    private boolean invalidarConexionActual() {
        synchronized (bloqueoConexion) {
            boolean habiaConexion = conexionActiva || conectando;
            generacionConexion++;
            conectando = false;
            conexionActiva = false;
            cerrarSilenciosamente(socket);
            socket = null;
            salidaServidor = null;
            return habiaConexion;
        }
    }

    /**
     * Limpia las referencias solamente si pertenecen a la sesión que terminó.
     *
     * @param generacion sesión que terminó.
     * @param socketTerminado socket usado por esa sesión.
     * @return true cuando se limpió el estado actual.
     */
    private boolean limpiarEstadoConexion(long generacion, Socket socketTerminado) {
        synchronized (bloqueoConexion) {
            if (generacion != generacionConexion || (socket != null && socket != socketTerminado)) return false;
            conexionActiva = false;
            conectando = false;
            socket = null;
            salidaServidor = null;
            return true;
        }
    }

    /**
     * @return true si el hilo pertenece a la conexión que continúa activa.
     */
    private boolean esConexionActiva(long generacion) {
        synchronized (bloqueoConexion) {
            return generacion == generacionConexion && conexionActiva;
        }
    }

    /**
     * @return true si la sesión indicada todavía se conecta o ya está conectada.
     */
    private boolean esSesionEnCurso(long generacion) {
        synchronized (bloqueoConexion) {
            return generacion == generacionConexion && (conectando || conexionActiva);
        }
    }

    /**
     * Manda una tarea al hilo de Swing únicamente si la sesión sigue vigente.
     *
     * @param generacion sesión que originó la tarea.
     * @param tarea cambio que se realizará en la ventana.
     */
    private void ejecutarEnInterfazSiActual(long generacion, Runnable tarea) {
        ejecutarEnInterfaz(() -> {
            synchronized (bloqueoConexion) {
                if (generacion != generacionConexion) return;
            }
            tarea.run();
        });
    }

    /**
     * Devuelve los controles visuales a su estado inicial.
     */
    private void mostrarEstadoDesconectado() {
        identidadConfirmada = false;
        campoNombre.setEnabled(true);
        campoIp.setEnabled(true);
        botonConexion.setText("Conectar");
        botonConexion.setBackground(AZUL);
        usuariosConectados.clear();
        reconstruirListaUsuarios();
    }

    /**
     * Cierra un socket sin detener el programa si ya estaba cerrado.
     *
     * @param socket conexión que se desea liberar.
     */
    private static void cerrarSilenciosamente(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // El socket ya puede estar cerrado.
            }
        }
    }

    /**
     * Quita saltos y tabuladores que podrían confundirse con el protocolo.
     *
     * @param texto texto escrito o recibido.
     * @return texto preparado para utilizarse en una línea.
     */
    private static String limpiarTexto(String texto) {
        return texto == null ? "" : texto.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * Genera una clave para comparar nombres sin importar mayúsculas.
     */
    private static String normalizarNombre(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /**
     * @return nombre de la cuenta de Windows o Usuario cuando no está disponible.
     */
    private static String nombrePredeterminado() {
        String nombre = limpiarTexto(System.getProperty("user.name", "Usuario"));
        return nombre.isBlank() ? "Usuario" : nombre;
    }

    /**
     * Muestra un error sencillo en una ventana emergente.
     */
    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Cliente", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Ejecuta una tarea de forma segura dentro del hilo gráfico de Swing.
     */
    private static void ejecutarEnInterfaz(Runnable tarea) {
        if (SwingUtilities.isEventDispatchThread()) tarea.run();
        else SwingUtilities.invokeLater(tarea);
    }

    /**
     * Inicia la aplicación cliente.
     *
     * @param args argumentos recibidos desde la consola.
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing conserva su apariencia predeterminada.
        }
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }

    /**
     * Guarda el historial, el borrador y los mensajes pendientes de un chat.
     */
    private static final class Conversacion {
        private final String nombre;
        private final List<MensajeChat> mensajes = new ArrayList<>();
        private int noLeidos;
        private String borrador = "";

        /**
         * @param nombre nombre que se mostrará en la lista de conversaciones.
         */
        private Conversacion(String nombre) {
            this.nombre = nombre;
        }
    }

    /**
     * Representa un mensaje que ya está listo para mostrarse en pantalla.
     */
    private static final class MensajeChat {
        private final String origen;
        private final String texto;
        private final boolean propio;
        private final boolean sistema;
        private final String hora = LocalTime.now().format(FORMATO_HORA);

        /**
         * Guarda quién escribió, el contenido y el tipo de mensaje.
         */
        private MensajeChat(String origen, String texto, boolean propio, boolean sistema) {
            this.origen = origen;
            this.texto = texto;
            this.propio = propio;
            this.sistema = sistema;
        }
    }

    /**
     * Permite partir enlaces o palabras largas sin ensanchar toda la conversación.
     */
    private static final class EditorChat extends HTMLEditorKit {
        private final ViewFactory fabrica = new HTMLFactory() {
            @Override
            public View create(Element elemento) {
                View vista = super.create(elemento);
                if (vista.getClass() == InlineView.class) {
                    return new InlineView(elemento) {
                        @Override
                        public float getMinimumSpan(int eje) {
                            return eje == View.X_AXIS ? 0 : super.getMinimumSpan(eje);
                        }
                    };
                }
                return vista;
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return fabrica;
        }
    }

    /**
     * Dibuja cada usuario con sus iniciales, su estado y sus mensajes sin leer.
     */
    private final class UsuarioRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel avatar = texto("", 15, AZUL);
        private final JLabel nombre = texto("", 14, TEXTO);
        private final JLabel detalle = texto("", 11, SECUNDARIO);
        private final JLabel contador = texto("", 12, AZUL);

        /**
         * Prepara los controles que forman cada elemento de la lista.
         */
        private UsuarioRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            avatar.setHorizontalAlignment(JLabel.CENTER);
            avatar.setPreferredSize(new Dimension(38, 38));
            avatar.setOpaque(true);
            nombre.setFont(nombre.getFont().deriveFont(Font.BOLD));
            JPanel descripcion = new JPanel(new BorderLayout(0, 5));
            descripcion.setOpaque(false);
            descripcion.add(nombre, BorderLayout.NORTH);
            descripcion.add(detalle, BorderLayout.SOUTH);
            add(avatar, BorderLayout.WEST);
            add(descripcion, BorderLayout.CENTER);
            add(contador, BorderLayout.EAST);
        }

        /**
         * Actualiza el elemento que Swing necesita mostrar en la lista.
         */
        @Override
        public Component getListCellRendererComponent(JList<? extends String> lista, String valor,
                int indice, boolean seleccionado, boolean foco) {
            boolean grupo = indice == 0;
            String clave = grupo ? "" : normalizarNombre(valor);
            Conversacion conversacion = conversaciones.get(clave);
            boolean conectado = usuariosConectados.contains(clave);
            nombre.setText(valor);
            nombre.setForeground(grupo || conectado ? TEXTO : SECUNDARIO);
            int fin = valor.offsetByCodePoints(0, Math.min(2, valor.codePointCount(0, valor.length())));
            avatar.setText(grupo ? "#" : valor.substring(0, fin).toUpperCase(Locale.ROOT));
            avatar.setBackground(seleccionado ? new Color(210, 226, 255) : new Color(235, 241, 251));
            setBackground(seleccionado ? new Color(232, 240, 255) : Color.WHITE);
            int pendientes = conversacion == null ? 0 : conversacion.noLeidos;
            contador.setText(pendientes > 0 ? Integer.toString(pendientes) : "");
            detalle.setText(grupo ? "Conversación general" : conectado ? "● En línea" : "Desconectado");
            detalle.setForeground(conectado && !grupo ? new Color(22, 128, 83) : SECUNDARIO);
            setToolTipText("Conversación: " + valor
                    + (pendientes > 0 ? " · " + pendientes + " sin leer" : ""));
            return this;
        }
    }
}
