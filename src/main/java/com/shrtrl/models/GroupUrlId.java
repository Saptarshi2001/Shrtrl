package com.shrtrl.models;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GroupUrlId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "groupid", nullable = false)
    private Integer groupId;

    @Column(name = "urlid", nullable = false)
    private Integer urlId;

    protected GroupUrlId() {
    }

    public GroupUrlId(Integer groupId, Integer urlId) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.urlId = Objects.requireNonNull(urlId, "urlId");
    }

    public Integer getGroupId() {
        return groupId;
    }

    public Integer getUrlId() {
        return urlId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroupUrlId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(urlId, that.urlId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, urlId);
    }
}
