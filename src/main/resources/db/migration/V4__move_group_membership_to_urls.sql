-- A URL may be ungrouped or belong to one group owned by the same user.
-- Flyway executes this migration transactionally on PostgreSQL.
LOCK TABLE groups, urls, group_url IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT urlid
        FROM group_url
        GROUP BY urlid
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot migrate: a URL belongs to multiple groups. Resolve group_url memberships first.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM group_url membership
        JOIN urls url ON url.urlid = membership.urlid
        JOIN groups grp ON grp.groupid = membership.groupid
        WHERE url.userid <> grp.userid
    ) THEN
        RAISE EXCEPTION 'Cannot migrate: a URL and its group have different owners. Resolve group_url memberships first.';
    END IF;
END;
$$;

ALTER TABLE urls ADD COLUMN groupid INTEGER;

UPDATE urls url
SET groupid = membership.groupid
FROM group_url membership
WHERE url.urlid = membership.urlid;

-- The composite foreign key also prevents cross-owner assignment.
ALTER TABLE groups
    ADD CONSTRAINT uq_groups_groupid_userid UNIQUE (groupid, userid);

ALTER TABLE urls
    ADD CONSTRAINT fk_urls_group_owner
        FOREIGN KEY (groupid, userid)
        REFERENCES groups(groupid, userid)
        ON DELETE SET NULL (groupid);

CREATE INDEX idx_urls_groupid ON urls(groupid);

DROP TABLE group_url;
