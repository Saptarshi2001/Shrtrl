package com.shrtrl.models;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.validator.constraints.Length;

import com.shrtrl.models.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "groups",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_groups_user_groupname",
                columnNames = {"userid", "groupname"}
        ),
        indexes = {
                @Index(name = "idx_groups_groupid", columnList = "groupid"),
        }
)
public class Groups {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "groups_groupid_seq"
    )
    @SequenceGenerator(
            name = "groups_groupid_seq",
            sequenceName = "groups_groupid_seq",
            allocationSize = 1
    )
    @Column(name = "groupid", nullable = false)
    @Length(max=6)
    private int groupId;

    @Column(name = "groupname", nullable = false, length = 20)
    @Length(max=15)
    @Length(min=5)
    private String groupName;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "userid", nullable = false)
    private Users user;

    protected Groups() {
    }

    public Groups(String groupName, Users user) {
        this.groupName = groupName;
        this.user = user;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Users getUser() {
        return user;
    }
}
