#:property TargetFramework=net10.0-windows
#:property UseWindowsForms=true
#:property OutputType=WinExe
#:property PublishAot=false

using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

ApplicationConfiguration.Initialize();
Application.Run(new ClienteCSharp());


/// <summary>
/// Ventana principal del cliente de mensajería hecho en C#.
///
/// Se conecta mediante TCP al puerto 5000 del servidor Java. Desde la ventana se
/// puede mandar un mensaje a todos o seleccionar a una persona para hablar en
/// privado. La lista de usuarios se actualiza con la información del servidor.
/// </summary>
internal sealed class ClienteCSharp : Form
{
    // El puerto y el nombre de la conversación general son iguales en los clientes.
    private const int Puerto = 5000;
    private const string DestinoTodos = "Todos";

    private static readonly Color Fondo = Color.FromArgb(245, 247, 251);
    private static readonly Color Blanco = Color.White;
    private static readonly Color Texto = Color.FromArgb(30, 41, 59);
    private static readonly Color Secundario = Color.FromArgb(100, 116, 139);
    private static readonly Color Azul = Color.FromArgb(37, 99, 235);
    private static readonly Color AzulSuave = Color.FromArgb(232, 240, 255);
    private static readonly Color Verde = Color.FromArgb(22, 128, 83);
    private static readonly Color Borde = Color.FromArgb(217, 225, 236);
    private static readonly Color BotonInactivo = Color.FromArgb(241, 245, 249);
    private static readonly Color TextoInactivo = Color.FromArgb(51, 65, 85);

    /*
     * Los bloqueos evitan que dos tareas utilicen la conexión o el flujo de salida
     * al mismo tiempo.
     */
    private readonly object bloqueoConexion = new();
    private readonly SemaphoreSlim bloqueoEnvio = new(1, 1);
    private readonly Dictionary<string, string> usuarios =
        new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, Conversacion> conversaciones =
        new(StringComparer.OrdinalIgnoreCase)
        {
            [""] = new Conversacion(DestinoTodos)
        };

    // Controles que forman la interfaz principal.
    private TextBox campoNombre = null!;
    private TextBox campoIp = null!;
    private TextBox campoMensaje = null!;
    private Button botonConexion = null!;
    private Button botonEnviar = null!;
    private Label etiquetaUsuarios = null!;
    private Label etiquetaConversacion = null!;
    private Label etiquetaDetalle = null!;
    private Label etiquetaAviso = null!;
    private ListBox listaUsuarios = null!;
    private RichTextBox areaMensajes = null!;

    // Datos de la conexión actual con el servidor.
    private TcpClient? conexion;
    private StreamReader? entrada;
    private StreamWriter? salida;
    private CancellationTokenSource? cancelacion;
    private int generacion;
    private bool conectando;
    private bool conectado;
    private bool identidadConfirmada;
    private bool actualizandoLista;
    private string nombreActual = "";
    private string conversacionActual = "";

    /// <summary>
    /// Configura la ventana, crea sus controles y prepara el evento de cierre.
    /// </summary>
    internal ClienteCSharp()
    {
        Text = "Cliente C#";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(820, 540);
        ClientSize = new Size(1000, 650);
        BackColor = Fondo;
        Font = new Font("Segoe UI", 10F);
        AutoScaleMode = AutoScaleMode.Dpi;

        CrearInterfaz();
        ReconstruirLista();
        FormClosing += (_, _) => Desconectar(false);
    }

    /// <summary>
    /// Crea y acomoda todos los elementos visibles del cliente.
    /// </summary>
    private void CrearInterfaz()
    {
        var raiz = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Fondo,
            Padding = new Padding(14),
            ColumnCount = 1,
            RowCount = 2
        };
        raiz.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
        raiz.RowStyles.Add(new RowStyle(SizeType.Absolute, 76F));
        raiz.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        Controls.Add(raiz);

