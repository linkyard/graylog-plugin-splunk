package com.graylog.splunk.output.senders;

import com.google.common.collect.ImmutableMap;
import com.splunk.logging.HttpEventCollectorErrorHandler;
import com.splunk.logging.HttpEventCollectorEventInfo;
import org.graylog2.plugin.Message;
import com.splunk.logging.HttpEventCollectorSender;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HECSender implements Sender {

    private final URI endpoint;
    private final String token;
    private HttpEventCollectorSender sender;

    public HECSender(String protocol, String hostname, int port, String token) throws URISyntaxException {
        this.endpoint = new URI(protocol, null, hostname, port, null, null, null);
        this.token = token;
        HttpEventCollectorErrorHandler.onError(new HttpEventCollectorErrorHandler.ErrorCallback() {
            @Override
            public void error(List<HttpEventCollectorEventInfo> list, Exception e) {
                System.out.println("ERROR: Sending to HEC-Output (" + endpoint.toString() + ") failed: " + e.toString());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void initialize() {
        Map<String, String> metadata = ImmutableMap.of();
        // TODO: Make batch sizing configurable?
        this.sender = new HttpEventCollectorSender(endpoint.toString(), token, null, "Raw", 0, 100, 10, "parallel", metadata);
    }

    @Override
    public void stop() {
        this.sender.flush(true);
        this.sender = null;
    }

    @Override
    public void send(Message message) {
        Map<String, String> properties = null;
        if (message.getFieldCount() > 0) {
            properties = new HashMap<>(message.getFieldCount());
            for (Map.Entry<String, Object> field : message.getFieldsEntries()) {
                Object value = field.getValue();
                // TODO: Determine if toString is sufficient or needs an actual serializer
                properties.put(field.getKey(), value == null ? null : value.toString());
            }
        }
        // TODO: Determine if using fields as properties are redundant
        this.sender.send("", message.getMessage(), "", "", properties, (String)null, "");
    }

    @Override
    public boolean isInitialized() {
        return this.sender != null;
    }
}
