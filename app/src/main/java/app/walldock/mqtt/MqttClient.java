package app.walldock.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Petit client MQTT 3.1.1 en Java pur (aucune dépendance) : connexion avec identifiants,
 * message « testament » (publié par le broker si la tablette disparaît), publication et
 * abonnement en QoS 0, reconnexion automatique. Suffisant pour Gladys Assistant,
 * Home Assistant ou tout autre broker (Mosquitto…).
 */
public final class MqttClient {

    public interface Listener {
        /** Connexion établie (fil réseau) : c'est le moment de s'abonner et de republier les états. */
        void onConnected();

        /** Connexion perdue ou refusée ; une nouvelle tentative suit toute seule. */
        void onDisconnected(String reason);

        void onMessage(String topic, String payload);
    }

    public static final class Options {
        public String host = "";
        public int port = 1883;
        public String clientId = "domowalldock";
        public String username = "";
        public String password = "";
        /** Secondes ; le broker considère la tablette perdue après 1,5 fois ce délai sans nouvelles. */
        public int keepAlive = 30;
        /** Testament : null = aucun. */
        public String willTopic;
        public String willPayload = "";
        public boolean willRetain;
    }

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final long RETRY_MIN_MS = 5_000;
    private static final long RETRY_MAX_MS = 60_000;
    private static final int MAX_PACKET = 1 << 20;

    private final Options options;
    private final Listener listener;
    private final Object writeLock = new Object();
    private final List<String> subscriptions = new ArrayList<>();

    private volatile boolean running;
    private volatile boolean connected;
    private volatile Socket socket;
    private OutputStream out;
    private Thread thread;
    private int packetId;

