package app.walldock.mqtt;

/**
 * Messages de « découverte MQTT » au format Home Assistant, compris par Gladys Assistant
 * (Intégrations → MQTT) : un message de configuration par entité, sur
 * homeassistant/&lt;type&gt;/&lt;appareil&gt;/&lt;entité&gt;/config.
 */
public final class Discovery {

    private Discovery() {}

    public static String baseTopic(String deviceId) {
        return "walldock/" + deviceId;
    }

    public static String configTopic(String component, String deviceId, String key) {
        return "homeassistant/" + component + "/" + deviceId + "/" + key + "/config";
    }

    public static String stateTopic(String deviceId, String key) {
        return baseTopic(deviceId) + "/" + key;
    }

    public static String commandTopic(String deviceId, String key) {
        return baseTopic(deviceId) + "/" + key + "/set";
    }

    /**
     * Configuration d'une entité. component : sensor, binary_sensor, switch ou button ;
     * deviceClass et unit peuvent être null.
     */
    public static String config(String component, String key, String name, String deviceClass, String unit,
                                String deviceId, String deviceLabel, String model, String version) {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "name", name).append(',');
        field(json, "unique_id", deviceId + "_" + key).append(',');
        if (!"button".equals(component)) field(json, "state_topic", stateTopic(deviceId, key)).append(',');
        if ("button".equals(component) || "switch".equals(component)) {
            field(json, "command_topic", commandTopic(deviceId, key)).append(',');
        }
        if (deviceClass != null) field(json, "device_class", deviceClass).append(',');
        if (unit != null) {
            field(json, "unit_of_measurement", unit).append(',');
            field(json, "state_class", "measurement").append(',');
        }
        json.append("\"device\":{\"identifiers\":[").append(quote(deviceId)).append("],");
        field(json, "name", deviceLabel).append(',');
        field(json, "manufacturer", "WallDock").append(',');
        field(json, "model", model).append(',');
        field(json, "sw_version", version);
        json.append("}}");
        return json.toString();
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        return json.append(quote(name)).append(':').append(quote(value));
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
