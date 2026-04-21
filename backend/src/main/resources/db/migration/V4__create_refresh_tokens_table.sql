CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT refresh_tokens_user_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_uk UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