    public MqttClient(Options options, Listener listener) {
        this.options = options;
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "walldock-mqtt");
        thread.setDaemon(true);
        thread.start();
    }

    /** Arrêt propre : le broker ne publie pas le testament. */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (connected) {
            try {
                write(new byte[] {(byte) 0xE0, 0});
            } catch (IOException e) {
                // déjà coupé
            }
        }
        closeSocket();
        if (thread != null) thread.interrupt();
        thread = null;
    }

    /** Publie en QoS 0 ; faux si la connexion n'est pas établie (à l'appelant de republier plus tard). */
    public boolean publish(String topic, String payload, boolean retain) {
        if (!connected) return false;
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, topic);
            body.write(payload.getBytes(StandardCharsets.UTF_8));
            write(packet(0x30 | (retain ? 1 : 0), body.toByteArray()));
            return true;
        } catch (IOException e) {
            closeSocket();
            return false;
        }
    }

    /** S'abonne (QoS 0) ; l'abonnement est refait à chaque reconnexion. */
    public void subscribe(String topicFilter) {
        synchronized (subscriptions) {
            if (!subscriptions.contains(topicFilter)) subscriptions.add(topicFilter);
        }
        if (connected) {
            try {
                sendSubscribe(topicFilter);
            } catch (IOException e) {
                closeSocket();
            }
        }
    }

    private void loop() {
        long retry = RETRY_MIN_MS;
        while (running) {
            String reason;
            try {
                session();
                reason = "connection closed";
            } catch (IOException e) {
                reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            boolean wasConnected = connected;
            connected = false;
            closeSocket();
            if (!running) return;
            listener.onDisconnected(reason);
            if (wasConnected) retry = RETRY_MIN_MS;
            try {
                Thread.sleep(retry);
            } catch (InterruptedException e) {
                return;
            }
            retry = Math.min(retry * 2, RETRY_MAX_MS);
        }
    }

    private void session() throws IOException {
        Socket s = new Socket();
        socket = s;
        s.connect(new InetSocketAddress(options.host, options.port), CONNECT_TIMEOUT_MS);
        s.setTcpNoDelay(true);
        s.setSoTimeout(CONNECT_TIMEOUT_MS);
        DataInputStream in = new DataInputStream(s.getInputStream());
        synchronized (writeLock) {
            out = s.getOutputStream();
        }
        write(connectPacket());

        int header = in.readUnsignedByte();
        byte[] ack = readBody(in);
        if (header >> 4 != 2 || ack.length < 2) throw new IOException("unexpected answer from the broker");
        if (ack[1] != 0) throw new IOException(refusal(ack[1]));

        // Demi-délai : assez tôt pour envoyer un ping avant que le broker ne s'impatiente
        int idleMs = Math.max(2, options.keepAlive / 2) * 1000;
        s.setSoTimeout(idleMs);
        connected = true;
        List<String> filters;
        synchronized (subscriptions) {
            filters = new ArrayList<>(subscriptions);
        }
        for (String filter : filters) sendSubscribe(filter);
        listener.onConnected();

        long lastReceived = System.nanoTime();
        while (running) {
            int first;
            try {
                first = in.read();
            } catch (SocketTimeoutException e) {
                if ((System.nanoTime() - lastReceived) / 1_000_000L > options.keepAlive * 2000L) {
                    throw new IOException("broker silent");
                }
                write(new byte[] {(byte) 0xC0, 0});
                continue;
            }
            if (first < 0) throw new EOFException("connection closed by the broker");
            byte[] body = readBody(in);
            lastReceived = System.nanoTime();
            if (first >> 4 == 3) handlePublish(first, body);
            // SUBACK, PINGRESP… : rien à faire
        }
    }

    private void handlePublish(int header, byte[] body) throws IOException {
        if (body.length < 2) return;
        int topicLength = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        int position = 2 + topicLength;
        if (position > body.length) return;
        String topic = new String(body, 2, topicLength, StandardCharsets.UTF_8);
        int qos = (header >> 1) & 3;
        if (qos > 0) {
            if (position + 2 > body.length) return;
            // Jamais demandé, mais on accuse réception si le broker envoie quand même en QoS 1
            if (qos == 1) write(new byte[] {0x40, 2, body[position], body[position + 1]});
            position += 2;
        }
        String payload = new String(body, position, body.length - position, StandardCharsets.UTF_8);
        try {
            listener.onMessage(topic, payload);
        } catch (RuntimeException e) {
            // un message mal formé ne doit pas couper la connexion
        }
    }

    private byte[] connectPacket() throws IOException {
        boolean will = options.willTopic != null && !options.willTopic.isEmpty();
        boolean user = options.username != null && !options.username.isEmpty();
        boolean pass = user && options.password != null && !options.password.isEmpty();
        int flags = 0x02; // session propre
        if (will) flags |= 0x04 | (options.willRetain ? 0x20 : 0);
        if (user) flags |= 0x80;
        if (pass) flags |= 0x40;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, "MQTT");
        body.write(4);
        body.write(flags);
        body.write(options.keepAlive >> 8);
        body.write(options.keepAlive & 0xFF);
        writeString(body, options.clientId);
        if (will) {
            writeString(body, options.willTopic);
            writeString(body, options.willPayload);
        }
        if (user) writeString(body, options.username);
        if (pass) writeString(body, options.password);
        return packet(0x10, body.toByteArray());
    }

    private void sendSubscribe(String filter) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int id;
        synchronized (writeLock) {
            packetId = packetId % 0xFFFF + 1;
            id = packetId;
        }
        body.write(id >> 8);
        body.write(id & 0xFF);
        writeString(body, filter);
        body.write(0);
        write(packet(0x82, body.toByteArray()));
    }

    private void write(byte[] data) throws IOException {
        synchronized (writeLock) {
            if (out == null) throw new IOException("not connected");
            out.write(data);
            out.flush();
        }
    }

    private void closeSocket() {
        connected = false;
        Socket s = socket;
        socket = null;
        synchronized (writeLock) {
            out = null;
        }
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                // déjà fermé
            }
        }
    }

    private static byte[] readBody(DataInputStream in) throws IOException {
        int length = 0;
        int shift = 0;
        int digit;
        do {
            digit = in.readUnsignedByte();
            length |= (digit & 0x7F) << shift;
            shift += 7;
            if (shift > 28) throw new IOException("malformed packet");
        } while ((digit & 0x80) != 0);
        if (length > MAX_PACKET) throw new IOException("packet too large");
        byte[] body = new byte[length];
        in.readFully(body);
        return body;
    }

    private static byte[] packet(int header, byte[] body) {
        ByteArrayOutputStream packet = new ByteArrayOutputStream(body.length + 5);
        packet.write(header);
        int length = body.length;
        do {
            int digit = length & 0x7F;
            length >>= 7;
            packet.write(length > 0 ? digit | 0x80 : digit);
        } while (length > 0);
        packet.write(body, 0, body.length);
        return packet.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream target, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) throw new IOException("text too long");
        target.write(bytes.length >> 8);
        target.write(bytes.length & 0xFF);
        target.write(bytes, 0, bytes.length);
    }

    private static String refusal(int code) {
        switch (code) {
            case 1: return "protocol refused by the broker";
            case 2: return "client identifier refused";
            case 3: return "broker unavailable";
            case 4: return "wrong user name or password";
            case 5: return "not authorized";
            default: return "connection refused (" + code + ")";
        }
    }
}
