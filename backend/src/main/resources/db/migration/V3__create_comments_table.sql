CREATE TABLE comments (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT comments_post_id_fk FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT comments_user_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX comments_post_id_idx ON comments (post_id);
