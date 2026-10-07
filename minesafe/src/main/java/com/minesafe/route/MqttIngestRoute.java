package com.minesafe.route;

import com.minesafe.service.TelemetryIngester;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumes BLE tag telemetry from the same broker/topic as the legacy Python app:
 * broker tcp://sakura.proxy.rlwy.net:50528, topic knot/ble/tags.
 *
 * <p>The broker URL and client id are configured on the {@code paho} component
 * (MQTT 3.1.1) via {@code camel.component.paho.*} in application.properties;
 * only the topic is part of the endpoint URI.
 *
 * <p>M2 scope: subscribe, parse the JSON body, evaluate per-MAC safety, persist
 * real alerts to the {@code alert_logs} table, and broadcast each saved
 * {@link com.minesafe.domain.AlertLog} via WebSocket. All heavy lifting is
 * delegated to {@link TelemetryIngester}.
 */
@Component
public class MqttIngestRoute extends RouteBuilder {

    private final String topic;
    private final TelemetryIngester telemetryIngester;

    public MqttIngestRoute(@Value("${app.mqtt.topic}") String topic,
                           TelemetryIngester telemetryIngester) {
        this.topic = topic;
        this.telemetryIngester = telemetryIngester;
    }

    @Override
    public void configure() {
        from("paho:" + topic)
            .routeId("ble-ingest")
            .convertBodyTo(String.class)
            .choice()
                .when(simple("${body} == null"))
                    .log(LoggingLevel.WARN, "Dropping empty BLE payload")
                    .stop()
                .end()
            .log(LoggingLevel.DEBUG, "BLE telemetry received: ${body}")
            .bean(telemetryIngester, "ingest");
    }
}