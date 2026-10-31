package com.fixlog.presentation.dto.request;

public record FolderSettingsRequest(Boolean inheritFromParent) {
    public boolean resolvedInheritFromParent() {
        return inheritFromParent == null || inheritFromParent;
    }
}
