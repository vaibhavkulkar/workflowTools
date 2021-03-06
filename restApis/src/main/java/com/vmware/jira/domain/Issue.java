package com.vmware.jira.domain;

import com.vmware.utils.ArrayUtils;
import com.vmware.utils.StringUtils;

import com.google.gson.annotations.Expose;

public class Issue {

    public static Issue noBugNumber = Issue.noBugNumber();

    @Expose(serialize = false)
    public int id;

    @Expose(serialize = false, deserialize = false)
    public boolean isNotFound;

    @Expose(serialize = false, deserialize = false)
    public boolean hasNoBugNumber;

    public String key;
    public IssueFields fields;

    public Issue() {
    }

    public Issue(String key) {
        this.key = key;
        this.fields = new IssueFields();
    }

    public Issue(IssueTypeDefinition issueType, String project, String component, String summary,
                 String description, String acceptanceCriteria) {
        IssueFields fields = new IssueFields();
        fields.project = new Project(project);
        fields.components = new Component[] {new Component(component)};
        fields.issuetype = new IssueType(issueType);
        fields.summary = summary;
        fields.description = description;
        fields.acceptanceCriteria = acceptanceCriteria;
        this.fields = fields;
    }

    public boolean hasLabel(String label) {
        return ArrayUtils.contains(fields.labels, label);
    }

    private static Issue noBugNumber() {
        Issue response = new Issue();
        response.hasNoBugNumber = true;
        return response;
    }

    public static Issue aNotFoundIssue(String key) {
        Issue response = new Issue();
        response.isNotFound = true;
        response.key = key;
        return response;
    }

    public boolean isReal() {
        return !isNotFound && !hasNoBugNumber;
    }

    public boolean isFromJira() {
        return StringUtils.isNotBlank(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o instanceof String) {
            String key = (String) o;
            return key.equals(this.key);
        }

        if (o == null || getClass() != o.getClass()) return false;

        Issue issue = (Issue) o;

        if (key != null ? !key.equals(issue.key) : issue.key != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Issue{" +
                "key='" + key + '\'' +
                ", fields=" + fields +
                '}';
    }

}
