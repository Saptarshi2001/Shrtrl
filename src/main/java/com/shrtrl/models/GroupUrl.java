package com.shrtrl.models;

import java.util.Objects;

import com.shrtrl.models.Urls;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "group_url")
public class GroupUrl {

    @EmbeddedId
    private GroupUrlId id = new GroupUrlId();

    @MapsId("groupId")
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "groupid", nullable = false)
    private Groups group;

    @MapsId("urlId")
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "urlid", nullable = false)
    private Urls url;

    protected GroupUrl() {
    }

    public GroupUrl(Groups group, Urls url) {
        this.group = Objects.requireNonNull(group, "group");
        this.url = Objects.requireNonNull(url, "url");
    }

    public GroupUrlId getId() {
        return id;
    }

    public Groups getGroup() {
        return group;
    }

    public Urls getUrl() {
        return url;
    }
}