        var conexionPanel = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Blanco,
            Padding = new Padding(14, 8, 14, 9),
            ColumnCount = 3,
            RowCount = 2,
            Margin = new Padding(0, 0, 0, 10),
            CellBorderStyle = TableLayoutPanelCellBorderStyle.None
        };
        conexionPanel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 40F));
        conexionPanel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 60F));
        conexionPanel.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 135F));
        conexionPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 20F));
        conexionPanel.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        conexionPanel.Paint += (_, evento) => DibujarBorde(evento.Graphics, conexionPanel);
        raiz.Controls.Add(conexionPanel, 0, 0);

        conexionPanel.Controls.Add(CrearEtiqueta("Nombre", Secundario, 9F), 0, 0);
        conexionPanel.Controls.Add(CrearEtiqueta("IP del servidor", Secundario, 9F), 1, 0);

        campoNombre = CrearCampo(Environment.UserName);
        campoNombre.Margin = new Padding(0, 2, 12, 0);
        conexionPanel.Controls.Add(campoNombre, 0, 1);

        campoIp = CrearCampo("127.0.0.1");
        campoIp.Margin = new Padding(0, 2, 12, 0);
        conexionPanel.Controls.Add(campoIp, 1, 1);

        botonConexion = CrearBoton("Conectar", Azul);
        botonConexion.Dock = DockStyle.Fill;
        botonConexion.Margin = new Padding(0, 0, 0, 1);
        conexionPanel.Controls.Add(botonConexion, 2, 0);
        conexionPanel.SetRowSpan(botonConexion, 2);
        botonConexion.Click += async (_, _) => await CambiarConexionAsync();
        campoNombre.KeyDown += ConectarConEnter;
        campoIp.KeyDown += ConectarConEnter;

        var division = new SplitContainer
        {
            Dock = DockStyle.Fill,
            Orientation = Orientation.Vertical,
            SplitterDistance = 235,
            SplitterWidth = 8,
            BackColor = Fondo,
            BorderStyle = BorderStyle.None,
            Margin = new Padding(0)
        };
        raiz.Controls.Add(division, 0, 1);

        var lateral = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Blanco,
            BorderStyle = BorderStyle.FixedSingle
        };
        division.Panel1.Controls.Add(lateral);

        etiquetaUsuarios = CrearEtiqueta("Usuarios · 0", Secundario, 10F, true);
        etiquetaUsuarios.Dock = DockStyle.Top;
        etiquetaUsuarios.Height = 48;
        etiquetaUsuarios.Padding = new Padding(13, 0, 0, 0);
        etiquetaUsuarios.TextAlign = ContentAlignment.MiddleLeft;
        listaUsuarios = new ListBox
        {
            Dock = DockStyle.Fill,
            BorderStyle = BorderStyle.None,
            BackColor = Blanco,
            ForeColor = Texto,
            DrawMode = DrawMode.OwnerDrawFixed,
            ItemHeight = 52,
            IntegralHeight = false
        };
        listaUsuarios.DrawItem += DibujarContacto;
        listaUsuarios.SelectedIndexChanged += (_, _) => CambiarConversacion();
        lateral.Controls.Add(listaUsuarios);
        lateral.Controls.Add(etiquetaUsuarios);

        var chat = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Blanco,
            BorderStyle = BorderStyle.FixedSingle,
            ColumnCount = 1,
            RowCount = 4,
            Margin = new Padding(0)
        };
        chat.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
        chat.RowStyles.Add(new RowStyle(SizeType.Absolute, 72F));
        chat.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
        chat.RowStyles.Add(new RowStyle(SizeType.Absolute, 72F));
        chat.RowStyles.Add(new RowStyle(SizeType.Absolute, 28F));
        division.Panel2.Controls.Add(chat);

        var cabecera = new Panel { Dock = DockStyle.Fill, BackColor = Blanco };
        etiquetaConversacion = CrearEtiqueta(DestinoTodos, Texto, 16F, true);
        etiquetaConversacion.Location = new Point(18, 10);
        etiquetaConversacion.AutoSize = true;
        etiquetaDetalle = CrearEtiqueta("Desconectado", Secundario, 9F);
        etiquetaDetalle.Location = new Point(19, 42);
        etiquetaDetalle.AutoSize = true;
        cabecera.Controls.Add(etiquetaConversacion);
        cabecera.Controls.Add(etiquetaDetalle);
        chat.Controls.Add(cabecera, 0, 0);

        areaMensajes = new RichTextBox
        {
            Dock = DockStyle.Fill,
            BorderStyle = BorderStyle.None,
            BackColor = Blanco,
            ForeColor = Texto,
            ReadOnly = true,
            DetectUrls = false,
            Font = new Font("Segoe UI", 10F),
            ScrollBars = RichTextBoxScrollBars.Vertical,
            Margin = new Padding(14, 4, 8, 4)
        };
        chat.Controls.Add(areaMensajes, 0, 1);

        var redaccion = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Blanco,
            Padding = new Padding(14, 13, 14, 10),
            ColumnCount = 2,
            RowCount = 1
        };
        redaccion.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
        redaccion.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 112F));
        campoMensaje = CrearCampo("");
        campoMensaje.Margin = new Padding(0, 0, 10, 0);
        campoMensaje.KeyDown += async (_, evento) =>
        {
            if (evento.KeyCode != Keys.Enter)
            {
                return;
            }
            evento.SuppressKeyPress = true;
            await EnviarMensajeAsync();
        };
        botonEnviar = CrearBoton("Enviar", BotonInactivo);
        botonEnviar.ForeColor = TextoInactivo;
        botonEnviar.Dock = DockStyle.Fill;
        botonEnviar.Margin = new Padding(0);
        botonEnviar.Click += async (_, _) => await EnviarMensajeAsync();
        redaccion.Controls.Add(campoMensaje, 0, 0);
        redaccion.Controls.Add(botonEnviar, 1, 0);
        chat.Controls.Add(redaccion, 0, 2);

        etiquetaAviso = CrearEtiqueta(" ", Secundario, 9F);
        etiquetaAviso.Dock = DockStyle.Fill;
        etiquetaAviso.Padding = new Padding(17, 0, 10, 5);
        etiquetaAviso.TextAlign = ContentAlignment.MiddleLeft;
        chat.Controls.Add(etiquetaAviso, 0, 3);
    }

    /// <summary>
    /// Crea una etiqueta con el formato usado en la aplicación.
    /// </summary>
    private static Label CrearEtiqueta(
        string texto,
        Color color,
        float tamano,
        bool negrita = false)
    {
        return new Label
        {
            Text = texto,
            ForeColor = color,
            BackColor = Blanco,
            Font = new Font("Segoe UI", tamano, negrita ? FontStyle.Bold : FontStyle.Regular),
            AutoEllipsis = true,
            Margin = new Padding(0)
        };
    }

    /// <summary>
    /// Crea un campo de texto con el estilo de la ventana.
    /// </summary>
    private static TextBox CrearCampo(string texto)
    {
        return new TextBox
        {
            Text = texto,
            Dock = DockStyle.Fill,
            BorderStyle = BorderStyle.FixedSingle,
            Font = new Font("Segoe UI", 10F),
            ForeColor = Texto,
            BackColor = Blanco
        };
    }

    /// <summary>
    /// Crea un botón y aplica los colores principales del cliente.
    /// </summary>
    private static Button CrearBoton(string texto, Color color)
    {
        var boton = new Button
        {
            Text = texto,
            BackColor = color,
            ForeColor = Blanco,
            FlatStyle = FlatStyle.Flat,
            Cursor = Cursors.Hand,
            Font = new Font("Segoe UI", 9.5F, FontStyle.Bold),
            UseVisualStyleBackColor = false
        };
        boton.FlatAppearance.BorderSize = 0;
        boton.FlatAppearance.MouseOverBackColor = Color.FromArgb(29, 78, 216);
        return boton;
    }

    /// <summary>
    /// Dibuja un borde sencillo alrededor de un panel.
    /// </summary>
    private static void DibujarBorde(Graphics grafica, Control control)
    {
        using var lapiz = new Pen(Borde);
        grafica.DrawRectangle(lapiz, 0, 0, control.Width - 1, control.Height - 1);
    }

    /// <summary>
    /// Dibuja un usuario con sus iniciales, estado y mensajes sin leer.
    /// </summary>
    private void DibujarContacto(object? sender, DrawItemEventArgs evento)
    {
        if (evento.Index < 0 || evento.Index >= listaUsuarios.Items.Count)
        {
            return;
        }

        var contacto = (Contacto)listaUsuarios.Items[evento.Index];
        bool seleccionado = (evento.State & DrawItemState.Selected) != 0;
        Color fondo = seleccionado ? AzulSuave : Blanco;
        using var brochaFondo = new SolidBrush(fondo);
        evento.Graphics.FillRectangle(brochaFondo, evento.Bounds);

        var avatar = new Rectangle(evento.Bounds.X + 10, evento.Bounds.Y + 9, 34, 34);
        using var brochaAvatar = new SolidBrush(
            seleccionado ? Color.FromArgb(210, 226, 255) : Color.FromArgb(235, 241, 251));
        evento.Graphics.FillEllipse(brochaAvatar, avatar);

        string inicial = contacto.EsGrupo ? "#" : ObtenerIniciales(contacto.Nombre);
        using var fuenteInicial = new Font("Segoe UI", 9F, FontStyle.Bold);
        TextRenderer.DrawText(
            evento.Graphics,
            inicial,
            fuenteInicial,
            avatar,
            Azul,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);

        var nombreRect = new Rectangle(
            evento.Bounds.X + 53,
            evento.Bounds.Y + 6,
            Math.Max(30, evento.Bounds.Width - 90),
            23);
        using var fuenteNombre = new Font("Segoe UI", 10F, FontStyle.Bold);
        TextRenderer.DrawText(
            evento.Graphics,
            contacto.Nombre,
            fuenteNombre,
            nombreRect,
            Texto,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);

        string detalle = contacto.EsGrupo
            ? "Conversación general"
            : contacto.EnLinea ? "● En línea" : "○ Desconectado";
        Color colorDetalle = contacto.EnLinea && !contacto.EsGrupo ? Verde : Secundario;
        var detalleRect = new Rectangle(
            evento.Bounds.X + 53,
            evento.Bounds.Y + 27,
            Math.Max(30, evento.Bounds.Width - 65),
            19);
        using var fuenteDetalle = new Font("Segoe UI", 8.5F);
        TextRenderer.DrawText(
            evento.Graphics,
            detalle,
            fuenteDetalle,
            detalleRect,
            colorDetalle,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);

        if (contacto.NoLeidos > 0)
        {
            string cantidad = contacto.NoLeidos > 99 ? "99+" : contacto.NoLeidos.ToString();
            var cuentaRect = new Rectangle(
                evento.Bounds.Right - 39,
                evento.Bounds.Y + 16,
                28,
                20);
            using var fuenteCuenta = new Font("Segoe UI", 8F, FontStyle.Bold);
            TextRenderer.DrawText(
                evento.Graphics,
                cantidad,
                fuenteCuenta,
                cuentaRect,
                Azul,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        }

        evento.DrawFocusRectangle();
    }

    /// <summary>
    /// Obtiene hasta dos letras para formar el avatar de un contacto.
    /// </summary>
    private static string ObtenerIniciales(string nombre)
    {
        string limpio = nombre.Trim();
        if (limpio.Length == 0)
        {
            return "?";
        }
        return limpio[..Math.Min(2, limpio.Length)].ToUpperInvariant();
    }

    /// <summary>
    /// Inicia o cancela la conexión cuando se presiona Enter.
    /// </summary>
    private async void ConectarConEnter(object? sender, KeyEventArgs evento)
    {
        if (evento.KeyCode != Keys.Enter)
        {
            return;
        }
        evento.SuppressKeyPress = true;
        await CambiarConexionAsync();
    }

    /// <summary>
    /// Decide si el botón debe conectar o desconectar al cliente.
    /// </summary>
    private async Task CambiarConexionAsync()
    {
        if (conectando || conectado)
        {
            Desconectar(false);
            return;
        }
        await ConectarAsync();
    }

    /// <summary>
    /// Valida los datos, abre el socket y envía el nombre al servidor.
    ///
    /// La operación es asíncrona para que la ventana pueda seguir respondiendo
    /// mientras se busca al servidor.
    /// </summary>
    private async Task ConectarAsync()
    {
        string nombre = Limpiar(campoNombre.Text);
        string ip = campoIp.Text.Trim();

        if (nombre.Length == 0)
        {
            MessageBox.Show(
                this,
                "Escribe el nombre que quieres usar.",
                "Cliente C#",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
            campoNombre.Focus();
            return;
        }
        if (ip.Length == 0)
        {
            MessageBox.Show(
                this,
                "Escribe la IP del servidor.",
                "Cliente C#",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
            campoIp.Focus();
            return;
        }

        int sesion = ++generacion;
        conectando = true;
        conectado = false;
        identidadConfirmada = false;
        campoNombre.Enabled = false;
        campoIp.Enabled = false;
        botonConexion.Text = "Cancelar";
        botonConexion.BackColor = Color.FromArgb(71, 85, 105);
        etiquetaAviso.Text = "Buscando el servidor…";
        ActualizarConversacion();

        var nuevaConexion = new TcpClient { NoDelay = true };
        var nuevaCancelacion = new CancellationTokenSource();
        lock (bloqueoConexion)
        {
            conexion = nuevaConexion;
            cancelacion = nuevaCancelacion;
        }

        try
        {
            using var esperaConexion = CancellationTokenSource.CreateLinkedTokenSource(
                nuevaCancelacion.Token);
            esperaConexion.CancelAfter(TimeSpan.FromSeconds(5));
            await nuevaConexion.ConnectAsync(ip, Puerto, esperaConexion.Token);

            NetworkStream flujo = nuevaConexion.GetStream();
            var nuevaEntrada = new StreamReader(
                flujo,
                new UTF8Encoding(false),
                false,
                1024,
                true);
            var nuevaSalida = new StreamWriter(
                flujo,
                new UTF8Encoding(false),
                1024,
                true)
            {
                AutoFlush = true,
                NewLine = "\n"
            };

            lock (bloqueoConexion)
            {
                if (sesion != generacion)
                {
                    nuevaConexion.Dispose();
                    return;
                }
                entrada = nuevaEntrada;
                salida = nuevaSalida;
            }

            await nuevaSalida.WriteLineAsync($"HELLO\t{nombre}");
            using var esperaSaludo = CancellationTokenSource.CreateLinkedTokenSource(
                nuevaCancelacion.Token);
            esperaSaludo.CancelAfter(TimeSpan.FromSeconds(10));
            string? bienvenida = await nuevaEntrada.ReadLineAsync(esperaSaludo.Token);

            if (bienvenida is null
                || !bienvenida.StartsWith("WELCOME\t", StringComparison.Ordinal)
                || string.IsNullOrWhiteSpace(bienvenida[8..]))
            {
                throw new IOException("El servidor no confirmó tu nombre.");
            }

            if (sesion != generacion)
            {
                return;
            }
            ProcesarBienvenida(nombre, bienvenida);
            _ = RecibirLineasAsync(
                sesion,
                nuevaConexion,
                nuevaEntrada,
                nuevaSalida,
                nuevaCancelacion.Token);
        }
        catch (OperationCanceledException)
        {
            if (sesion == generacion && !nuevaCancelacion.IsCancellationRequested)
            {
                MostrarDesconectado("El servidor tardó demasiado en responder.");
            }
            LimpiarConexion(sesion, nuevaConexion);
        }
        catch (SocketException)
        {
            if (sesion == generacion)
            {
                MostrarDesconectado(
                    "No se pudo conectar. Revisa la IP y que el servidor esté abierto.");
            }
            LimpiarConexion(sesion, nuevaConexion);
        }
        catch (IOException)
        {
            if (sesion == generacion)
            {
                MostrarDesconectado("El servidor no pudo completar la conexión.");
            }
            LimpiarConexion(sesion, nuevaConexion);
        }
    }

    /// <summary>
    /// Confirma el nombre asignado y prepara una sesión nueva de chat.
    /// </summary>
    private void ProcesarBienvenida(string solicitado, string linea)
    {
        nombreActual = linea[8..];
        conectando = false;
        conectado = true;
        identidadConfirmada = true;
        conversaciones.Clear();
        conversaciones[""] = new Conversacion(DestinoTodos);
        usuarios.Clear();
        conversacionActual = "";
        campoNombre.Enabled = true;
        campoNombre.Text = nombreActual;
        campoNombre.Enabled = false;
        campoMensaje.Text = "";
        botonConexion.Text = "Desconectar";
        botonConexion.BackColor = Color.FromArgb(71, 85, 105);
        etiquetaAviso.Text = " ";
        ReconstruirLista();
        RenderizarConversacion();

        if (!string.Equals(solicitado, nombreActual, StringComparison.Ordinal))
        {
            RegistrarSistema(
                $"Ese nombre no estaba disponible. Entraste como {nombreActual}.");
        }
        campoMensaje.Focus();
    }

    /// <summary>
    /// Permanece leyendo líneas hasta que el servidor o el usuario cierre la conexión.
    /// </summary>
    private async Task RecibirLineasAsync(
        int sesion,
        TcpClient cliente,
        StreamReader lector,
        StreamWriter escritor,
        CancellationToken token)
    {
        string aviso = "El servidor cerró la conexión.";
        try
        {
            while (!token.IsCancellationRequested)
            {
                string? linea = await lector.ReadLineAsync(token).ConfigureAwait(false);
                if (linea is null)
                {
                    break;
                }
                EnInterfaz(sesion, () => ProcesarLinea(linea));
            }
        }
        catch (OperationCanceledException)
        {
            aviso = " ";
        }
        catch (IOException)
        {
            aviso = "Se perdió la conexión con el servidor.";
        }
        catch (ObjectDisposedException)
        {
            aviso = " ";
        }
        finally
        {
            escritor.Dispose();
            lector.Dispose();
            cliente.Dispose();
            EnInterfaz(sesion, () =>
            {
                LimpiarConexion(sesion, cliente);
                MostrarDesconectado(aviso);
            });
        }
    }

    /// <summary>
    /// Ejecuta en el hilo de la ventana una acción producida por la red.
    /// La sesión impide que una tarea anterior modifique una conexión más reciente.
    /// </summary>
    private void EnInterfaz(int sesion, Action accion)
    {
        if (IsDisposed || sesion != generacion)
        {
            return;
        }
        try
        {
            BeginInvoke(new Action(() =>
            {
                if (!IsDisposed && sesion == generacion)
                {
                    accion();
                }
            }));
        }
        catch (InvalidOperationException)
        {
            // La ventana ya se está cerrando.
        }
    }

    /// <summary>
    /// Interpreta una línea según los prefijos MSG, PRIVATE, USERS o SYS.
    /// </summary>
    private void ProcesarLinea(string linea)
    {
        if (linea.StartsWith("MSG\t", StringComparison.Ordinal))
        {
            string contenido = linea[4..];
            int separador = contenido.IndexOf('\t');
            if (separador >= 0)
            {
                string origen = contenido[..separador];
                string mensaje = contenido[(separador + 1)..];
                AgregarMensaje(
                    "",
                    new Mensaje(
                        origen,
                        mensaje,
                        Normalizar(origen) == Normalizar(nombreActual),
                        false));
            }
        }
        else if (linea.StartsWith("PRIVATE\t", StringComparison.Ordinal))
        {
            string[] partes = linea.Split(new[] { '\t' }, 4, StringSplitOptions.None);
            if (partes.Length != 4)
            {
                RegistrarSistema("No se pudo mostrar un mensaje.");
                return;
            }
            string origen = partes[1];
            string destino = partes[2];
            bool propio = Normalizar(origen) == Normalizar(nombreActual);
            if (!propio && Normalizar(destino) != Normalizar(nombreActual))
            {
                return;
            }
            string otro = propio ? destino : origen;
            string clave = Normalizar(otro);
            conversaciones.TryAdd(clave, new Conversacion(otro));
            AgregarMensaje(clave, new Mensaje(origen, partes[3], propio, false));
        }
        else if (linea == "USERS")
        {
            ActualizarUsuarios("");
        }
        else if (linea.StartsWith("USERS\t", StringComparison.Ordinal))
        {
            ActualizarUsuarios(linea[6..]);
        }
        else if (linea.StartsWith("SYS\t", StringComparison.Ordinal))
        {
            RegistrarSistema(linea[4..]);
        }
    }

    /// <summary>
    /// Guarda la lista que el servidor manda cada vez que alguien entra o sale.
    /// </summary>
    private void ActualizarUsuarios(string contenido)
    {
        usuarios.Clear();
        if (!string.IsNullOrWhiteSpace(contenido))
        {
            foreach (string nombre in contenido.Split('\t'))
            {
                string clave = Normalizar(nombre);
                if (clave.Length == 0 || clave == Normalizar(DestinoTodos))
                {
                    continue;
                }
                usuarios[clave] = nombre;
                if (clave != Normalizar(nombreActual))
                {
                    conversaciones.TryAdd(clave, new Conversacion(nombre));
                }
            }
        }
        ReconstruirLista();
    }

    /// <summary>
    /// Vuelve a mostrar el grupo y las conversaciones individuales.
    /// Los usuarios conectados se colocan antes que los desconectados.
    /// </summary>
    private void ReconstruirLista()
    {
        actualizandoLista = true;
        listaUsuarios.BeginUpdate();
        listaUsuarios.Items.Clear();

        Conversacion general = conversaciones[""];
        listaUsuarios.Items.Add(
            new Contacto("", DestinoTodos, true, true, general.NoLeidos));

        string propia = Normalizar(nombreActual);
        IEnumerable<string> claves = conversaciones
            .Where(par => par.Key.Length > 0
                && !string.Equals(par.Key, propia, StringComparison.OrdinalIgnoreCase)
                && (usuarios.ContainsKey(par.Key)
                    || par.Value.Mensajes.Count > 0
                    || par.Value.Borrador.Length > 0
                    || string.Equals(par.Key, conversacionActual, StringComparison.OrdinalIgnoreCase)))
            .OrderBy(par => usuarios.ContainsKey(par.Key) ? 0 : 1)
            .ThenBy(par => par.Value.Nombre, StringComparer.CurrentCultureIgnoreCase)
            .Select(par => par.Key);

        foreach (string clave in claves)
        {
            Conversacion conversacion = conversaciones[clave];
            listaUsuarios.Items.Add(
                new Contacto(
                    clave,
                    conversacion.Nombre,
                    false,
                    usuarios.ContainsKey(clave),
                    conversacion.NoLeidos));
        }

        int seleccionado = 0;
        for (int indice = 0; indice < listaUsuarios.Items.Count; indice++)
        {
            if (((Contacto)listaUsuarios.Items[indice]).Clave == conversacionActual)
            {
                seleccionado = indice;
                break;
            }
        }
        listaUsuarios.SelectedIndex = seleccionado;
        listaUsuarios.EndUpdate();
        actualizandoLista = false;
        etiquetaUsuarios.Text = $"Usuarios · {usuarios.Count}";
        ActualizarConversacion();
    }

    /// <summary>
    /// Abre el chat seleccionado y guarda el borrador de la conversación anterior.
    /// </summary>
    private void CambiarConversacion()
    {
        if (actualizandoLista || listaUsuarios.SelectedItem is not Contacto contacto)
        {
            return;
        }
        if (contacto.Clave == conversacionActual)
        {
            return;
        }

        conversaciones[conversacionActual].Borrador = campoMensaje.Text;
        conversacionActual = contacto.Clave;
        Conversacion conversacion = conversaciones[conversacionActual];
        conversacion.NoLeidos = 0;
        campoMensaje.Text = conversacion.Borrador;
        etiquetaAviso.Text = " ";
        ActualizarConversacion();
        RenderizarConversacion();
        ReconstruirLista();
        campoMensaje.Focus();
    }

    /// <summary>
    /// Revisa que la conexión esté lista y el destinatario siga disponible.
    /// </summary>
    private bool PuedeEnviar()
    {
        return conectado
            && identidadConfirmada
            && (conversacionActual.Length == 0 || usuarios.ContainsKey(conversacionActual));
    }

    /// <summary>
    /// Actualiza el título, el estado y los controles para escribir mensajes.
    /// </summary>
    private void ActualizarConversacion()
    {
        Conversacion conversacion = conversaciones[conversacionActual];
        etiquetaConversacion.Text = conversacion.Nombre;

        if (!conectado || !identidadConfirmada)
        {
            etiquetaDetalle.Text = conectando ? "Conectando…" : "Desconectado";
        }
        else if (conversacionActual.Length == 0)
        {
            etiquetaDetalle.Text = usuarios.Count == 1
                ? "1 participante"
                : $"{usuarios.Count} participantes";
        }
        else
        {
            etiquetaDetalle.Text = usuarios.ContainsKey(conversacionActual)
                ? "En línea"
                : "Desconectado";
        }

        bool habilitado = PuedeEnviar();
        campoMensaje.ReadOnly = !habilitado;
        campoMensaje.BackColor = habilitado ? Blanco : BotonInactivo;
        botonEnviar.Enabled = habilitado;
        botonEnviar.BackColor = habilitado ? Azul : BotonInactivo;
        botonEnviar.ForeColor = habilitado ? Blanco : TextoInactivo;
    }

    /// <summary>
    /// Prepara el texto como mensaje general o privado y después lo envía.
    /// </summary>
    private async Task EnviarMensajeAsync()
    {
        string mensaje = Limpiar(campoMensaje.Text);
        if (mensaje.Length == 0)
        {
            return;
        }
        if (!PuedeEnviar())
        {
            etiquetaAviso.Text = conversacionActual.Length == 0
                ? "Conéctate para enviar mensajes."
                : "Este usuario se desconectó.";
            return;
        }

        Conversacion conversacion = conversaciones[conversacionActual];
        string linea = conversacionActual.Length == 0
            ? $"MSG\t{mensaje}"
            : $"PRIVATE\t{conversacion.Nombre}\t{mensaje}";

        if (await EnviarLineaAsync(linea))
        {
            campoMensaje.Text = "";
            conversacion.Borrador = "";
            etiquetaAviso.Text = " ";
        }
    }

    /// <summary>
    /// Escribe una línea completa en el flujo de salida del servidor.
    /// </summary>
    /// <returns>True cuando la línea se pudo enviar.</returns>
    private async Task<bool> EnviarLineaAsync(string linea)
    {
        StreamWriter? escritor;
        lock (bloqueoConexion)
        {
            escritor = conectado ? salida : null;
        }
        if (escritor is null)
        {
            return false;
        }

        await bloqueoEnvio.WaitAsync();
        try
        {
            await escritor.WriteLineAsync(linea);
            return true;
        }
        catch (IOException)
        {
            Desconectar(false);
            etiquetaAviso.Text = "No se pudo enviar el mensaje. Vuelve a conectarte.";
            return false;
        }
        finally
        {
            bloqueoEnvio.Release();
        }
    }

    /// <summary>
    /// Guarda un mensaje y aumenta el contador si pertenece a otra conversación.
    /// </summary>
    private void AgregarMensaje(string clave, Mensaje mensaje)
    {
        if (!conversaciones.TryGetValue(clave, out Conversacion? conversacion))
        {
            conversacion = new Conversacion(clave.Length == 0 ? DestinoTodos : mensaje.Origen);
            conversaciones[clave] = conversacion;
        }
        conversacion.Mensajes.Add(mensaje);
        if (clave == conversacionActual)
        {
            RenderizarConversacion();
        }
        else
        {
            conversacion.NoLeidos++;
        }
        ReconstruirLista();
    }

    /// <summary>
    /// Agrega al chat general un aviso producido por el programa.
    /// </summary>
    private void RegistrarSistema(string texto)
    {
        etiquetaAviso.Text = texto;
        AgregarMensaje("", new Mensaje("", texto, false, true));
    }

    /// <summary>
    /// Dibuja en el área de mensajes el historial del chat seleccionado.
    /// </summary>
    private void RenderizarConversacion()
    {
        areaMensajes.Clear();
        foreach (Mensaje mensaje in conversaciones[conversacionActual].Mensajes)
        {
            if (mensaje.Sistema)
            {
                areaMensajes.SelectionColor = Secundario;
                using var cursiva = new Font(areaMensajes.Font, FontStyle.Italic);
                areaMensajes.SelectionFont = cursiva;
                areaMensajes.AppendText(mensaje.Texto + Environment.NewLine + Environment.NewLine);
                continue;
            }

            areaMensajes.SelectionColor = mensaje.Propio ? Azul : Color.FromArgb(37, 75, 135);
            using (var negrita = new Font(areaMensajes.Font, FontStyle.Bold))
            {
                areaMensajes.SelectionFont = negrita;
                areaMensajes.AppendText(mensaje.Origen);
            }
            areaMensajes.SelectionColor = Secundario;
            using (var fuenteHora = new Font("Segoe UI", 8F))
            {
                areaMensajes.SelectionFont = fuenteHora;
                areaMensajes.AppendText($"   {mensaje.Hora:HH:mm}{Environment.NewLine}");
            }
            areaMensajes.SelectionColor = Texto;
            areaMensajes.SelectionFont = areaMensajes.Font;
            areaMensajes.AppendText(mensaje.Texto + Environment.NewLine + Environment.NewLine);
        }
        areaMensajes.SelectionStart = areaMensajes.TextLength;
        areaMensajes.ScrollToCaret();
    }

    /// <summary>
    /// Cancela la sesión actual y libera los recursos de la conexión.
    /// </summary>
    private void Desconectar(bool mostrarAviso)
    {
        ++generacion;
        TcpClient? cliente;
        CancellationTokenSource? cancelar;
        lock (bloqueoConexion)
        {
            cliente = conexion;
            cancelar = cancelacion;
            conexion = null;
            entrada = null;
            salida = null;
            cancelacion = null;
        }
        cancelar?.Cancel();
        cliente?.Dispose();
        MostrarDesconectado(mostrarAviso ? "Te desconectaste del servidor." : " ");
    }

    /// <summary>
    /// Limpia los datos únicamente si pertenecen a la sesión que terminó.
    /// </summary>
    private void LimpiarConexion(int sesion, TcpClient cliente)
    {
        lock (bloqueoConexion)
        {
            if (sesion != generacion || !ReferenceEquals(conexion, cliente))
            {
                return;
            }
            conexion = null;
            entrada = null;
            salida = null;
            cancelacion = null;
        }
        cliente.Dispose();
    }

    /// <summary>
    /// Devuelve la ventana a su estado inicial y muestra un aviso opcional.
    /// </summary>
    private void MostrarDesconectado(string aviso)
    {
        conectando = false;
        conectado = false;
        identidadConfirmada = false;
        usuarios.Clear();
        campoNombre.Enabled = true;
        campoIp.Enabled = true;
        botonConexion.Text = "Conectar";
        botonConexion.BackColor = Azul;
        etiquetaAviso.Text = aviso;
        ReconstruirLista();
    }

    /// <summary>
    /// Quita saltos y tabuladores que podrían confundirse con el protocolo.
    /// </summary>
    private static string Limpiar(string texto)
    {
        return texto
            .Replace('\t', ' ')
            .Replace('\r', ' ')
            .Replace('\n', ' ')
            .Trim();
    }

    /// <summary>
    /// Genera una clave para comparar nombres sin importar mayúsculas.
    /// </summary>
    private static string Normalizar(string texto)
    {
        return texto.Trim().ToUpperInvariant();
    }
}


/// <summary>
/// Guarda el historial, el borrador y los mensajes pendientes de una conversación.
/// </summary>
internal sealed class Conversacion(string nombre)
{
    internal string Nombre { get; } = nombre;
    internal List<Mensaje> Mensajes { get; } = [];
    internal string Borrador { get; set; } = "";
    internal int NoLeidos { get; set; }
}


/// <summary>
/// Representa un mensaje que ya está listo para mostrarse en la ventana.
/// </summary>
internal sealed record Mensaje(
    string Origen,
    string Texto,
    bool Propio,
    bool Sistema)
{
    internal DateTime Hora { get; } = DateTime.Now;
}


/// <summary>
/// Reúne los datos necesarios para dibujar un elemento de la lista de usuarios.
/// </summary>
internal sealed record Contacto(
    string Clave,
    string Nombre,
    bool EsGrupo,
    bool EnLinea,
    int NoLeidos);
