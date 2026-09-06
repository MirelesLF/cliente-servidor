import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Clase auxiliar con métodos relacionados con las direcciones de red.
 *
 * Se utiliza principalmente para obtener las direcciones IPv4 de la
 * computadora servidor y mostrarlas dentro de la interfaz gráfica.
 */
public final class RedUtil {

    /**
     * Constructor privado porque esta clase solamente contiene métodos estáticos.
     */
    private RedUtil() {
        // No es necesario crear objetos de esta clase.
    }

    /**
     * Busca las direcciones IPv4 disponibles en los adaptadores de red activos.
     *
     * Se omiten interfaces apagadas, interfaces de loopback y adaptadores
     * marcados como virtuales para mostrar direcciones más útiles al usuario.
     *
     * @return lista ordenada de direcciones IPv4 encontradas.
     */
    public static List<String> obtenerDireccionesIPv4() {
        List<String> direcciones = new ArrayList<>();

        try {
            // Se obtienen todos los adaptadores de red de la computadora.
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            if (interfaces == null) {
                return List.of("127.0.0.1");
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface interfaz = interfaces.nextElement();

                /*
                 * Se ignoran adaptadores que no estén activos,
                 * que representen localhost o que sean virtuales.
                 */
                if (!interfaz.isUp()
                        || interfaz.isLoopback()
                        || interfaz.isVirtual()) {
                    continue;
                }

                // Se recorren las direcciones relacionadas con ese adaptador.
                Enumeration<InetAddress> direccionesInterfaz =
                        interfaz.getInetAddresses();

                while (direccionesInterfaz.hasMoreElements()) {
                    InetAddress direccion = direccionesInterfaz.nextElement();

                    // Solamente se agregan direcciones IPv4 reales de la red.
                    if (direccion instanceof Inet4Address
                            && !direccion.isLoopbackAddress()) {
                        direcciones.add(direccion.getHostAddress());
                    }
                }
            }

        } catch (SocketException ignored) {
            // Si no se pueden consultar las interfaces se utilizará localhost.
        }

        // Si no se encontró una dirección de red se agrega localhost como respaldo.
        if (direcciones.isEmpty()) {
            direcciones.add("127.0.0.1");
        }

        // Se ordenan para mantener una presentación constante en la interfaz.
        Collections.sort(direcciones);

        return direcciones;
    }

    /**
     * Une las direcciones IPv4 encontradas para mostrarlas en una sola línea.
     *
     * @return direcciones separadas por comas.
     */
    public static String obtenerDireccionesComoTexto() {
        return String.join(", ", obtenerDireccionesIPv4());
    }
}
