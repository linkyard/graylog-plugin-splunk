/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.graylog.splunk.output;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.graylog.splunk.output.senders.HECSender;
import com.graylog.splunk.output.senders.Sender;
import com.graylog.splunk.output.senders.TCPSender;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.DropdownField;
import org.graylog2.plugin.configuration.fields.NumberField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.streams.Stream;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class SplunkOutput implements MessageOutput {

    private static final String CK_SPLUNK_HOST = "splunk_host";
    private static final String CK_SPLUNK_PORT = "splunk_port";
    private static final String CK_SPLUNK_PROTOCOL = "splunk_protocol";
    private static final String CK_SPLUNK_TOKEN = "splunk_token";

    private boolean running;

    private final Sender sender;

    @Inject
    public SplunkOutput(@Assisted Configuration configuration) throws MessageOutputConfigurationException {
        // Check configuration.
        if (!checkConfiguration(configuration)) {
            throw new MessageOutputConfigurationException("Missing configuration.");
        }

        // Set up sender.
        switch (configuration.getString(CK_SPLUNK_PROTOCOL, "TCP")) {
            case "HTTPS":
                try {
                    sender = new HECSender(
                            configuration.getString(CK_SPLUNK_PROTOCOL),
                            configuration.getString(CK_SPLUNK_HOST),
                            configuration.getInt(CK_SPLUNK_PORT),
                            configuration.getString(CK_SPLUNK_TOKEN)
                    );
                } catch (URISyntaxException e) {
                    throw new MessageOutputConfigurationException("Failed to configured HEC endpoint: " + e.getMessage());
                }
                break;
            case "TCP":
                sender = new TCPSender(
                        configuration.getString(CK_SPLUNK_HOST),
                        configuration.getInt(CK_SPLUNK_PORT)
                );
                break;
            default:
                throw new MessageOutputConfigurationException("Unrecognized protocol.");
        }

        running = true;
    }

    @Override
    public void stop() {
        sender.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void write(Message message) throws Exception {
        if (message == null || message.getFields() == null || message.getFields().isEmpty()) {
            return;
        }

        if(!sender.isInitialized()) {
            sender.initialize();
        }

        sender.send(message);
    }

    @Override
    public void write(List<Message> list) throws Exception {
        if (list == null) {
            return;
        }

        for(Message m : list) {
            write(m);
        }
    }

    public boolean checkConfiguration(Configuration c) {
        return c.stringIsSet(CK_SPLUNK_HOST)
                && c.intIsSet(CK_SPLUNK_PORT)
                && c.stringIsSet(CK_SPLUNK_PROTOCOL)
                && ("UDP".equals(c.getString(CK_SPLUNK_PROTOCOL)) || "TCP".equals(c.getString(CK_SPLUNK_PROTOCOL))
                || "HTTPS".equals(c.getString(CK_SPLUNK_PROTOCOL)))
                && (!"HTTPS".equals(c.getString(CK_SPLUNK_PROTOCOL)) || c.stringIsSet(CK_SPLUNK_TOKEN));
    }

    @FactoryClass
    public interface Factory extends MessageOutput.Factory<SplunkOutput> {
        @Override
        SplunkOutput create(Stream stream, Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    @ConfigClass
    public static class Config extends MessageOutput.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();

            configurationRequest.addField(new TextField(
                            CK_SPLUNK_HOST, "Splunk Host", "",
                            "Hostname or IP address of a Splunk instance",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new NumberField(
                            CK_SPLUNK_PORT, "Splunk Port", 12999,
                            "Port of a Splunk instance",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_SPLUNK_TOKEN, "Splunk Token", "",
                            "Authentication Token for HTTPS",
                            ConfigurationField.Optional.OPTIONAL)
            );

            final Map<String, String> protocols = ImmutableMap.of("TCP", "TCP", "HTTPS", "HTTPS");
            configurationRequest.addField(new DropdownField(
                            CK_SPLUNK_PROTOCOL, "Splunk Protocol", "TCP", protocols,
                            "Protocol that should be used to send messages to Splunk",
                            ConfigurationField.Optional.OPTIONAL)
            );

            return configurationRequest;
        }
    }

    public static class Descriptor extends MessageOutput.Descriptor {
        public Descriptor() {
            super("Splunk Output", false, "", "Writes messages to your Splunk installation via UDP or TCP.");
        }
    }

}
