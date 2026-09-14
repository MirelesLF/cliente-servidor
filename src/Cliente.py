"""Cliente gráfico en Python para el chat de red local.

Este programa se conecta al servidor Java por medio de un socket TCP en el
puerto 5000. Permite conversar con todos los participantes o seleccionar a una
persona para enviarle mensajes privados. La lista de usuarios cambia sola cada
vez que el servidor informa que alguien entró o salió.
"""

from __future__ import annotations

import getpass
import queue
import socket
import threading
import tkinter as tk
from dataclasses import dataclass, field
from datetime import datetime
from tkinter import messagebox


PUERTO = 5000
DESTINO_TODOS = "Todos"

FONDO = "#f5f7fb"
BLANCO = "#ffffff"
TEXTO = "#1e293b"
SECUNDARIO = "#64748b"
AZUL = "#2563eb"
AZUL_SUAVE = "#e8f0ff"
VERDE = "#168053"
BORDE = "#d9e1ec"
BOTON_INACTIVO = "#f1f5f9"
TEXTO_INACTIVO = "#334155"


@dataclass
class Mensaje:
    """Guarda la información necesaria para mostrar un mensaje en el chat."""

    origen: str
    texto: str
    propio: bool = False
    sistema: bool = False
    hora: str = field(default_factory=lambda: datetime.now().strftime("%H:%M"))


@dataclass
class Conversacion:
    """Conserva el historial, el borrador y los mensajes pendientes de un chat."""

    nombre: str
    mensajes: list[Mensaje] = field(default_factory=list)
    borrador: str = ""
    no_leidos: int = 0


