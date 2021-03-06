package com.vmware.reviewboard.domain;

public class DiffToUpload {
    public String basedir;
    public byte[] parent_diff_path;
    public byte[] path;

    public DiffToUpload() {}

    public DiffToUpload(final byte[] path) {
        this.path = path;
    }

    public DiffToUpload(final byte[] parent_diff_path, final byte[] path) {
        this.parent_diff_path = parent_diff_path;
        this.path = path;
    }

    public boolean hasEmptyDiff() {
        return path == null || path.length == 0;
    }
}
