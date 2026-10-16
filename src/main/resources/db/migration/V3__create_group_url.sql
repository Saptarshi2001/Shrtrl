CREATE TABLE group_url
(
    groupid INTEGER NOT NULL,
    urlid INTEGER NOT NULL,

    CONSTRAINT pk_group_url PRIMARY KEY (groupid, urlid),
    CONSTRAINT fk_group_url_group FOREIGN KEY (groupid)
        REFERENCES groups(groupid) ON DELETE CASCADE,
    CONSTRAINT fk_group_url_url FOREIGN KEY (urlid)
        REFERENCES urls(urlid) ON DELETE CASCADE
);

CREATE INDEX idx_group_url_urlid ON group_url(urlid);
