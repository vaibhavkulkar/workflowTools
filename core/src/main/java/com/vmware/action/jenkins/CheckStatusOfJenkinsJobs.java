
package com.vmware.action.jenkins;

import com.vmware.action.base.AbstractCommitWithBuildsAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;

import java.io.IOException;
import java.net.URISyntaxException;

@ActionDescription("Reads the testing done section and checks the status for all jenkins jobs found.")
public class CheckStatusOfJenkinsJobs extends AbstractCommitWithBuildsAction {

    public CheckStatusOfJenkinsJobs(WorkflowConfig config) throws IllegalAccessException, IOException, URISyntaxException {
        super(config);
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException {
        jenkins.checkStatusOfJenkinsJobs(draft);
    }
}
