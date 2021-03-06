package com.vmware.action.commitInfo;

import com.vmware.action.base.AbstractReadMultiLine;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;

@ActionDescription("Sets the testing done section. Replaces existing value if there is one.")
public class SetTestingDone extends AbstractReadMultiLine {
    public SetTestingDone(WorkflowConfig config) throws NoSuchFieldException {
        super(config, "testingDone", false, config.testingDoneTemplates);
    }
}
