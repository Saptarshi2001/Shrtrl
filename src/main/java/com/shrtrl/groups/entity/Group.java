package com.shrtrl.groups.entity;

import com.shrtrl.users.entity.User;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class Group {

    @Id
    @GeneratedValue(strategy=GeneratedType.UUID)
    @Column(name="groupid", unique=true, nullable=false)
    private int groupId;

    @Basic
    @Column(name="groupname", unique=true, nullable=false)
    private String groupName;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name="userid", nullable=false)
    private User user;

    public Group() {
    }

    public Group(int groupId, String groupName, User user) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.user = user;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
