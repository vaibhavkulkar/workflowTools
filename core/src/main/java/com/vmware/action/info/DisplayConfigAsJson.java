package com.vmware.action.info;

import com.google.gson.Gson;
import com.vmware.action.AbstractAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.rest.json.ConfiguredGsonBuilder;
import com.vmware.utils.Padder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

@ActionDescription("Displays the current workflow configuration as json")
public class DisplayConfigAsJson extends AbstractAction {

    private final Gson gson;

    public DisplayConfigAsJson(WorkflowConfig config) {
        super(config);
        gson = new ConfiguredGsonBuilder().setPrettyPrinting().build();
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException, ParseException {
        Padder titlePadder = new Padder("Workflow Configuration Json");
        titlePadder.infoTitle();
        log.info(gson.toJson(config));
        titlePadder.infoTitle();
    }
}
