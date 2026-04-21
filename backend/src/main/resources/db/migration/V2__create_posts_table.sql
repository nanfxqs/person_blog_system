CREATE TABLE posts (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    author_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NULL,
    content_md TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    published_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT posts_author_id_fk FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX posts_status_published_at_idx ON posts (status, published_at);
