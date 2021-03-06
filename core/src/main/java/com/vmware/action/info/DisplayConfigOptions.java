package com.vmware.action.info;

import com.google.gson.Gson;
import com.vmware.action.AbstractAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.ConfigurableProperty;
import com.vmware.config.WorkflowConfig;
import com.vmware.rest.json.ConfiguredGsonBuilder;
import com.vmware.utils.ClasspathResource;
import com.vmware.utils.Padder;
import com.vmware.utils.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

@ActionDescription("Displays a list of configuration options that can be set.")
public class DisplayConfigOptions extends AbstractAction {

    private final Gson gson;

    public DisplayConfigOptions(WorkflowConfig config) {
        super(config);
        gson = new ConfiguredGsonBuilder().build();
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException {
        Reader reader = new ClasspathResource("/internalConfig.json").getReader();
        WorkflowConfig defaultConfig = gson.fromJson(reader, WorkflowConfig.class);
        log.info("");
        log.info("Printing configuration options");
        log.info("Each option is displayed in the following format");
        log.info("name, command line overrides if any, description, default value if any");
        log.info("");
        log.info("Each option can also be specified as a git config value, prepend workflow. to the name");
        log.info("E.g. workflow.configFile would be the git config value for configuring the configuration file");
        log.info("");
        log.info("Overriding config option priority from lowest to highest: \n{}\n{}\n{}\n{}\n{}\n{}",
                "Internal Config File", "Project Config file", "User Config file", "Git Config Value", "Specified Config Files", "Command Line Arguments");
        Padder titlePadder = new Padder("Configuration Options");
        titlePadder.infoTitle();
        log.info("configFile, [-c,--config] Optional configuration file to use, file is in json format, Defaults to config file in jar");
        for (Field field : config.configurableFields) {
            ConfigurableProperty configProperty = field.getAnnotation(ConfigurableProperty.class);
            Object defaultValue = field.get(defaultConfig);

            String defaultDisplayValue;
            if (defaultValue == null) {
              defaultDisplayValue = null;
            } else if (Map.class.isAssignableFrom(field.getType())) {
                defaultDisplayValue = ((Map) defaultValue).keySet().toString();
            } else if (field.getType() == int[].class) {
                defaultDisplayValue = Arrays.toString((int[]) defaultValue);
            } else if (field.getType().isArray()) {
                defaultDisplayValue = Arrays.toString((Object[]) defaultValue);
            } else {
                defaultDisplayValue = String.valueOf(defaultValue);
            }

            String defaultDisplayText = defaultDisplayValue != null ? "Default: " + defaultDisplayValue: "No Default";
            log.info("{},[{}], {}, {}", field.getName(), configProperty.commandLine(), configProperty.help(), defaultDisplayText);
            if (StringUtils.isNotBlank(configProperty.gitConfigProperty())) {
                log.info("{} can also be set from git config value {}", field.getName(), configProperty.gitConfigProperty());
            }
        }
        titlePadder.infoTitle();
    }
}