class ClientePython(tk.Tk):
    """Ventana principal del cliente de mensajería hecho en Python."""

    def __init__(self) -> None:
        """Prepara el estado del cliente y construye todos los controles."""
        super().__init__()
        self.title("Cliente Python")
        self.geometry("1000x650")
        self.minsize(820, 540)
        self.configure(bg=FONDO)

        # El hilo de red coloca aquí los cambios que después mostrará Tkinter.
        self._eventos: queue.Queue[tuple] = queue.Queue()

        # Estos datos se protegen porque se usan desde más de un hilo.
        self._bloqueo_conexion = threading.Lock()
        self._socket: socket.socket | None = None
        self._entrada = None
        self._salida = None
        self._generacion = 0
        self._conectando = False
        self._conectado = False
        self._identidad_confirmada = False

        # La clave vacía representa la conversación general con Todos.
        self._nombre_actual = ""
        self._usuarios: dict[str, str] = {}
        self._conversaciones: dict[str, Conversacion] = {
            "": Conversacion(DESTINO_TODOS)
        }
        self._conversacion_actual = ""
        self._claves_lista: list[str] = []
        self._actualizando_lista = False

        self._crear_interfaz()
        self._reconstruir_lista()
        self.protocol("WM_DELETE_WINDOW", self._cerrar_ventana)
        self.after(50, self._procesar_eventos)

    @staticmethod
    def _nombre_predeterminado() -> str:
        """Obtiene el nombre de la cuenta o utiliza Usuario como alternativa."""
        nombre = getpass.getuser().strip()
        return nombre if nombre else "Usuario"

    def _crear_interfaz(self) -> None:
        """Crea y acomoda los elementos que aparecen en la ventana."""
        superior = tk.Frame(
            self,
            bg=BLANCO,
            highlightbackground=BORDE,
            highlightthickness=1,
            padx=14,
            pady=10,
        )
        superior.pack(fill="x", padx=14, pady=(14, 10))
        superior.columnconfigure(0, weight=4)
        superior.columnconfigure(1, weight=5)

        tk.Label(superior, text="Nombre", bg=BLANCO, fg=SECUNDARIO).grid(
            row=0, column=0, sticky="w"
        )
        tk.Label(superior, text="IP del servidor", bg=BLANCO, fg=SECUNDARIO).grid(
            row=0, column=1, sticky="w", padx=(12, 0)
        )

        self.campo_nombre = self._crear_entrada(superior)
        self.campo_nombre.insert(0, self._nombre_predeterminado())
        self.campo_nombre.grid(row=1, column=0, sticky="ew", pady=(4, 0))

        self.campo_ip = self._crear_entrada(superior)
        self.campo_ip.insert(0, "127.0.0.1")
        self.campo_ip.grid(
            row=1, column=1, sticky="ew", padx=(12, 12), pady=(4, 0)
        )

        self.boton_conexion = tk.Button(
            superior,
            text="Conectar",
            command=self._cambiar_conexion,
            bg=AZUL,
            fg=BLANCO,
            activebackground="#1d4ed8",
            activeforeground=BLANCO,
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            bd=0,
            padx=20,
            pady=9,
            cursor="hand2",
        )
        self.boton_conexion.grid(row=0, column=2, rowspan=2, sticky="nsew")

        cuerpo = tk.Frame(self, bg=FONDO)
        cuerpo.pack(fill="both", expand=True, padx=14, pady=(0, 14))
        cuerpo.columnconfigure(0, minsize=235)
        cuerpo.columnconfigure(1, weight=1)
        cuerpo.rowconfigure(0, weight=1)

        lateral = tk.Frame(
            cuerpo,
            bg=BLANCO,
            highlightbackground=BORDE,
            highlightthickness=1,
        )
        lateral.grid(row=0, column=0, sticky="nsew")
        lateral.rowconfigure(1, weight=1)
        lateral.columnconfigure(0, weight=1)

        self.etiqueta_usuarios = tk.Label(
            lateral,
            text="Usuarios · 0",
            bg=BLANCO,
            fg=SECUNDARIO,
            font=("Segoe UI", 10, "bold"),
            anchor="w",
            padx=14,
            pady=13,
        )
        self.etiqueta_usuarios.grid(row=0, column=0, sticky="ew")

        self.lista_usuarios = tk.Listbox(
            lateral,
            bg=BLANCO,
            fg=TEXTO,
            selectbackground=AZUL_SUAVE,
            selectforeground=TEXTO,
            highlightthickness=0,
            borderwidth=0,
            font=("Segoe UI", 11, "bold"),
            activestyle="none",
            exportselection=False,
        )
        self.lista_usuarios.grid(row=1, column=0, sticky="nsew", padx=6, pady=(0, 6))
        self.lista_usuarios.bind("<<ListboxSelect>>", self._cambiar_conversacion)

        chat = tk.Frame(
            cuerpo,
            bg=BLANCO,
            highlightbackground=BORDE,
            highlightthickness=1,
        )
        chat.grid(row=0, column=1, sticky="nsew", padx=(10, 0))
        chat.columnconfigure(0, weight=1)
        chat.rowconfigure(1, weight=1)

        cabecera = tk.Frame(chat, bg=BLANCO, padx=18, pady=12)
        cabecera.grid(row=0, column=0, columnspan=2, sticky="ew")
        self.etiqueta_conversacion = tk.Label(
            cabecera,
            text=DESTINO_TODOS,
            bg=BLANCO,
            fg=TEXTO,
            font=("Segoe UI", 16, "bold"),
            anchor="w",
        )
        self.etiqueta_conversacion.pack(anchor="w")
        self.etiqueta_detalle = tk.Label(
            cabecera,
            text="Desconectado",
            bg=BLANCO,
            fg=SECUNDARIO,
            font=("Segoe UI", 9),
            anchor="w",
        )
        self.etiqueta_detalle.pack(anchor="w", pady=(4, 0))

        marco_mensajes = tk.Frame(chat, bg=BLANCO)
        marco_mensajes.grid(row=1, column=0, columnspan=2, sticky="nsew")
        marco_mensajes.rowconfigure(0, weight=1)
        marco_mensajes.columnconfigure(0, weight=1)

        self.area_mensajes = tk.Text(
            marco_mensajes,
            state="disabled",
            wrap="word",
            bg=BLANCO,
            fg=TEXTO,
            font=("Segoe UI", 10),
            relief="flat",
            padx=18,
            pady=14,
            cursor="arrow",
        )
        desplazamiento = tk.Scrollbar(
            marco_mensajes, command=self.area_mensajes.yview
        )
        self.area_mensajes.configure(yscrollcommand=desplazamiento.set)
        self.area_mensajes.grid(row=0, column=0, sticky="nsew")
        desplazamiento.grid(row=0, column=1, sticky="ns")
        self.area_mensajes.tag_configure(
            "nombre", foreground="#254b87", font=("Segoe UI", 9, "bold")
        )
        self.area_mensajes.tag_configure(
            "propio", foreground=AZUL, font=("Segoe UI", 9, "bold")
        )
        self.area_mensajes.tag_configure(
            "hora", foreground=SECUNDARIO, font=("Segoe UI", 8)
        )
        self.area_mensajes.tag_configure(
            "sistema", foreground=SECUNDARIO, font=("Segoe UI", 9, "italic")
        )

        redaccion = tk.Frame(chat, bg=BLANCO, padx=16, pady=14)
        redaccion.grid(row=2, column=0, columnspan=2, sticky="ew")
        redaccion.columnconfigure(0, weight=1)

        self.campo_mensaje = self._crear_entrada(redaccion, altura=12)
        self.campo_mensaje.grid(row=0, column=0, sticky="ew", padx=(0, 10))
        self.campo_mensaje.bind("<Return>", self._enviar_con_enter)

        self.boton_enviar = tk.Button(
            redaccion,
            text="Enviar",
            command=self._enviar_mensaje,
            bg=BOTON_INACTIVO,
            fg=TEXTO_INACTIVO,
            disabledforeground=TEXTO_INACTIVO,
            activebackground="#1d4ed8",
            activeforeground=BLANCO,
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            bd=0,
            padx=22,
            pady=10,
        )
        self.boton_enviar.grid(row=0, column=1, sticky="nsew")

        self.etiqueta_aviso = tk.Label(
            chat,
            text=" ",
            bg=BLANCO,
            fg=SECUNDARIO,
            font=("Segoe UI", 9),
            anchor="w",
            padx=17,
            pady=3,
        )
        self.etiqueta_aviso.grid(row=3, column=0, columnspan=2, sticky="ew")

        self.campo_nombre.bind("<Return>", lambda _evento: self._cambiar_conexion())
        self.campo_ip.bind("<Return>", lambda _evento: self._cambiar_conexion())

    @staticmethod
    def _crear_entrada(padre: tk.Misc, altura: int = 9) -> tk.Entry:
        """Crea un campo de texto con el mismo estilo que los demás controles."""
        return tk.Entry(
            padre,
            bg=BLANCO,
            fg=TEXTO,
            disabledbackground=BOTON_INACTIVO,
            disabledforeground=SECUNDARIO,
            insertbackground=TEXTO,
            font=("Segoe UI", 10),
            relief="solid",
            bd=1,
            highlightthickness=0,
        )

    @staticmethod
    def _limpiar(texto: str) -> str:
        """Quita caracteres que podrían confundirse con el protocolo del chat."""
        return texto.replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()

    @staticmethod
    def _normalizar(nombre: str) -> str:
        """Genera una clave para comparar nombres sin importar mayúsculas."""
        return nombre.strip().casefold()

    def _cambiar_conexion(self) -> None:
        """Conecta o desconecta según el estado actual del cliente."""
        if self._conectando or self._conectado:
            self._desconectar()
        else:
            self._conectar()

    def _conectar(self) -> None:
        """Valida los datos e inicia la conexión sin bloquear la ventana."""
        nombre = self._limpiar(self.campo_nombre.get())
        ip = self.campo_ip.get().strip()

        if not nombre:
            messagebox.showinfo("Cliente Python", "Escribe el nombre que quieres usar.")
            self.campo_nombre.focus_set()
            return
        if not ip:
            messagebox.showinfo("Cliente Python", "Escribe la IP del servidor.")
            self.campo_ip.focus_set()
            return

        self._generacion += 1
        generacion = self._generacion
        self._conectando = True
        self._conectado = False
        self._identidad_confirmada = False
        self.campo_nombre.configure(state="disabled")
        self.campo_ip.configure(state="disabled")
        self.boton_conexion.configure(text="Cancelar", bg="#475569")
        self.etiqueta_aviso.configure(text="Buscando el servidor…")
        self._actualizar_conversacion()

        # El socket trabaja en otro hilo para que la interfaz no se congele.
        threading.Thread(
            target=self._trabajo_conexion,
            args=(generacion, nombre, ip),
            daemon=True,
        ).start()

    def _trabajo_conexion(self, generacion: int, nombre: str, ip: str) -> None:
        """Abre el socket, manda el nombre y escucha las respuestas del servidor.

        La generación identifica este intento de conexión. Si el usuario intenta
        conectarse otra vez, las respuestas de un hilo anterior ya no se utilizan.
        """
        conexion: socket.socket | None = None
        entrada = None
        salida = None
        conexion_confirmada = False

        try:
            conexion = socket.create_connection((ip, PUERTO), timeout=5)
            conexion.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
            conexion.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            entrada = conexion.makefile("r", encoding="utf-8", newline="\n")
            salida = conexion.makefile("w", encoding="utf-8", newline="\n")

            with self._bloqueo_conexion:
                if generacion != self._generacion:
                    return
                self._socket = conexion
                self._entrada = entrada
                self._salida = salida

            salida.write(f"HELLO\t{nombre}\n")
            salida.flush()
            bienvenida = entrada.readline()
            if not bienvenida:
                raise ConnectionError("El servidor cerró la conexión.")
            bienvenida = bienvenida.rstrip("\r\n")
            if not bienvenida.startswith("WELCOME\t") or not bienvenida[8:].strip():
                raise ConnectionError("El servidor no confirmó el nombre.")

            conexion.settimeout(None)
            conexion_confirmada = True
            self._eventos.put(("bienvenida", generacion, nombre, bienvenida))

            while generacion == self._generacion:
                linea = entrada.readline()
                if not linea:
                    break
                self._eventos.put(("linea", generacion, linea.rstrip("\r\n")))

            if generacion == self._generacion:
                self._eventos.put(
                    ("cerrada", generacion, "El servidor cerró la conexión.")
                )
        except socket.timeout:
            self._eventos.put(
                ("error", generacion, "El servidor tardó demasiado en responder.")
            )
        except socket.gaierror:
            self._eventos.put(
                ("error", generacion, "No se encontró esa dirección. Revisa la IP.")
            )
        except (ConnectionError, OSError):
            if generacion == self._generacion:
                aviso = (
                    "Se perdió la conexión con el servidor."
                    if conexion_confirmada
                    else "No se pudo conectar. Revisa la IP y que el servidor esté abierto."
                )
                self._eventos.put(("error", generacion, aviso))
        finally:
            with self._bloqueo_conexion:
                if generacion == self._generacion and self._socket is conexion:
                    self._socket = None
                    self._entrada = None
                    self._salida = None
            for recurso in (salida, entrada, conexion):
                try:
                    if recurso is not None:
                        recurso.close()
                except OSError:
                    pass

    def _procesar_eventos(self) -> None:
        """Pasa al hilo de Tkinter los eventos que produjo el hilo de red."""
        try:
            while True:
                evento = self._eventos.get_nowait()
                tipo, generacion, *datos = evento
                if generacion != self._generacion:
                    continue
                if tipo == "bienvenida":
                    self._procesar_bienvenida(datos[0], datos[1])
                elif tipo == "linea":
                    self._procesar_linea(datos[0])
                elif tipo in {"error", "cerrada"}:
                    self._mostrar_desconectado(datos[0])
        except queue.Empty:
            pass
        if self.winfo_exists():
            self.after(50, self._procesar_eventos)

    def _procesar_bienvenida(self, solicitado: str, linea: str) -> None:
        """Confirma el nombre asignado y prepara una sesión nueva de chat."""
        self._nombre_actual = linea.removeprefix("WELCOME\t")
        self._conectando = False
        self._conectado = True
        self._identidad_confirmada = True
        self._conversaciones = {"": Conversacion(DESTINO_TODOS)}
        self._usuarios.clear()
        self._conversacion_actual = ""
        self.campo_mensaje.configure(state="normal")
        self.campo_mensaje.delete(0, "end")
        self.campo_nombre.configure(state="normal")
        self.campo_nombre.delete(0, "end")
        self.campo_nombre.insert(0, self._nombre_actual)
        self.campo_nombre.configure(state="disabled")
        self.boton_conexion.configure(text="Desconectar", bg="#475569")
        self.etiqueta_aviso.configure(text=" ")
        self._reconstruir_lista()
        self._renderizar_conversacion()
        if solicitado != self._nombre_actual:
            self._registrar_sistema(
                f"Ese nombre no estaba disponible. Entraste como {self._nombre_actual}."
            )
        self.campo_mensaje.focus_set()

    def _procesar_linea(self, linea: str) -> None:
        """Interpreta una línea recibida según el prefijo usado por el servidor."""
        if linea.startswith("WELCOME\t"):
            return
        if linea.startswith("MSG\t"):
            partes = linea.split("\t", 2)
            if len(partes) == 3:
                self._agregar_mensaje(
                    "",
                    Mensaje(
                        partes[1],
                        partes[2],
                        self._normalizar(partes[1])
                        == self._normalizar(self._nombre_actual),
                    ),
                )
        elif linea.startswith("PRIVATE\t"):
            partes = linea.split("\t", 3)
            if len(partes) != 4:
                self._registrar_sistema("No se pudo mostrar un mensaje.")
                return
            origen, destino, texto = partes[1], partes[2], partes[3]
            propio = self._normalizar(origen) == self._normalizar(self._nombre_actual)
            if not propio and self._normalizar(destino) != self._normalizar(
                self._nombre_actual
            ):
                return
            otro = destino if propio else origen
            clave = self._normalizar(otro)
            self._conversaciones.setdefault(clave, Conversacion(otro))
            self._agregar_mensaje(clave, Mensaje(origen, texto, propio))
        elif linea.startswith("USERS\t"):
            self._actualizar_usuarios(linea[6:])
        elif linea == "USERS":
            self._actualizar_usuarios("")
        elif linea.startswith("SYS\t"):
            self._registrar_sistema(linea[4:])

    def _actualizar_usuarios(self, contenido: str) -> None:
        """Guarda la lista de usuarios que el servidor envía automáticamente."""
        nuevos: dict[str, str] = {}
        if contenido.strip():
            for nombre in contenido.split("\t"):
                clave = self._normalizar(nombre)
                if clave and clave != self._normalizar(DESTINO_TODOS):
                    nuevos[clave] = nombre
                    if clave != self._normalizar(self._nombre_actual):
                        self._conversaciones.setdefault(clave, Conversacion(nombre))
        self._usuarios = nuevos
        self._reconstruir_lista()

    def _reconstruir_lista(self) -> None:
        """Vuelve a mostrar el grupo y las conversaciones individuales.

        Los usuarios conectados aparecen primero. También se conservan los chats
        anteriores cuando contienen mensajes o un borrador sin enviar.
        """
        self._actualizando_lista = True
        self.lista_usuarios.delete(0, "end")
        self._claves_lista = [""]

        general = self._conversaciones.get("", Conversacion(DESTINO_TODOS))
        texto_general = DESTINO_TODOS
        if general.no_leidos:
            texto_general += f"  ({general.no_leidos})"
        self.lista_usuarios.insert("end", texto_general)

        propia = self._normalizar(self._nombre_actual)
        claves = [
            clave
            for clave, conversacion in self._conversaciones.items()
            if clave
            and clave != propia
            and (
                clave in self._usuarios
                or conversacion.mensajes
                or conversacion.borrador
                or clave == self._conversacion_actual
            )
        ]
        claves.sort(
            key=lambda clave: (
                clave not in self._usuarios,
                self._conversaciones[clave].nombre.casefold(),
            )
        )

        for clave in claves:
            conversacion = self._conversaciones[clave]
            conectado = clave in self._usuarios
            prefijo = "●" if conectado else "○"
            texto = f"{prefijo}  {conversacion.nombre}"
            if conversacion.no_leidos:
                texto += f"  ({conversacion.no_leidos})"
            self._claves_lista.append(clave)
            self.lista_usuarios.insert("end", texto)
            indice = self.lista_usuarios.size() - 1
            self.lista_usuarios.itemconfig(
                indice, foreground=TEXTO if conectado else SECUNDARIO
            )

        try:
            indice_actual = self._claves_lista.index(self._conversacion_actual)
        except ValueError:
            self._conversacion_actual = ""
            indice_actual = 0
        self.lista_usuarios.selection_set(indice_actual)
        self.lista_usuarios.see(indice_actual)
        self._actualizando_lista = False
        self.etiqueta_usuarios.configure(text=f"Usuarios · {len(self._usuarios)}")
        self._actualizar_conversacion()

    def _cambiar_conversacion(self, _evento=None) -> None:
        """Abre la conversación seleccionada y conserva el borrador anterior."""
        if self._actualizando_lista:
            return
        seleccion = self.lista_usuarios.curselection()
        if not seleccion:
            return
        nueva = self._claves_lista[seleccion[0]]
        if nueva == self._conversacion_actual:
            return

        actual = self._conversaciones[self._conversacion_actual]
        actual.borrador = self.campo_mensaje.get()
        self._conversacion_actual = nueva
        conversacion = self._conversaciones[nueva]
        conversacion.no_leidos = 0
        self.campo_mensaje.configure(state="normal")
        self.campo_mensaje.delete(0, "end")
        self.campo_mensaje.insert(0, conversacion.borrador)
        self.etiqueta_aviso.configure(text=" ")
        self._actualizar_conversacion()
        self._renderizar_conversacion()
        self._reconstruir_lista()
        self.campo_mensaje.focus_set()

    def _puede_enviar(self) -> bool:
        """Indica si hay conexión y el destinatario todavía está disponible."""
        return self._conectado and self._identidad_confirmada and (
            not self._conversacion_actual
            or self._conversacion_actual in self._usuarios
        )

    def _actualizar_conversacion(self) -> None:
        """Actualiza el título, el estado y los controles para enviar mensajes."""
        conversacion = self._conversaciones[self._conversacion_actual]
        self.etiqueta_conversacion.configure(text=conversacion.nombre)

        if not self._conectado or not self._identidad_confirmada:
            detalle = "Conectando…" if self._conectando else "Desconectado"
        elif not self._conversacion_actual:
            cantidad = len(self._usuarios)
            detalle = f"{cantidad} participante" if cantidad == 1 else f"{cantidad} participantes"
        elif self._conversacion_actual in self._usuarios:
            detalle = "En línea"
        else:
            detalle = "Desconectado"
        self.etiqueta_detalle.configure(text=detalle)

        habilitado = self._puede_enviar()
        self.campo_mensaje.configure(state="normal" if habilitado else "disabled")
        self.boton_enviar.configure(
            state="normal" if habilitado else "disabled",
            bg=AZUL if habilitado else BOTON_INACTIVO,
            fg=BLANCO if habilitado else TEXTO_INACTIVO,
        )

    def _enviar_con_enter(self, _evento) -> str:
        """Envía el mensaje cuando se presiona Enter y evita el salto normal."""
        self._enviar_mensaje()
        return "break"

    def _enviar_mensaje(self) -> None:
        """Prepara un mensaje grupal o privado según el chat seleccionado."""
        mensaje = self._limpiar(self.campo_mensaje.get())
        if not mensaje:
            return
        if not self._puede_enviar():
            aviso = (
                "Conéctate para enviar mensajes."
                if not self._conversacion_actual
                else "Este usuario se desconectó."
            )
            self.etiqueta_aviso.configure(text=aviso)
            return

        conversacion = self._conversaciones[self._conversacion_actual]
        linea = (
            f"MSG\t{mensaje}"
            if not self._conversacion_actual
            else f"PRIVATE\t{conversacion.nombre}\t{mensaje}"
        )
        if self._enviar_linea(linea):
            self.campo_mensaje.delete(0, "end")
            conversacion.borrador = ""
            self.etiqueta_aviso.configure(text=" ")

    def _enviar_linea(self, linea: str) -> bool:
        """Manda una línea al servidor y avisa si la conexión dejó de funcionar."""
        try:
            with self._bloqueo_conexion:
                if not self._conectado or self._salida is None:
                    return False
                self._salida.write(linea + "\n")
                self._salida.flush()
            return True
        except OSError:
            self._desconectar()
            self.etiqueta_aviso.configure(
                text="No se pudo enviar el mensaje. Vuelve a conectarte."
            )
            return False

    def _agregar_mensaje(self, clave: str, mensaje: Mensaje) -> None:
        """Guarda un mensaje y cuenta como pendiente si pertenece a otro chat."""
        conversacion = self._conversaciones.setdefault(
            clave, Conversacion(DESTINO_TODOS if not clave else mensaje.origen)
        )
        conversacion.mensajes.append(mensaje)
        if clave != self._conversacion_actual:
            conversacion.no_leidos += 1
        else:
            self._renderizar_conversacion()
        self._reconstruir_lista()

    def _registrar_sistema(self, texto: str) -> None:
        """Muestra en el chat general un aviso producido por el programa."""
        self.etiqueta_aviso.configure(text=texto)
        self._agregar_mensaje("", Mensaje("", texto, sistema=True))

    def _renderizar_conversacion(self) -> None:
        """Dibuja el historial de la conversación que está seleccionada."""
        conversacion = self._conversaciones[self._conversacion_actual]
        self.area_mensajes.configure(state="normal")
        self.area_mensajes.delete("1.0", "end")

        for mensaje in conversacion.mensajes:
            if mensaje.sistema:
                self.area_mensajes.insert("end", mensaje.texto + "\n\n", "sistema")
                continue
            etiqueta = "propio" if mensaje.propio else "nombre"
            self.area_mensajes.insert("end", mensaje.origen, etiqueta)
            self.area_mensajes.insert("end", f"   {mensaje.hora}\n", "hora")
            self.area_mensajes.insert("end", mensaje.texto + "\n\n")

        self.area_mensajes.configure(state="disabled")
        self.area_mensajes.see("end")

    def _desconectar(self) -> None:
        """Cancela la sesión actual y libera el socket utilizado."""
        self._generacion += 1
        with self._bloqueo_conexion:
            conexion = self._socket
            self._socket = None
            self._entrada = None
            self._salida = None
        if conexion is not None:
            try:
                conexion.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                conexion.close()
            except OSError:
                pass
        self._mostrar_desconectado(" ")

    def _mostrar_desconectado(self, aviso: str) -> None:
        """Devuelve la ventana a su estado inicial y muestra un aviso opcional."""
        self._conectando = False
        self._conectado = False
        self._identidad_confirmada = False
        self._usuarios.clear()
        self.campo_nombre.configure(state="normal")
        self.campo_ip.configure(state="normal")
        self.boton_conexion.configure(text="Conectar", bg=AZUL)
        self.etiqueta_aviso.configure(text=aviso)
        self._reconstruir_lista()

    def _cerrar_ventana(self) -> None:
        """Cierra primero la conexión y después termina la ventana."""
        self._desconectar()
        self.destroy()


if __name__ == "__main__":
    # Tkinter mantiene aquí el ciclo de eventos de la aplicación.
    ClientePython().mainloop()
